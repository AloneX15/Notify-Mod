package com.takumistudios.notifymod.client.timeline;

import java.util.Locale;

/** Punto de anclaje dentro del lienzo y alineación del elemento respecto a él (§8.3). */
public enum Anchor {
    TOP_LEFT(0, 0), TOP(0.5f, 0), TOP_RIGHT(1, 0),
    LEFT(0, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1, 0.5f),
    BOTTOM_LEFT(0, 1), BOTTOM(0.5f, 1), BOTTOM_RIGHT(1, 1);

    public final float fx;
    public final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    public static Anchor parse(String text) {
        if (text == null) {
            return CENTER;
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ancla desconocida: " + text);
        }
    }
}

