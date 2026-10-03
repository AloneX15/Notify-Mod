package com.takumistudios.notifymod.client.state;

import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.Placement;

/**
 * Una notificación en pantalla: cuándo empezó, cuándo termina y su opacidad en cada instante (entrada y salida
 * suaves). Los tiempos son milisegundos del reloj monotónico del cliente.
 */
public final class ActiveNotification {
    public static final long FADE_IN_MS = 200;
    public static final long FADE_OUT_MS = 300;

    private final Notification notification;
    private final Placement placement;
    private final long start;
    private long end;
    private long fadeOut = FADE_OUT_MS;
    /** Datos de dibujo que calcula el renderizador una sola vez (texto, líneas...). */
    public Object renderCache;

    public ActiveNotification(Notification notification, Placement placement, long now) {
        this(notification, placement, now, notification.durationMs());
    }

    /** @param durationMs duración real (la de la presentación si el cliente la conoce) */
    public ActiveNotification(Notification notification, Placement placement, long now, int durationMs) {
        this.notification = notification;
        this.placement = placement;
        this.start = now;
        this.end = now + Math.max(1, durationMs);
    }

    public Notification notification() {
        return notification;
    }

    public Placement placement() {
        return placement;
    }

    public long start() {
        return start;
    }

    public long end() {
        return end;
    }

    public boolean finished(long now) {
        return now >= end;
    }

    /** Opacidad entre 0 y 1, continua aunque se acorte la salida. */
    public float alpha(long now) {
        if (now < start || now >= end) {
            return 0f;
        }
        float in = Math.min(1f, (now - start) / (float) FADE_IN_MS);
        float out = Math.min(1f, (end - now) / (float) Math.max(1, fadeOut));
        return Math.min(in, out);
    }

    /** Acorta la salida: termina con un fundido de {@code fadeMs} desde la opacidad actual. Nunca alarga. */
    public void endEarly(long now, long fadeMs) {
        float a = alpha(now);
        long newEnd = now + Math.round(fadeMs * a);
        if (newEnd < end) {
            end = newEnd;
            fadeOut = Math.max(1, fadeMs);
        }
    }

    /** Progreso de 0 a 1 de toda la vida de la notificación (para barras de tiempo). */
    public float progress(long now) {
        long total = Math.max(1, end - start);
        return Math.max(0f, Math.min(1f, (now - start) / (float) total));
    }
}


