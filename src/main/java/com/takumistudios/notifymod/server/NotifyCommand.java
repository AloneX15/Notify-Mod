package com.takumistudios.notifymod.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.takumistudios.notifymod.core.Channel;
import com.takumistudios.notifymod.core.CommandOptions;
import com.takumistudios.notifymod.core.Durations;
import com.takumistudios.notifymod.core.Message;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.NotificationArgs;
import com.takumistudios.notifymod.core.Placement;
import com.takumistudios.notifymod.core.Priority;
import com.takumistudios.notifymod.core.Template;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Util;

/**
 * {@code /notify} (§18). Todas las órdenes funcionan desde consola, bloques de comandos y funciones. Opciones con la
 * sintaxis {@code clave=valor}: {@code placement=}, {@code priority=}, {@code key=}, {@code duration=},
 * {@code chat=true} y {@code message="..."}.
 */
public final class NotifyCommand {
    static final Set<String> OPTIONS = Set.of("placement", "priority", "key", "duration", "chat", "message");

    private NotifyCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("notify")
                .requires(s -> NotifyPermissions.check(s, NotifyPermissions.COMMAND, PermissionLevel.GAMEMASTERS))
                .then(Commands.literal("send")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.SEND, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(c -> sendChat(c.getSource(), StringArgumentType.getString(c, "message")))))
                .then(Commands.literal("hud")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.HUD, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> sendHud(c.getSource(), StringArgumentType.getString(c, "text")))))
                .then(Commands.literal("showcase")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.SHOWCASE, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("input", StringArgumentType.greedyString())
                                .suggests(NotifyCommand::suggestShowcase)
                                .executes(c -> sendShowcase(c.getSource(), StringArgumentType.getString(c, "input")))))
                .then(Commands.literal("cancel")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.CANCEL, PermissionLevel.GAMEMASTERS))
                        .then(Commands.argument("key", StringArgumentType.greedyString())
                                .executes(c -> cancel(c.getSource(), StringArgumentType.getString(c, "key")))))
                .then(Commands.literal("clear")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.CLEAR, PermissionLevel.GAMEMASTERS))
                        .executes(c -> cancel(c.getSource(), "all")))
                .then(Commands.literal("reload")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.RELOAD, PermissionLevel.ADMINS))
                        .executes(c -> reload(c.getSource())))
                .then(Commands.literal("validate")
                        .requires(s -> NotifyPermissions.check(s, NotifyPermissions.VALIDATE, PermissionLevel.GAMEMASTERS))
                        .executes(c -> validate(c.getSource())))
                .then(Commands.literal("help").executes(c -> help(c.getSource()))));
    }

    // ------------------------------------------------------------ envío

    private static int sendChat(CommandSourceStack source, String text) {
        return run(source, () -> new Notification(EnumSet.of(Channel.CHAT), null, Message.literal(text.strip()), null,
                Priority.NORMAL, null, null, Notification.DEFAULT_HUD_MS));
    }

    private static int sendHud(CommandSourceStack source, String input) {
        return run(source, () -> build(source, EnumSet.of(Channel.HUD), Notification.MOTD,
                CommandOptions.parse(input, OPTIONS, false), null));
    }

    private static int sendShowcase(CommandSourceStack source, String input) {
        return run(source, () -> {
            String trimmed = input.strip();
            int space = trimmed.indexOf(' ');
            String id = normalizeId(space < 0 ? trimmed : trimmed.substring(0, space));
            String rest = space < 0 ? "" : trimmed.substring(space + 1);
            NotifyServer server = NotifyServer.get();
            Template template = server.templates().get(id);
            if (template != null) {
                long wait = server.templates().cooldownRemaining(template, Util.getMillis());
                if (wait > 0) {
                    throw new IllegalArgumentException("La plantilla " + id + " está en espera (" + Durations.format(
                            (wait + 999) / 1000 * 1000) + ")");
                }
            }
            return build(source, EnumSet.of(Channel.SHOWCASE), id, CommandOptions.parse(rest, OPTIONS, true), template);
        });
    }

    /** Construye y valida la notificación, comprobando los permisos de prioridad y colocación. */
    static Notification build(CommandSourceStack source, EnumSet<Channel> channels, String presentation,
            CommandOptions.Parsed parsed, Template template) {
        Priority priority = parsed.option("priority") == null ? null : Priority.parse(parsed.option("priority"));
        Placement placement = parsed.option("placement") == null ? null : Placement.parse(parsed.option("placement"));
        Boolean chat = parsed.option("chat") == null ? null : CommandOptions.parseBoolean(parsed.option("chat"));
        if (placement != null && channels.contains(Channel.SHOWCASE)
                && !NotifyPermissions.check(source, NotifyPermissions.PLACEMENT, PermissionLevel.GAMEMASTERS)) {
            throw new IllegalArgumentException("No tienes permiso para elegir la colocación (notifymod.showcase.placement)");
        }
        NotificationArgs args = NotificationArgs.of(parsed.args());
        Notification n;
        if (template != null) {
            n = template.toNotification(args, placement, priority, chat);
            String message = parsed.option("message");
            if (message != null || parsed.option("key") != null || parsed.option("duration") != null) {
                n = new Notification(n.channels(), n.presentation(), message != null ? Message.literal(message) : n.message(),
                        n.args(), n.priority(), n.placement(), parsed.option("key") != null ? parsed.option("key") : n.key(),
                        duration(parsed, n.durationMs()));
            }
        } else {
            String message = parsed.message().strip();
            if (message.isEmpty()) {
                throw new IllegalArgumentException("Falta el mensaje. Ejemplo: message=\"Reinicio en 5 minutos\"");
            }
            if (chat == null ? NotifyServer.get().config().defaultMirrorToChat() : chat) {
                channels.add(Channel.CHAT);
            }
            n = new Notification(channels, presentation, Message.literal(message), args, priority, placement,
                    parsed.option("key"), duration(parsed, Notification.defaultDuration(channels)));
        }
        checkPriority(source, n.priority());
        return n;
    }

    private static int duration(CommandOptions.Parsed parsed, int fallback) {
        String d = parsed.option("duration");
        if (d == null) {
            return fallback;
        }
        long ms = Durations.parseMillis(d);
        return (int) Math.min(Integer.MAX_VALUE, ms);
    }

    private static void checkPriority(CommandSourceStack source, Priority priority) {
        if (priority == Priority.CRITICAL
                && !NotifyPermissions.check(source, NotifyPermissions.PRIORITY_CRITICAL, PermissionLevel.ADMINS)) {
            throw new IllegalArgumentException("No tienes permiso para la prioridad CRITICAL (notifymod.priority.critical)");
        }
        if (priority == Priority.HIGH
                && !NotifyPermissions.check(source, NotifyPermissions.PRIORITY_HIGH, PermissionLevel.GAMEMASTERS)) {
            throw new IllegalArgumentException("No tienes permiso para la prioridad HIGH (notifymod.priority.high)");
        }
    }

    private interface Builder {
        Notification build();
    }

    private static int run(CommandSourceStack source, Builder builder) {
        NotifyServer server = NotifyServer.get();
        ServerPlayer player = source.getPlayer();
        if (player != null && !server.limiter().tryAcquire(player.getStringUUID(),
                server.config().maxCommandsPerMinute(), Util.getMillis())) {
            source.sendFailure(Component.literal("Demasiados avisos seguidos; espera un poco."));
            return 0;
        }
        Notification notification;
        try {
            notification = builder.build();
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        List<ServerPlayer> targets = source.getServer().getPlayerList().getPlayers();
        int delivered = NotificationDispatcher.send(targets, notification);
        source.sendSuccess(() -> Component.literal("Notify: enviado a " + delivered + " jugador(es) ["
                + String.join(", ", notification.channels().stream().map(Channel::id).sorted().toList()) + "]"), true);
        return Math.max(1, delivered);
    }

    // ------------------------------------------------------------ mantenimiento

    private static int cancel(CommandSourceStack source, String key) {
        String k = key.strip();
        String wire = k.equalsIgnoreCase("all") ? com.takumistudios.notifymod.network.NotifyNetwork.CancelPayload.ALL : k;
        if (wire.length() > Notification.MAX_KEY) {
            source.sendFailure(Component.literal("Clave demasiado larga"));
            return 0;
        }
        NotificationDispatcher.cancel(source.getServer().getPlayerList().getPlayers(), wire);
        source.sendSuccess(() -> Component.literal(wire.equals("*") ? "Notify: cola vaciada" : "Notify: cancelado " + wire),
                true);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        NotifyServer.get().reload(source.getServer());
        return validate(source);
    }

    private static int validate(CommandSourceStack source) {
        TemplateRegistry templates = NotifyServer.get().templates();
        source.sendSuccess(() -> Component.literal("Notify: " + templates.all().size() + " plantillas, "
                + templates.errors().size() + " con errores"), false);
        for (String error : templates.errors()) {
            source.sendSuccess(() -> Component.literal(" - " + error).withStyle(ChatFormatting.RED), false);
        }
        return templates.errors().isEmpty() ? 1 : 0;
    }

    private static int help(CommandSourceStack source) {
        for (String line : List.of(
                "/notify send <mensaje>  - chat",
                "/notify hud <mensaje> [placement=top_right] [priority=high] [key=x] [duration=10s] [chat=true]",
                "/notify showcase <plantilla|presentación> [message=\"...\"] [placement=...] [arg=valor...]",
                "/notify cancel <clave|all>  ·  /notify clear",
                "/notify reload  ·  /notify validate")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    // ------------------------------------------------------------ utilidades

    static String normalizeId(String id) {
        return id.indexOf(':') < 0 ? "notifymod:" + id : id;
    }

    private static CompletableFuture<Suggestions> suggestShowcase(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        if (remaining.contains(" ")) {
            return builder.buildFuture();
        }
        List<String> ids = new ArrayList<>(NotifyServer.get().templates().all().keySet());
        ids.add(Notification.MOTD);
        return SharedSuggestionProvider.suggest(ids, builder);
    }
}

