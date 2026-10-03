package com.takumistudios.notifymod.client.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.takumistudios.notifymod.client.hud.HudLayout;
import com.takumistudios.notifymod.core.Channel;
import com.takumistudios.notifymod.core.Message;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.Placement;
import com.takumistudios.notifymod.core.Priority;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Estado del cliente (§6.4 y §11): ocultación, interrupciones, pilas de esquina y cancelación. */
class ClientStateTest {
    private final AtomicLong now = new AtomicLong(10_000);
    private boolean hide;

    private ClientNotificationState state() {
        return new ClientNotificationState(now::get, new ClientNotificationState.Options() {
            @Override
            public boolean hideCornerNotifications() {
                return hide;
            }

            @Override
            public int maxVisibleCards() {
                return 2;
            }
        });
    }

    private static Notification showcase(String text, Priority priority, Placement placement, String key) {
        return new Notification(EnumSet.of(Channel.SHOWCASE), null, Message.literal(text), null, priority, placement,
                key, 5000);
    }

    private static Notification card(String text, Placement placement) {
        return new Notification(EnumSet.of(Channel.HUD), null, Message.literal(text), null, Priority.NORMAL, placement,
                null, 5000);
    }

    @Test
    void elJugadorSoloOcultaLasEsquinas() {
        hide = true;
        ClientNotificationState s = state();
        s.receive(card("esquina", Placement.TOP_RIGHT));
        s.receive(showcase("centro", Priority.NORMAL, Placement.CENTER, null));
        s.receive(showcase("crítico en esquina", Priority.CRITICAL, Placement.BOTTOM_LEFT, null));
        s.tick(false);
        assertTrue(s.corner(Placement.TOP_RIGHT).isEmpty());
        assertNotNull(s.center());
        // Los ocultos quedan en el historial
        assertTrue(s.history().stream().anyMatch(ClientNotificationState.HistoryEntry::hidden));
    }

    @Test
    void criticalInterrumpeYHighAcortaLaSalida() {
        ClientNotificationState s = state();
        s.receive(showcase("normal", Priority.NORMAL, Placement.CENTER, null));
        s.tick(false);
        now.addAndGet(1000);
        s.receive(showcase("crítico", Priority.CRITICAL, Placement.CENTER, null));
        assertTrue(s.center().end() <= now.get() + ClientNotificationState.CRITICAL_FADE_MS);
        now.addAndGet(ClientNotificationState.CRITICAL_FADE_MS);
        s.tick(false);
        assertEquals("crítico", s.center().notification().message().text());
    }

    @Test
    void conPantallaAbiertaElCentroEsperaSalvoCritical() {
        ClientNotificationState s = state();
        s.receive(showcase("normal", Priority.NORMAL, Placement.CENTER, null));
        s.tick(true);
        assertNull(s.center());
        s.receive(showcase("crítico", Priority.CRITICAL, Placement.CENTER, null));
        s.tick(true);
        assertEquals(Priority.CRITICAL, s.center().notification().priority());
        now.addAndGet(5000);
        s.tick(false);
        assertEquals("normal", s.center().notification().message().text());
    }

    @Test
    void pilaDeEsquinaConTopeYClaves() {
        ClientNotificationState s = state();
        s.receive(card("a", Placement.TOP_LEFT));
        s.receive(card("b", Placement.TOP_LEFT));
        s.receive(card("c", Placement.TOP_LEFT));
        assertEquals(2, s.corner(Placement.TOP_LEFT).size());
        now.addAndGet(5000);
        s.tick(false);
        assertEquals(1, s.corner(Placement.TOP_LEFT).size());
        assertEquals("c", s.corner(Placement.TOP_LEFT).get(0).notification().message().text());
        // Una tarjeta "al centro" sube arriba: el centro es solo para el Showcase
        assertEquals(Placement.TOP, ClientNotificationState.placementFor(card("x", Placement.CENTER)));
    }

    @Test
    void cancelarPorClaveYTodo() {
        ClientNotificationState s = state();
        s.receive(showcase("uno", Priority.NORMAL, Placement.CENTER, "k1"));
        s.receive(showcase("dos", Priority.NORMAL, Placement.CENTER, "k2"));
        s.tick(false);
        s.cancel("k2");
        assertEquals(0, s.pendingCenter());
        s.cancel("*");
        now.addAndGet(ClientNotificationState.CRITICAL_FADE_MS);
        s.tick(false);
        assertNull(s.center());
        s.reset();
        assertTrue(s.corner(Placement.TOP_LEFT).isEmpty());
    }

    @Test
    void opacidadContinuaAlAcortar() {
        Notification n = showcase("x", Priority.NORMAL, Placement.CENTER, null);
        ActiveNotification a = new ActiveNotification(n, Placement.CENTER, 0);
        assertEquals(0f, a.alpha(0));
        assertEquals(0.5f, a.alpha(100), 0.001);
        assertEquals(1f, a.alpha(2000));
        a.endEarly(2000, 300);
        assertEquals(1f, a.alpha(2000), 0.001);
        assertEquals(2300, a.end());
        assertTrue(a.finished(2300));
        a.endEarly(2100, 1000); // nunca alarga
        assertEquals(2300, a.end());
    }

    @Test
    void posicionesDeLasTarjetas() {
        assertEquals(4, HudLayout.cardOrigin(Placement.TOP_LEFT, 400, 300, 100, 20, 0)[0]);
        assertEquals(400 - 4 - 100, HudLayout.cardOrigin(Placement.TOP_RIGHT, 400, 300, 100, 20, 0)[0]);
        assertEquals(300 - 4 - 20 - 23, HudLayout.cardOrigin(Placement.BOTTOM_LEFT, 400, 300, 100, 20, 23)[1]);
        assertEquals(150, HudLayout.cardOrigin(Placement.TOP, 400, 300, 100, 20, 0)[0]);
    }
}


