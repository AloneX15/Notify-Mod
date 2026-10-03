package com.takumistudios.notifymod.core;

import java.util.Locale;

/** Los tres canales de una notificación (§6.1 del plan). */
public enum Channel {
    CHAT,
    HUD,
    SHOWCASE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Channel parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Canal vacío");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Canal desconocido: " + text);
        }
    }
}

