package com.takumistudios.notifymod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada común (servidor y cliente). Cada evento que se registre aquí debe ir protegido con try/catch para
 * que un error de Notify Mod nunca tumbe el juego.
 */
public final class NotifyMod implements ModInitializer {
    public static final String MOD_ID = "notifymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Notify Mod {} cargado. Creado por TakumiStudios.", version());
        try {
            com.takumistudios.notifymod.network.NotifyNetwork.register();
            com.takumistudios.notifymod.server.NotifyServer.get().init();
        } catch (RuntimeException e) {
            LOGGER.error("Notify Mod no se pudo iniciar del todo; el juego sigue sin sus funciones", e);
        }
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("desconocida");
    }
}
