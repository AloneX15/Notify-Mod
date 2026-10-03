package com.takumistudios.notifymod.core;

import java.util.Locale;

/**
 * Prioridad de una notificación (§11 del plan). El orden de declaración es el orden de importancia.
 *
 * <p>La caducidad es el tiempo máximo que un aviso puede esperar en la cola antes de descartarse. CRITICAL no caduca.
 */
public enum Priority {
    LOW(30_000L),
    NORMAL(120_000L),
    HIGH(300_000L),
    CRITICAL(Long.MAX_VALUE);

    private final long defaultTtlMs;

    Priority(long defaultTtlMs) {
        this.defaultTtlMs = defaultTtlMs;
    }

    /** Tiempo de espera máximo en cola, en milisegundos. {@link Long#MAX_VALUE} significa "no caduca". */
    public long defaultTtlMs() {
        return defaultTtlMs;
    }

    public boolean isAtLeast(Priority other) {
        return compareTo(other) >= 0;
    }

    /** Admite mayúsculas o minúsculas ("high", "HIGH"). */
    public static Priority parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Prioridad vacía");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Prioridad desconocida: " + text);
        }
    }
}

