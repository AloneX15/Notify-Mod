package com.takumistudios.notifymod.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Test de cliente: abre un mundo, espera a que se rendericen los chunks y guarda capturas en
 * versions/<v>/build/run/clientGameTest/screenshots/. Ejecutar con ./gradlew :26.3:runClientGameTest.
 */
public class NotifyModClientGameTest implements FabricClientGameTest {
    private static void waitForChunks(TestSingleplayerContext world) {
        //? if >=26.2 {
        world.getConnection().waitForChunksRender();
        //?} else {
        /*world.getClientLevel().waitForChunksRender();
        *///?}
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            waitForChunks(world);
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            context.waitTicks(20);
            context.takeScreenshot("notifymod_world");
        }
    }
}
