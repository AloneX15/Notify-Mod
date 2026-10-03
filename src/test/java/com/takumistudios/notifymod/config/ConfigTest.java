package com.takumistudios.notifymod.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Configuración: valores por defecto, rangos, archivo dañado con copia .bak y escritura atómica. */
class ConfigTest {
    @TempDir
    Path dir;

    @Test
    void creaElArchivoConLosValoresPorDefecto() {
        Path file = dir.resolve("notifymod/server.json");
        assertEquals(ServerConfig.DEFAULTS, ServerConfig.load(file));
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling("server.json.tmp")));
    }

    @Test
    void limitaLosRangos() throws IOException {
        Path file = dir.resolve("client.json");
        Files.writeString(file, "{\"card_scale\": 9, \"max_visible_cards\": -3, \"hide_corner_notifications\": true}");
        ClientConfig c = ClientConfig.load(file);
        assertEquals(2.0, c.cardScale());
        assertEquals(1, c.maxVisibleCards());
        assertTrue(c.hideCornerNotifications());
        // Se reescribe normalizado
        assertTrue(Files.readString(file).contains("\"format_version\""));
    }

    @Test
    void archivoDanadoSeGuardaEnBak() throws IOException {
        Path file = dir.resolve("server.json");
        Files.writeString(file, "{ esto no es json", StandardCharsets.UTF_8);
        assertEquals(ServerConfig.DEFAULTS, ServerConfig.load(file));
        assertEquals("{ esto no es json", Files.readString(file.resolveSibling("server.json.bak")));
        assertEquals(ServerConfig.DEFAULTS, ServerConfig.load(file));
    }
}

