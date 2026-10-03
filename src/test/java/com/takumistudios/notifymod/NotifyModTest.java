package com.takumistudios.notifymod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests unitarios de la lógica pura (sin Minecraft). Todo bug corregido añade su caso aquí o en los gametests. */
class NotifyModTest {
    @Test
    void modIdIsValid() {
        assertTrue(NotifyMod.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"));
    }
}
