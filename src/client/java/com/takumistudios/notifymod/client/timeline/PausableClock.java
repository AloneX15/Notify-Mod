package com.takumistudios.notifymod.client.timeline;

/**
 * Reloj de las presentaciones (§8.2): monotónico, independiente de los FPS y detenido mientras el juego está en pausa
 * (mundos de un jugador). Se actualiza una vez por fotograma con el reloj real.
 */
public final class PausableClock {
    private long pausedTotal;
    private long pausedSince = -1;
    private long lastReal;

    /** @param realMs reloj real monotónico; {@code paused} si el juego está en pausa ahora */
    public void update(long realMs, boolean paused) {
        lastReal = realMs;
        if (paused && pausedSince < 0) {
            pausedSince = realMs;
        } else if (!paused && pausedSince >= 0) {
            pausedTotal += realMs - pausedSince;
            pausedSince = -1;
        }
    }

    /** Tiempo de juego en ms: avanza con el reloj real salvo durante las pausas. */
    public long now() {
        long frozenAt = pausedSince >= 0 ? pausedSince : lastReal;
        return frozenAt - pausedTotal;
    }
}

