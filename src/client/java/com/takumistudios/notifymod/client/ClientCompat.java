package com.takumistudios.notifymod.client;

import net.minecraft.client.Minecraft;

/**
 * Capa de aislamiento (§10): todo lo que cambia de nombre entre versiones de Minecraft se resuelve aquí con
 * comentarios de Stonecutter, para que el resto del código sea igual en todas.
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    /** ¿Hay una pantalla abierta (inventario, chat, menú...)? */
    public static boolean screenOpen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen() != null;
        //?} else {
        /*return client.screen != null;
        *///?}
    }
}

