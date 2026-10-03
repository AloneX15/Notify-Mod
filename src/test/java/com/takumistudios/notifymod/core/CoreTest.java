package com.takumistudios.notifymod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Núcleo de la Fase 1 (§6, §11 y §23 del plan): cola, colocación, argumentos, marcadores y duraciones. */
class CoreTest {
    private final AtomicLong now = new AtomicLong(1_000);

    private NotificationQueue<String> queue(int capacity) {
        return new NotificationQueue<>(capacity, now::get);
    }

    // ------------------------------------------------------------ cola

    @Test
    void salePrimeroLaPrioridadMasAltaYLuegoPorOrden() {
        NotificationQueue<String> q = queue(10);
        q.offer("a", Priority.NORMAL, null);
        q.offer("b", Priority.LOW, null);
        q.offer("c", Priority.HIGH, null);
        q.offer("d", Priority.NORMAL, null);
        assertEquals(Optional.of(Priority.HIGH), q.peekPriority());
        assertEquals("c", q.poll().orElseThrow());
        assertEquals("a", q.poll().orElseThrow());
        assertEquals("d", q.poll().orElseThrow());
        assertEquals("b", q.poll().orElseThrow());
        assertTrue(q.poll().isEmpty());
    }

    @Test
    void caducaSegunLaPrioridadPeroCriticalNo() {
        NotificationQueue<String> q = queue(10);
        q.offer("low", Priority.LOW, null);
        q.offer("crit", Priority.CRITICAL, null);
        q.offer("propio", Priority.HIGH, null, 50);
        now.addAndGet(Priority.LOW.defaultTtlMs());
        assertEquals(1, q.size());
        now.set(Long.MAX_VALUE - 10);
        assertEquals("crit", q.poll().orElseThrow());
    }

    @Test
    void laMismaClaveSustituyeALaAnterior() {
        NotificationQueue<String> q = queue(10);
        q.offer("reinicio 5 min", Priority.NORMAL, "restart");
        q.offer("otro", Priority.NORMAL, null);
        assertEquals(NotificationQueue.OfferResult.REPLACED, q.offer("reinicio 1 min", Priority.NORMAL, "restart"));
        assertEquals(2, q.size());
        // Conserva el turno de la que sustituye
        assertEquals("reinicio 1 min", q.poll().orElseThrow());
        assertTrue(q.removeKey("x") == false);
    }

    @Test
    void llenaSoloEntraSiExpulsaAOtraDeMenorPrioridad() {
        NotificationQueue<String> q = queue(2);
        q.offer("n1", Priority.NORMAL, null);
        q.offer("l1", Priority.LOW, null);
        assertEquals(NotificationQueue.OfferResult.REJECTED_FULL, q.offer("l2", Priority.LOW, null));
        assertEquals(NotificationQueue.OfferResult.EVICTED_OTHER, q.offer("h1", Priority.HIGH, null));
        assertEquals("h1", q.poll().orElseThrow());
        assertEquals("n1", q.poll().orElseThrow());
        assertTrue(q.isEmpty());
    }

    @Test
    void capacidadNoValida() {
        assertThrows(IllegalArgumentException.class, () -> queue(0));
    }

    // ------------------------------------------------------------ colocación

    @Test
    void criticalSiempreVaAlCentroYNoSeOculta() {
        assertEquals(Placement.CENTER, Placement.effective(Placement.BOTTOM_RIGHT, Priority.CRITICAL));
        assertFalse(Placement.hiddenByPlayer(Placement.BOTTOM_RIGHT, Priority.CRITICAL, true));
    }

    @Test
    void elJugadorSoloOcultaLasEsquinas() {
        assertFalse(Placement.hiddenByPlayer(Placement.CENTER, Priority.NORMAL, true));
        assertTrue(Placement.hiddenByPlayer(Placement.TOP_LEFT, Priority.HIGH, true));
        assertTrue(Placement.hiddenByPlayer(Placement.BOTTOM, Priority.LOW, true));
        assertFalse(Placement.hiddenByPlayer(Placement.TOP_LEFT, Priority.HIGH, false));
        assertEquals(Placement.CENTER, Placement.effective(null, Priority.NORMAL));
    }

    @Test
    void analizaNombres() {
        assertEquals(Placement.BOTTOM_RIGHT, Placement.parse("bottom_right"));
        assertEquals(Priority.HIGH, Priority.parse(" high "));
        assertEquals(Channel.SHOWCASE, Channel.parse("Showcase"));
        assertThrows(IllegalArgumentException.class, () -> Placement.parse("izquierda"));
        assertThrows(IllegalArgumentException.class, () -> Priority.parse(null));
        assertEquals("top_left", Placement.TOP_LEFT.id());
    }

    // ------------------------------------------------------------ argumentos y marcadores

    @Test
    void validaYLimpiaLosArgumentos() {
        NotificationArgs args = NotificationArgs.of(Map.of("jugador", "§cNotch\n"));
        assertEquals("cNotch", args.get("jugador"));
        assertThrows(IllegalArgumentException.class, () -> NotificationArgs.of(Map.of("Mayus", "x")));
        assertThrows(IllegalArgumentException.class, () -> NotificationArgs.of(Map.of("a", "x".repeat(257))));
        Map<String, String> many = new HashMap<>();
        for (int i = 0; i <= NotificationArgs.MAX_ARGS; i++) {
            many.put("a" + i, "x");
        }
        assertThrows(IllegalArgumentException.class, () -> NotificationArgs.of(many));
        assertEquals(NotificationArgs.EMPTY, NotificationArgs.of(null));
    }

    @Test
    void marcadoresEnUnaSolaPasada() {
        NotificationArgs args = NotificationArgs.of(Map.of("jugador", "{causa}", "causa", "lava"));
        assertEquals("{causa} murió por lava", Placeholders.apply("{jugador} murió por {causa}", args));
        assertEquals("sin {dato} y {", Placeholders.apply("sin {dato} y {", args));
        assertEquals("{literal}", Placeholders.apply("{{literal}", args));
        assertEquals("texto", Placeholders.apply("texto", args));
    }

    // ------------------------------------------------------------ duraciones

    @Test
    void duraciones() {
        assertEquals(500, Durations.parseMillis("500ms"));
        assertEquals(30_000, Durations.parseMillis("30s"));
        assertEquals(300_000, Durations.parseMillis("5m"));
        assertEquals(3_600_000, Durations.parseMillis("1h"));
        assertEquals(7000, Durations.parseMillis("7000"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseMillis("-5s"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseMillis("s"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseMillis("9999999d"));
        assertEquals("30s", Durations.format(30_000));
        assertEquals("1500ms", Durations.format(1500));
        assertEquals("2d", Durations.format(172_800_000));
    }
}

