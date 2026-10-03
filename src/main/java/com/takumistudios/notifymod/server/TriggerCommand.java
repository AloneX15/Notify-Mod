package com.takumistudios.notifymod.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.core.Trigger;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Util;

/** Commands and admin actions share the same permission checks and validated edit path. */
public final class TriggerCommand {
    private TriggerCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("trigger");
        root.then(Commands.literal("list").requires(s -> permission(s, "list", false)).executes(c -> run(c.getSource(), () -> {
            var manager = NotifyServer.get().triggers();
            manager.all().values().forEach(t -> message(c.getSource(), t.id() + " [" + t.type() + "] " + t.enabled()));
            manager.errors().forEach(e -> message(c.getSource(), e));
            return 1;
        })));
        for (String action : List.of("info", "enable", "disable", "fire", "test", "audience")) {
            boolean admin = !Set.of("info", "audience").contains(action);
            String node = Set.of("enable", "disable").contains(action) ? "manage" : action.equals("audience") ? "info" : action;
            root.then(Commands.literal(action).requires(s -> permission(s, node, admin))
                    .then(Commands.argument("id", new TriggerIdArgument())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(NotifyServer.get().triggers().all().keySet(), b))
                            .executes(c -> run(c.getSource(), () -> execute(c.getSource(), action, StringArgumentType.getString(c, "id"))))));
        }
        root.then(Commands.literal("set").requires(s -> permission(s, "manage", true))
                .then(Commands.argument("id", new TriggerIdArgument())
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("cooldown", "per_player_cooldown", "max_per_minute", "placement", "priority"), b))
                                .then(Commands.argument("value", StringArgumentType.word()).executes(c -> run(c.getSource(), () -> {
                                    NotifyServer.get().triggers().edit(NotifyCommand.normalizeId(StringArgumentType.getString(c, "id")),
                                            StringArgumentType.getString(c, "field"), StringArgumentType.getString(c, "value"), c.getSource().getTextName());
                                    message(c.getSource(), "notifymod.trigger.saved", StringArgumentType.getString(c, "id")); return 1;
                                }))))));
        dispatcher.register(Commands.literal("notify")
                .requires(s -> NotifyPermissions.check(s, NotifyPermissions.COMMAND, PermissionLevel.GAMEMASTERS)).then(root));
    }

    private static int execute(CommandSourceStack source, String action, String rawId) {
        String id = NotifyCommand.normalizeId(rawId);
        TriggerManager manager = NotifyServer.get().triggers(); Trigger trigger = manager.all().get(id);
        if (trigger == null) throw new IllegalArgumentException("Unknown trigger: " + id);
        switch (action) {
            case "enable", "disable" -> {
                manager.edit(id, "enabled", action.equals("enable") ? "true" : "false", source.getTextName());
                message(source, "notifymod.trigger.saved", id);
            }
            case "info" -> message(source, trigger.toString());
            case "audience" -> message(source, String.join(", ", manager.audience(source.getServer(), trigger, source.getPlayer()).stream().map(p -> p.getScoreboardName()).toList()));
            case "test" -> {
                if (source.getPlayer() == null) throw new IllegalArgumentException("Test requires a player");
                var template = NotifyServer.get().templates().get(trigger.template());
                if (template == null) throw new IllegalArgumentException("Unknown template");
                var data = TriggerManager.event(source.getPlayer()); data.put("death_message", "Test"); data.put("killer", "Test");
                NotificationDispatcher.send(List.of(source.getPlayer()), template.toNotification(trigger.resolveArgs(data), trigger.placement(), trigger.priority(), null));
                manager.audit(source.getTextName(), "test", id);
            }
            case "fire" -> {
                int count = manager.fire(source.getServer(), trigger, source.getPlayer(), source.getPlayer() == null ? Map.of() : TriggerManager.event(source.getPlayer()));
                manager.audit(source.getTextName(), "fire", id); message(source, "notifymod.trigger.delivered", count);
            }
            default -> throw new IllegalArgumentException("Unknown action");
        }
        return 1;
    }

    private static boolean permission(CommandSourceStack source, String action, boolean admin) {
        return NotifyPermissions.check(source, "trigger." + action, admin ? PermissionLevel.ADMINS : PermissionLevel.GAMEMASTERS);
    }
    private static int run(CommandSourceStack source, java.util.function.IntSupplier action) {
        try {
            if (source.getPlayer() != null && !NotifyServer.get().limiter().tryAcquire(source.getPlayer().getStringUUID(),
                    NotifyServer.get().config().maxCommandsPerMinute(), Util.getMillis())) {
                source.sendFailure(Component.translatable("notifymod.trigger.rate_limit")); return 0;
            }
            return action.getAsInt();
        } catch (RuntimeException ex) {
            NotifyMod.LOGGER.debug("Rejected trigger command", ex);
            source.sendFailure(Component.translatable("notifymod.trigger.error", ex.getMessage())); return 0;
        }
    }
    private static void message(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> args.length == 0 ? Component.literal(key) : Component.translatable(key, args), false);
    }
}
