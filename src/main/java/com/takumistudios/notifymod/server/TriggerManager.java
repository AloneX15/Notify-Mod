package com.takumistudios.notifymod.server;

import com.google.gson.*;
import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.config.JsonConfigFile;
import com.takumistudios.notifymod.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

/** Server-owned trigger registry, active index, bounded deferred delivery and serialized atomic persistence. */
public final class TriggerManager {
    private final Path directory;
    private final Map<String, JsonObject> definitions = new TreeMap<>();
    private final Map<String, Trigger> triggers = new TreeMap<>();
    private final Map<String, List<Trigger>> active = new HashMap<>();
    private final Set<String> installed = new HashSet<>();
    private final Set<String> failed = new HashSet<>();
    private final List<String> errors = new ArrayList<>();
    private final Map<UUID, ArrayDeque<Deferred>> deferred = new HashMap<>();
    private final TriggerThrottle throttle = new TriggerThrottle();
    private JsonObject overrides = new JsonObject();
    private ThreadPoolExecutor io;
    private final java.util.concurrent.atomic.AtomicInteger pendingWrites = new java.util.concurrent.atomic.AtomicInteger();
    private record Deferred(String trigger, String template, Notification notification, long expires) {}

    public TriggerManager(Path directory) { this.directory = directory; }
    public Map<String, Trigger> all() { return Collections.unmodifiableMap(triggers); }
    public List<String> errors() { return List.copyOf(errors); }
    public boolean enabled(String id) {
        Trigger trigger = triggers.get(id);
        return trigger != null && trigger.enabled() && !failed.contains(id);
    }

    public void reload(MinecraftServer server) {
        if (io == null) io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256), r -> {
            Thread t = new Thread(r, "Notify-Triggers-IO"); t.setDaemon(true); return t;
        });
        definitions.clear(); triggers.clear(); active.clear(); errors.clear(); failed.clear(); throttle.clear(); deferred.clear();
        JsonObject state = pendingWrites.get() > 0 ? stateSnapshot() : JsonConfigFile.load(directory.resolve("triggers_state.json"), emptyState(), value -> {
            if (value.get("format_version").getAsInt() != 1) throw new IllegalArgumentException("Invalid state version");
            JsonObject changes = value.getAsJsonObject("overrides");
            if (changes.size() > 1024) throw new IllegalArgumentException("Too many overrides");
            for (var e : changes.entrySet()) {
                if (!Notification.isValidId(e.getKey())) throw new IllegalArgumentException("Invalid override id");
                for (String k : e.getValue().getAsJsonObject().keySet()) {
                    if (!Set.of("enabled", "cooldown", "per_player_cooldown", "max_per_minute", "placement", "priority").contains(k)) {
                        throw new IllegalArgumentException("Invalid override field");
                    }
                }
            }
            return value;
        }, value -> value);
        overrides = state.getAsJsonObject("overrides").deepCopy();
        for (var e : server.getResourceManager().listResources("notify/triggers", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String id = file.getNamespace() + ":" + file.getPath().substring("notify/triggers/".length(), file.getPath().length() - 5);
            try (Reader reader = e.getValue().openAsReader()) {
                load(id, read(reader));
            } catch (IOException | RuntimeException ex) { error(id, ex); }
        }
        Path managed = directory.resolve("managed/triggers");
        if (Files.isDirectory(managed)) try (var files = Files.walk(managed, 8)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).toList()) {
                Path relative = managed.relativize(file);
                if (relative.getNameCount() < 2) { errors.add("managed/" + relative + ": namespace required"); continue; }
                String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                String id = relative.getName(0) + ":" + path.substring(0, path.length() - 5);
                try {
                    if (Files.size(file) > Template.MAX_JSON_CHARS * 4L) throw new IllegalArgumentException("Oversized file");
                    load(id, Files.readString(file, StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException ex) { definitions.remove(id); error(id, ex); }
            }
        } catch (IOException ex) { error("managed", ex); }
        for (var entry : definitions.entrySet()) try {
            JsonObject merged = entry.getValue().deepCopy();
            if (overrides.has(entry.getKey())) overrides.getAsJsonObject(entry.getKey()).entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
            Trigger trigger = Trigger.parse(entry.getKey(), merged.toString());
            if (NotifyServer.get().templates().get(trigger.template()) == null) throw new IllegalArgumentException("Unknown template: " + trigger.template());
            triggers.put(trigger.id(), trigger);
            if (!FabricLoader.getInstance().isModLoaded("luckperms") && (trigger.include().stream().anyMatch(r -> r.type().equals("group"))
                    || trigger.exclude().stream().anyMatch(r -> r.type().equals("group")) || trigger.conditions().containsKey("cause_group"))) {
                NotifyMod.LOGGER.warn("Trigger {} uses groups but LuckPerms is absent; group rules select nobody", trigger.id());
            }
        } catch (RuntimeException ex) { error(entry.getKey(), ex); }
        reindex();
    }

    private void load(String id, String text) {
        if (definitions.size() >= 1024 && !definitions.containsKey(id)) throw new IllegalArgumentException("Too many triggers");
        Trigger.parse(id, text);
        definitions.put(id, JsonParser.parseString(text).getAsJsonObject());
    }

    private static String read(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder(); char[] buffer = new char[4096]; int count;
        while ((count = reader.read(buffer)) != -1) {
            out.append(buffer, 0, count);
            if (out.length() > Template.MAX_JSON_CHARS) throw new IllegalArgumentException("Oversized trigger");
        }
        return out.toString();
    }

    private void error(String id, Exception ex) {
        errors.add(id + ": " + ex.getMessage()); NotifyMod.LOGGER.warn("Invalid trigger {}: {}", id, ex.toString());
    }

    public void edit(String id, String field, String value, String actor) {
        if (io == null || io.isShutdown() || io.getQueue().remainingCapacity() < 2) {
            throw new IllegalArgumentException("Trigger persistence is busy; retry later");
        }
        if (!triggers.containsKey(id)) throw new IllegalArgumentException("Unknown trigger: " + id);
        JsonObject change = overrides.has(id) ? overrides.getAsJsonObject(id).deepCopy() : new JsonObject();
        switch (field) {
            case "enabled" -> {
                if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Expected true or false");
                change.addProperty(field, Boolean.parseBoolean(value));
            }
            case "max_per_minute" -> change.addProperty(field, Integer.parseInt(value));
            case "cooldown", "per_player_cooldown", "placement", "priority" -> change.addProperty(field, value);
            default -> throw new IllegalArgumentException("Unsupported edit field: " + field);
        }
        JsonObject merged = definitions.get(id).deepCopy();
        change.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
        Trigger trigger = Trigger.parse(id, merged.toString());
        overrides.add(id, change); triggers.put(id, trigger); failed.remove(id); reindex();
        JsonObject state = stateSnapshot();
        JsonConfigFile.saveAsync(directory.resolve("triggers_state.json"), state, task -> {
            pendingWrites.incrementAndGet();
            try { io.execute(() -> { try { task.run(); } finally { pendingWrites.decrementAndGet(); } }); }
            catch (RuntimeException ex) { pendingWrites.decrementAndGet(); throw ex; }
        });
        audit(actor, "edit", id + " " + field + "=" + value);
    }

    private void reindex() {
        active.clear();
        for (Trigger trigger : triggers.values()) if (enabled(trigger.id())) {
            active.computeIfAbsent(trigger.type(), k -> new ArrayList<>()).add(trigger);
        }
        for (String type : active.keySet()) install(type);
        deferred.values().forEach(queue -> queue.removeIf(item -> !enabled(item.trigger())));
        deferred.values().removeIf(ArrayDeque::isEmpty);
    }

    // Fabric has no unregister API. Hooks are installed lazily once and become a constant-time no-op when disabled.
    private void install(String type) {
        if (!installed.add(type)) return;
        if (type.equals("player_death")) {
            ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
                if (!deferred.isEmpty()) safe(() -> deliverDeferred(player));
            });
        }
        switch (type) {
            case "player_death" -> ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
                if (active.containsKey(type) && entity instanceof ServerPlayer player) safe(() -> {
                    Map<String, String> data = event(player);
                    data.put("death_message", damage.getLocalizedDeathMessage(player).getString());
                    data.put("damage_type", damage.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse(""));
                    if (damage.getEntity() != null) {
                        data.put("killer", damage.getEntity().getName().getString());
                        data.put("killer_type", BuiltInRegistries.ENTITY_TYPE.getKey(damage.getEntity().getType()).toString());
                    }
                    emit(player.level().getServer(), type, player, data);
                });
            });
            case "player_respawn" -> ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
                if (active.containsKey(type)) safe(() -> emit(newPlayer.level().getServer(), type, newPlayer, event(newPlayer)));
            });
            case "player_join" -> ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
                if (active.containsKey(type)) safe(() -> emit(server, type, handler.player, event(handler.player)));
            });
            case "player_leave" -> ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
                if (active.containsKey(type)) safe(() -> emit(server, type, handler.player, event(handler.player)));
            });
            case "chat_keyword" -> ServerMessageEvents.CHAT_MESSAGE.register((message, player, bound) -> {
                if (active.containsKey(type)) safe(() -> {
                    Map<String, String> data = event(player); data.put("keyword", message.signedContent());
                    emit(player.level().getServer(), type, player, data);
                });
            });
            case "server_stopping" -> ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                if (active.containsKey(type)) safe(() -> emit(server, type, null, Map.of()));
            });
            default -> { /* server_ready is emitted after reload; manual uses the command/API path. */ }
        }
    }

    public void initLifecycle() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            deferred.remove(handler.player.getUUID()); throttle.forget(handler.player.getStringUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> close());
    }

    private void deliverDeferred(ServerPlayer player) {
        ArrayDeque<Deferred> pending = deferred.remove(player.getUUID());
        if (pending == null) return;
        for (Deferred item : pending) {
            Trigger trigger = triggers.get(item.trigger());
            if (item.expires() > Util.getMillis() && enabled(item.trigger())
                    && audience(player.level().getServer(), trigger, player).contains(player)) {
                NotificationDispatcher.send(List.of(player), item.notification());
            }
        }
    }

    public void emit(MinecraftServer server, String type, ServerPlayer cause, Map<String, String> data) {
        for (Trigger trigger : List.copyOf(active.getOrDefault(type, List.of()))) {
            try { fire(server, trigger, cause, data); }
            catch (RuntimeException ex) {
                active.get(type).remove(trigger);
                failed.add(trigger.id());
                NotifyMod.LOGGER.error("Trigger {} disabled for this session after callback failure", trigger.id(), ex);
            }
        }
    }

    public int fire(MinecraftServer server, Trigger trigger, ServerPlayer cause, Map<String, String> data) {
        if (!enabled(trigger.id()) || !matches(trigger, cause, data)) return 0;
        Template template = NotifyServer.get().templates().get(trigger.template());
        if (template == null || !throttle.acquire(trigger, cause == null ? "server" : cause.getStringUUID(), Util.getMillis())) return 0;
        Notification notification = template.toNotification(trigger.resolveArgs(data), trigger.placement(), trigger.priority(), null);
        List<ServerPlayer> recipients = audience(server, trigger, cause);
        if (cause != null && cause.isDeadOrDying() && trigger.type().equals("player_death") && recipients.remove(cause)) {
            if (trigger.causeDelivery().equals("after_respawn")) {
                ArrayDeque<Deferred> queue = deferred.computeIfAbsent(cause.getUUID(), k -> new ArrayDeque<>());
                if (queue.size() >= 16) queue.removeFirst();
                queue.addLast(new Deferred(trigger.id(), template.id(), notification, Util.getMillis() + 60_000));
            }
        } else if (cause != null && trigger.causeDelivery().equals("skip")) recipients.remove(cause);
        return NotificationDispatcher.send(recipients, notification);
    }

    public List<ServerPlayer> audience(MinecraftServer server, Trigger trigger, ServerPlayer cause) {
        List<ServerPlayer> recipients = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (trigger.include().stream().anyMatch(r -> rule(r, player, cause))
                    && trigger.exclude().stream().noneMatch(r -> rule(r, player, cause))
                    && !permission(player, "notifymod:bypass.trigger." + trigger.id().replace(':', '.'), false)
                    && NotifyPermissions.canReceive(player, "receive." + trigger.template().replace(':', '.'))) recipients.add(player);
        }
        return recipients;
    }

    private boolean rule(Trigger.AudienceRule rule, ServerPlayer player, ServerPlayer cause) {
        return switch (rule.type()) {
            case "all" -> true;
            case "involved" -> cause != null && player.getUUID().equals(cause.getUUID());
            case "dimension" -> player.level().dimension().identifier().toString().equals(rule.value());
            case "team" -> player.getTeam() != null && player.getTeam().getName().equals(rule.value());
            case "permission" -> permission(player, rule.value(), false);
            case "group" -> FabricLoader.getInstance().isModLoaded("luckperms") && permission(player, "group:" + rule.value(), false);
            default -> false;
        };
    }

    private boolean matches(Trigger trigger, ServerPlayer cause, Map<String, String> data) {
        for (var entry : trigger.conditions().entrySet()) {
            boolean match = switch (entry.getKey()) {
                case "cause_permission" -> cause != null && entry.getValue().stream().anyMatch(v -> permission(cause, v, false));
                case "cause_group" -> cause != null && FabricLoader.getInstance().isModLoaded("luckperms")
                        && entry.getValue().stream().anyMatch(v -> permission(cause, "group:" + v, false));
                case "keyword" -> entry.getValue().stream().anyMatch(v -> data.getOrDefault("keyword", "").equalsIgnoreCase(v));
                default -> entry.getValue().contains(data.getOrDefault(entry.getKey(), ""));
            };
            if (!match) return false;
        }
        return true;
    }

    private static boolean permission(ServerPlayer player, String node, boolean fallback) {
        // LuckPerms maps Fabric's namespace:path to namespace.path, including inherited group membership.
        if (!node.contains(":")) {
            int dot = node.indexOf('.');
            if (dot < 1) return false;
            node = node.substring(0, dot) + ":" + node.substring(dot + 1);
        }
        Identifier id = Identifier.tryParse(node);
        return id != null && player.checkPermission(id, fallback);
    }

    public static Map<String, String> event(ServerPlayer player) {
        Map<String, String> data = new HashMap<>();
        data.put("player", player.getScoreboardName());
        data.put("dimension", player.level().dimension().identifier().toString());
        data.put("time", Long.toString(player.level().getGameTime()));
        data.put("x", Integer.toString(player.getBlockX())); data.put("y", Integer.toString(player.getBlockY())); data.put("z", Integer.toString(player.getBlockZ()));
        return data;
    }

    public void audit(String actor, String action, String detail) {
        JsonObject row = new JsonObject(); row.addProperty("time", java.time.Instant.now().toString());
        row.addProperty("actor", actor); row.addProperty("action", action); row.addProperty("detail", detail);
        Runnable write = () -> {
            try {
                Path log = directory.resolve("audit.jsonl");
                Files.createDirectories(directory);
                if (Files.exists(log) && Files.size(log) > 4 * 1024 * 1024) {
                    Files.move(log, directory.resolve("audit.previous.jsonl"), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.writeString(log, row + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) { NotifyMod.LOGGER.warn("Could not write trigger audit", ex); }
        };
        try { io.execute(write); }
        catch (RejectedExecutionException ex) { NotifyMod.LOGGER.warn("Trigger audit queue full; entry rejected: {} {}", action, detail); }
    }

    private static JsonObject emptyState() {
        JsonObject state = new JsonObject(); state.addProperty("format_version", 1); state.add("overrides", new JsonObject()); return state;
    }

    private JsonObject stateSnapshot() {
        JsonObject state = emptyState(); state.add("overrides", overrides.deepCopy()); return state;
    }

    private static void safe(Runnable action) {
        try { action.run(); } catch (RuntimeException ex) { NotifyMod.LOGGER.error("Trigger callback failed", ex); }
    }

    private void close() {
        active.clear(); triggers.clear(); definitions.clear(); deferred.clear(); throttle.clear();
        if (io != null) {
            io.shutdown();
            try { if (!io.awaitTermination(10, TimeUnit.SECONDS)) NotifyMod.LOGGER.warn("Trigger writes are still pending"); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); NotifyMod.LOGGER.warn("Interrupted waiting for trigger writes", ex); }
            io = null;
        }
    }
}
