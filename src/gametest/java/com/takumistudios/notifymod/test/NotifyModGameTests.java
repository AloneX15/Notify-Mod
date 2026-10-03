package com.takumistudios.notifymod.test;

import com.takumistudios.notifymod.NotifyMod;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/** Gametests de servidor: ./gradlew :26.3:runGameTest (y con -PcompatPack en la CI). */
public class NotifyModGameTests {
    @GameTest
    public void styledNotificationRoundTripsAndFormatsChat(GameTestHelper helper) {
        var template = com.takumistudios.notifymod.server.NotifyServer.get().templates().get("notifymod:styled_corner");
        var notification = template.toNotification(com.takumistudios.notifymod.core.NotificationArgs.EMPTY, null, null, true);
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var codec = com.takumistudios.notifymod.network.NotifyNetwork.ShowPayload.CODEC;
            codec.encode(buffer, new com.takumistudios.notifymod.network.NotifyNetwork.ShowPayload(notification));
            var decoded = codec.decode(buffer).notification();
            if (!notification.equals(decoded)) throw helper.assertionException("Style lost during network round trip");
            var chat = com.takumistudios.notifymod.NotificationText.chat(decoded);
            if (!chat.getString().contains("MOMENTO REVILL\n\nDennis"))
                throw helper.assertionException("Missing heading/spacing in chat");
            var heading = chat.getSiblings().getFirst();
            if (!heading.getStyle().isBold() || heading.getStyle().getColor().getValue() != 0x00CC33)
                throw helper.assertionException("Missing chat color/bold title");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest
    public void triggerManagementRequiresAdmin(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
        var dispatcher = server.getCommands().getDispatcher();
        try {
            dispatcher.execute("notify trigger enable notifymod:player_death", source);
            throw helper.assertionException("OP 2 could manage triggers");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            if (com.takumistudios.notifymod.server.NotifyServer.get().triggers().enabled("notifymod:player_death")) {
                throw helper.assertionException("Denied command changed trigger state");
            }
        }
        helper.succeed();
    }
    @GameTest
    public void triggersLoadDisabledAndCommandsExist(GameTestHelper helper) {
        var manager = com.takumistudios.notifymod.server.NotifyServer.get().triggers();
        var trigger = manager.all().get("notifymod:player_death");
        if (trigger == null || trigger.enabled() || !manager.errors().isEmpty()) {
            throw helper.assertionException("Trigger default/load failure: " + manager.errors());
        }
        var command = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("notify").getChild("trigger");
        for (String action : java.util.List.of("list", "info", "enable", "disable", "set", "test", "fire", "audience")) {
            if (command == null || command.getChild(action) == null) throw helper.assertionException("Missing trigger command " + action);
        }
        helper.succeed();
    }
    @GameTest
    public void modIsLoaded(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded(NotifyMod.MOD_ID)) {
            throw helper.assertionException("Notify Mod no está cargado");
        }
        helper.succeed();
    }

    /** Fase 1: /notify existe y la plantilla de ejemplo del mod se carga sin errores. */
    @GameTest
    public void commandAndTemplatesAreLoaded(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (server.getCommands().getDispatcher().getRoot().getChild("notify") == null) {
            throw helper.assertionException("/notify no está registrado");
        }
        var templates = com.takumistudios.notifymod.server.NotifyServer.get().templates();
        if (templates.get("notifymod:restart_warning") == null) {
            throw helper.assertionException("No se cargó notifymod:restart_warning. Errores: " + templates.errors());
        }
        if (!templates.errors().isEmpty()) {
            throw helper.assertionException("Plantillas con errores: " + templates.errors());
        }
        helper.succeed();
    }

    /** Enviar sin jugadores conectados no falla y no llega a nadie. */
    @GameTest
    public void dispatchWithoutPlayersIsSafe(GameTestHelper helper) {
        var template = com.takumistudios.notifymod.server.NotifyServer.get().templates().get("notifymod:restart_warning");
        var notification = template.toNotification(com.takumistudios.notifymod.core.NotificationArgs.of(
                java.util.Map.of("minutos", "5")), null, null, null);
        int delivered = com.takumistudios.notifymod.server.NotificationDispatcher.send(java.util.List.of(), notification);
        if (delivered != 0) {
            throw helper.assertionException("Se entregó a " + delivered + " jugadores inexistentes");
        }
        helper.succeed();
    }
}
