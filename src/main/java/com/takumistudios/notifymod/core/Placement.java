package com.takumistudios.notifymod.core;

import java.util.Locale;

/**
 * Las siete posiciones de pantalla (§6.2 del plan) y las reglas de ocultación (§6.4).
 *
 * <ul>
 *   <li>Solo lo que no está en el centro se puede ocultar con "Ocultar avisos de esquina".</li>
 *   <li>CRITICAL siempre va al centro, ignore lo que pida la plantilla o el admin.</li>
 * </ul>
 */
public enum Placement {
    CENTER,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP,
    BOTTOM;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Todo lo que no es el centro se dibuja en versión compacta y el jugador lo puede ocultar. */
    public boolean isDocked() {
        return this != CENTER;
    }

    /** Posición en la que se mostrará de verdad: CRITICAL nunca sale de centro. */
    public static Placement effective(Placement requested, Priority priority) {
        if (priority == Priority.CRITICAL) {
            return CENTER;
        }
        return requested == null ? CENTER : requested;
    }

    /**
     * Si el cliente debe descartar el aviso sin mostrarlo. El jugador solo puede ocultar los avisos de esquina; el
     * centro y CRITICAL nunca se ocultan.
     */
    public static boolean hiddenByPlayer(Placement requested, Priority priority, boolean hideCornerNotifications) {
        return hideCornerNotifications && effective(requested, priority).isDocked();
    }

    public static Placement parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Colocación vacía");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Colocación desconocida: " + text
                    + " (válidas: center, top_left, top_right, bottom_left, bottom_right, top, bottom)");
        }
    }
}

