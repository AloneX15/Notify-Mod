package com.takumistudios.notifymod.client.timeline;

/**
 * Estado de una pista en un instante: lo que el renderizador aplica (opacidad, desplazamiento en fracciones del
 * lienzo, escala, rotación en grados y fracción de texto visible). Mutable y reutilizable para no asignar memoria en
 * cada fotograma.
 */
public final class TrackState {
    public boolean visible;
    public float alpha;
    public float dx;
    public float dy;
    public float scale;
    public float rotation;
    public float reveal;
    /** Milisegundos desde que empezó la pista (para animaciones de fotogramas). */
    public long localMs;

    /**
     * Calcula el estado de {@code track} a {@code timeMs} desde el inicio de la presentación.
     *
     * @param presentationEnd fin de la presentación, para las pistas sin duración propia
     */
    public TrackState evaluate(TrackSpec track, long timeMs, int presentationEnd) {
        long start = track.time();
        long end = track.durationMs() > 0 ? start + track.durationMs() : presentationEnd;
        visible = timeMs >= start && timeMs < end;
        alpha = track.opacity();
        dx = 0;
        dy = 0;
        scale = 1;
        rotation = 0;
        reveal = 1;
        localMs = Math.max(0, timeMs - start);
        if (!visible) {
            return this;
        }
        AnimSpec in = track.in();
        if (in != null && localMs < in.durationMs()) {
            in.apply(in.easing().applyAsDouble(localMs / (double) in.durationMs()), this);
        }
        AnimSpec out = track.out();
        long remaining = end - timeMs;
        if (out != null && remaining < out.durationMs()) {
            double leaving = 1 - remaining / (double) out.durationMs();
            out.apply(1 - out.easing().applyAsDouble(leaving), this);
        }
        if (track.loop() != null) {
            track.loop().applyLoop(localMs, this);
        }
        alpha = Math.max(0, Math.min(1, alpha));
        return this;
    }
}

