package com.takumistudios.notifymod.test;

import com.takumistudios.notifymod.NotifyMod;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/** Gametests de servidor: ./gradlew :26.3:runGameTest (y con -PcompatPack en la CI). */
public class NotifyModGameTests {
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
