package com.takumistudios.notifymod.core;

import java.util.Locale;

/** Duraciones legibles en plantillas y comandos: {@code 500ms}, {@code 30s}, {@code 5m}, {@code 1h}, {@code 2d}. */
public final class Durations {
    /** Tope para que ningún dato de configuración desborde un reloj: 365 días. */
    public static final long MAX_MS = 365L * 24 * 60 * 60 * 1000;

    private Durations() {
    }

    /** Devuelve milisegundos. Un número sin unidad se interpreta como milisegundos. */
    public static long parseMillis(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Duración vacía");
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        long factor;
        String number;
        if (s.endsWith("ms")) {
            factor = 1;
            number = s.substring(0, s.length() - 2);
        } else if (s.endsWith("s")) {
            factor = 1000;
            number = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            factor = 60_000;
            number = s.substring(0, s.length() - 1);
        } else if (s.endsWith("h")) {
            factor = 3_600_000;
            number = s.substring(0, s.length() - 1);
        } else if (s.endsWith("d")) {
            factor = 86_400_000;
            number = s.substring(0, s.length() - 1);
        } else {
            factor = 1;
            number = s;
        }
        if (number.isEmpty() || !number.chars().allMatch(c -> c >= '0' && c <= '9') || number.length() > 12) {
            throw new IllegalArgumentException("Duración no válida: " + text);
        }
        long value = Long.parseLong(number);
        if (value > MAX_MS / factor) {
            throw new IllegalArgumentException("Duración demasiado larga: " + text);
        }
        return value * factor;
    }

    /** Formato corto inverso de {@link #parseMillis(String)}: usa la unidad más grande que sea exacta. */
    public static String format(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duración negativa");
        }
        if (millis != 0) {
            if (millis % 86_400_000 == 0) {
                return millis / 86_400_000 + "d";
            }
            if (millis % 3_600_000 == 0) {
                return millis / 3_600_000 + "h";
            }
            if (millis % 60_000 == 0) {
                return millis / 60_000 + "m";
            }
            if (millis % 1000 == 0) {
                return millis / 1000 + "s";
            }
        }
        return millis + "ms";
    }
}


