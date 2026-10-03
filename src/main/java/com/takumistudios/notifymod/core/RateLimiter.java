package com.takumistudios.notifymod.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Límite de usos por persona en una ventana deslizante (§23: abuso de comandos). No es seguro entre hilos: se usa
 * desde el hilo del servidor.
 */
public final class RateLimiter {
    private final long windowMs;
    private final Map<String, Deque<Long>> uses = new HashMap<>();

    public RateLimiter(long windowMs) {
        this.windowMs = windowMs;
    }

    /** Registra un uso si cabe en el límite y devuelve si se permite. */
    public boolean tryAcquire(String who, int maxPerWindow, long now) {
        Deque<Long> times = uses.computeIfAbsent(who, k -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst() <= now - windowMs) {
            times.pollFirst();
        }
        if (times.size() >= maxPerWindow) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    public void forget(String who) {
        uses.remove(who);
    }
}

