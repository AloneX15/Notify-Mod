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

            mediaPipelineDrawsEveryFormat(context);
            notificationsReachTheHud(context, world);
            timelinePresentationPlays(context, world);
            modelsAndPreload(context, world);
        }
    }

    /**
     * Fase 3 (§8.3, §9): ítems, bloques y mobs en una presentación, una spritesheet con .mcmeta e imágenes
     * decodificadas en segundo plano.
     */
    private static void modelsAndPreload(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("notify showcase notifymod-test:models message=\"Modelos 3D\"");
        context.waitTicks(30);
        context.takeScreenshot("notifymod_phase3_models");
        String problem = context.computeOnClient(client -> {
            var state = com.takumistudios.notifymod.client.NotifyModClient.state();
            if (state.center() == null || !"notifymod-test:models".equals(
                    state.center().notification().presentation())) {
                return "la presentación de modelos no está en el centro";
            }
            if (!com.takumistudios.notifymod.client.NotifyModClient.media()
                    .isReady("notifymod-test:notify/media/sample.gif")) {
                return "la imagen precargada no está lista tras 1,5 s";
            }
            var sprite = com.takumistudios.notifymod.client.NotifyModClient.media()
                    .get("notifymod-test:notify/media/sprite.png");
            if (sprite == null) {
                return "la spritesheet con .mcmeta no está lista tras 1,5 s";
            }
            if (sprite.frames().frameCount() != 4 || sprite.frames().height() != 16
                    || sprite.frames().delayMs(0) != 200) {
                return "la spritesheet no se cortó según su .mcmeta: " + sprite.frames().frameCount()
                        + " fotogramas de " + sprite.frames().width() + "x" + sprite.frames().height();
            }
            return hudFailure();
        });
        world.getServer().runCommand("notify clear");
        context.waitTicks(10);
        if (problem != null) {
            throw new AssertionError("Fase 3: " + problem);
        }
    }

    /**
     * Fase 2 (§8): una presentación con línea de tiempo (sonido, forma con degradado, GIF, Lottie y textos animados) se
     * reproduce en el centro y, en versión compacta, en una esquina.
     */
    private static void timelinePresentationPlays(ClientGameTestContext context, TestSingleplayerContext world) {
        String problem = context.computeOnClient(client -> {
            var registry = com.takumistudios.notifymod.client.NotifyModClient.presentations();
            if (registry.get("notifymod-test:cinematic") == null) {
                return "no se cargó notifymod-test:cinematic. Errores: " + registry.errors();
            }
            return null;
        });
        if (problem != null) {
            throw new AssertionError("Presentaciones: " + problem);
        }
        world.getServer().runCommand(
                "notify showcase notifymod-test:cinematic message=\"ELIMINADO\" jugador=Steve");
        world.getServer().runCommand(
                "notify showcase notifymod-test:cinematic message=\"Compacta\" placement=bottom_right jugador=Alex");
        context.waitTicks(50); // 2,5 s: todas las pistas ya han entrado
        context.takeScreenshot("notifymod_phase2_timeline");
        problem = context.computeOnClient(client -> {
            var state = com.takumistudios.notifymod.client.NotifyModClient.state();
            if (state.center() == null || !"notifymod-test:cinematic".equals(
                    state.center().notification().presentation())) {
                return "la presentación no está en el centro";
            }
            if (state.center().end() - state.center().start() != 7000) {
                return "no se usa la duración de la presentación (7 s)";
            }
            if (state.corner(com.takumistudios.notifymod.core.Placement.BOTTOM_RIGHT).isEmpty()) {
                return "no está la versión compacta en bottom_right";
            }
            return hudFailure();
        });
        world.getServer().runCommand("notify clear");
        context.waitTicks(10);
        if (problem != null) {
            throw new AssertionError("Línea de tiempo: " + problem);
        }
    }

    /** Fase 1: un Showcase de texto en el centro y tarjetas en dos esquinas llegan del servidor y se dibujan. */
    private static void notificationsReachTheHud(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runCommand("notify showcase notifymod:motd message=\"Notify Mod: Showcase de prueba\" duration=20s");
        world.getServer().runCommand("notify hud Tarjeta de esquina placement=top_right priority=high duration=20s");
        world.getServer().runCommand("notify hud Otra tarjeta placement=bottom_left duration=20s");
        world.getServer().runCommand("notify showcase restart_warning minutos=5");
        context.waitTicks(10);
        context.takeScreenshot("notifymod_phase1_hud");
        String problem = context.computeOnClient(client -> {
            var state = com.takumistudios.notifymod.client.NotifyModClient.state();
            if (state == null) {
                return "el estado del cliente no se inició";
            }
            if (state.center() == null) {
                return "no hay Showcase en el centro";
            }
            if (state.corner(com.takumistudios.notifymod.core.Placement.TOP_RIGHT).isEmpty()) {
                return "no hay tarjeta en top_right";
            }
            if (state.corner(com.takumistudios.notifymod.core.Placement.BOTTOM_LEFT).isEmpty()) {
                return "no hay tarjeta en bottom_left";
            }
            if (state.corner(com.takumistudios.notifymod.core.Placement.TOP).isEmpty()) {
                return "la plantilla restart_warning no llegó (arriba)";
            }
            return hudFailure();
        });
        world.getServer().runCommand("notify clear");
        context.waitTicks(10);
        if (problem != null) {
            throw new AssertionError("Notificaciones: " + problem);
        }
    }

    /**
     * La capa del HUD captura sus propios fallos para no tumbar el juego, así que un error de dibujo no rompe el test
     * por sí solo: hay que preguntarlo. Llamar en el hilo del cliente.
     */
    private static String hudFailure() {
        var hud = com.takumistudios.notifymod.client.NotifyModClient.hud();
        if (hud.failed()) {
            return "la capa del HUD falló y se desactivó (ver el log)";
        }
        if (hud.renderer().disabledModels() > 0) {
            return hud.renderer().disabledModels() + " modelos 3D fallaron al dibujarse (ver el log)";
        }
        return null;
    }

    /** Fase 0 (§24): un archivo de cada formato se decodifica y se dibuja en el HUD. */
    private static void mediaPipelineDrawsEveryFormat(ClientGameTestContext context) {
        MediaPocHud.enabled = true;
        context.waitTicks(5);
        context.takeScreenshot("notifymod_media_poc");
        String problems = context.computeOnClient(client -> {
            if (!MediaPocHud.ERRORS.isEmpty()) {
                return String.join("; ", MediaPocHud.ERRORS);
            }
            return MediaPocHud.loadedRows() == MediaPocHud.FILES.length ? null
                    : "solo se cargaron " + MediaPocHud.loadedRows() + " de " + MediaPocHud.FILES.length + " formatos";
        });
        MediaPocHud.enabled = false;
        if (problems != null) {
            throw new AssertionError("Tubería de medios: " + problems);
        }
    }
}
