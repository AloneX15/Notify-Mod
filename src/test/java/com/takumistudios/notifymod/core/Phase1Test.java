package com.takumistudios.notifymod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Fase 1: opciones de los comandos, plantillas, notificaciones y límite de uso. */
class Phase1Test {
    private static final Set<String> OPTS = Set.of("placement", "priority", "key", "duration", "chat", "message");

    @Test
    void opcionesConComillasYPalabrasSueltas() {
        CommandOptions.Parsed p = CommandOptions.parse("Hola  mundo placement=top_right message=\"Reinicio en 5\" x=1",
                OPTS, false);
        assertEquals("top_right", p.option("placement"));
        assertEquals("Reinicio en 5", p.message());
        assertEquals(List.of("Hola", "mundo", "x=1"), p.words());
        assertTrue(p.args().isEmpty());
    }

    @Test
    void argumentosDePlantillaYEscapes() {
        CommandOptions.Parsed p = CommandOptions.parse("minutos=5 jugador=\"Steve \\\"el bueno\\\"\" 2+2=4", OPTS, true);
        assertEquals(Map.of("minutos", "5", "jugador", "Steve \"el bueno\""), p.args());
        assertEquals("2+2=4", p.message());
        assertThrows(IllegalArgumentException.class, () -> CommandOptions.parse("message=\"sin cerrar", OPTS, false));
        assertThrows(IllegalArgumentException.class, () -> CommandOptions.parse("key=a key=b", OPTS, false));
        // Un '=' entre comillas no convierte la palabra en opción
        assertEquals(List.of("placement=top"), CommandOptions.parse("\"placement=top\"", OPTS, false).words());
        assertTrue(CommandOptions.parseBoolean("sí"));
        assertThrows(IllegalArgumentException.class, () -> CommandOptions.parseBoolean("quizá"));
    }

    @Test
    void notificacionValidaYCriticalAlCentro() {
        Notification n = new Notification(EnumSet.of(Channel.HUD), null, Message.literal("hola"), null,
                Priority.CRITICAL, Placement.BOTTOM_LEFT, "k", 5000);
        assertEquals(Placement.CENTER, n.placement());
        assertEquals(Notification.MOTD, n.presentation());
        assertThrows(IllegalArgumentException.class, () -> new Notification(EnumSet.noneOf(Channel.class), null,
                Message.literal("x"), null, null, null, null, 5000));
        assertThrows(IllegalArgumentException.class, () -> new Notification(EnumSet.of(Channel.HUD), "../malo",
                Message.literal("x"), null, null, null, null, 5000));
        assertThrows(IllegalArgumentException.class, () -> new Notification(EnumSet.of(Channel.HUD), null,
                Message.literal("x"), null, null, null, "clave con espacios", 5000));
        assertThrows(IllegalArgumentException.class, () -> new Notification(EnumSet.of(Channel.HUD), null,
                Message.literal("x"), null, null, null, null, 10));
        assertThrows(IllegalArgumentException.class, () -> Message.literal("x".repeat(Message.MAX_TEXT + 1)));
    }

    @Test
    void plantillaDelPlan() {
        Template t = Template.parse("notifymod:restart_warning", """
                {
                  "format_version": 1,
                  "_comment": "Aviso de reinicio",
                  "channels": ["showcase"],
                  "presentation": "notifymod:motd",
                  "placement": "top",
                  "priority": "HIGH",
                  "message": { "translate": "notifymod.restart.warning", "with": ["{minutos}"] },
                  "mirror_to_chat": true,
                  "key": "restart_warning",
                  "cooldown": "30s"
                }""");
        assertEquals(EnumSet.of(Channel.SHOWCASE, Channel.CHAT), EnumSet.copyOf(t.channels()));
        assertEquals(30_000, t.cooldownMs());
        assertEquals(Notification.DEFAULT_SHOWCASE_MS, t.durationMs());
        Notification n = t.toNotification(NotificationArgs.of(Map.of("minutos", "5")), null, null, false);
        assertFalse(n.has(Channel.CHAT));
        assertEquals(List.of("5"), n.message().resolveWith(n.args()));
        assertEquals(Placement.TOP, n.placement());
        assertTrue(n.message().translate());
    }

    @Test
    void plantillasNoValidas() {
        assertThrows(IllegalArgumentException.class, () -> Template.parse("notifymod:x", "[1]"));
        assertThrows(IllegalArgumentException.class, () -> Template.parse("notifymod:x", "{}"));
        assertThrows(IllegalArgumentException.class, () -> Template.parse("notifymod:x",
                "{\"format_version\": 99, \"message\": \"a\"}"));
        assertThrows(IllegalArgumentException.class, () -> Template.parse("notifymod:x",
                "{\"message\": \"a\", \"placement\": \"izquierda\"}"));
        assertThrows(IllegalArgumentException.class, () -> Template.parse("Mal Id", "{\"message\": \"a\"}"));
        Template simple = Template.parse("ejemplo:hola", "{\"channels\": \"hud\", \"message\": \"Hola {jugador}\"}");
        assertEquals(Placement.TOP_RIGHT, simple.placement());
        assertNull(simple.key());
        assertEquals("Hola Steve", simple.message().resolveLiteral(NotificationArgs.of(Map.of("jugador", "Steve"))));
    }

    @Test
    void limiteDeUso() {
        RateLimiter limiter = new RateLimiter(60_000);
        assertTrue(limiter.tryAcquire("a", 2, 0));
        assertTrue(limiter.tryAcquire("a", 2, 10));
        assertFalse(limiter.tryAcquire("a", 2, 20));
        assertTrue(limiter.tryAcquire("b", 2, 20));
        assertTrue(limiter.tryAcquire("a", 2, 60_001));
    }
}

