package com.takumistudios.notifymod.client.timeline;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * Una animación de entrada, de salida o en bucle (§8.4). {@code amount} depende del tipo: distancia en fracciones del
 * lienzo (slide, shake), escala inicial (scale), grados (rotate, spin) o intensidad (pulse).
 */
public record AnimSpec(Type type, DoubleUnaryOperator easing, int durationMs, float amount) {
    public enum Type {
        FADE(0), SLIDE_LEFT(0.15f), SLIDE_RIGHT(0.15f), SLIDE_UP(0.15f), SLIDE_DOWN(0.15f), SCALE(0.5f), POP(0),
        ROTATE(90), TYPEWRITER(0),
        // Solo en bucle
        SHAKE(0.01f), PULSE(0.05f), SPIN(360);

        final float defaultAmount;

        Type(float defaultAmount) {
            this.defaultAmount = defaultAmount;
        }

        public boolean isLoop() {
            return this == SHAKE || this == PULSE || this == SPIN;
        }

        public static Type parse(String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Animación desconocida: " + text + " (fade, slide_left, slide_right, "
                        + "slide_up, slide_down, scale, pop, rotate, typewriter; en bucle: shake, pulse, spin)");
            }
        }
    }

    public AnimSpec {
        if (durationMs < 1) {
            throw new IllegalArgumentException("La animación debe durar al menos 1 ms");
        }
    }

    /**
     * Aplica la animación con visibilidad {@code v} (0 = oculto, 1 = en su sitio; puede pasarse de 1 con back o
     * elastic) sobre el estado.
     */
    void apply(double v, TrackState s) {
        float visible = (float) v;
        float clamped = Math.max(0, Math.min(1, visible));
        switch (type) {
            case FADE -> s.alpha *= clamped;
            case SLIDE_RIGHT -> {
                s.dx -= (1 - visible) * amount;
                s.alpha *= clamped;
            }
            case SLIDE_LEFT -> {
                s.dx += (1 - visible) * amount;
                s.alpha *= clamped;
            }
            case SLIDE_UP -> {
                s.dy += (1 - visible) * amount;
                s.alpha *= clamped;
            }
            case SLIDE_DOWN -> {
                s.dy -= (1 - visible) * amount;
                s.alpha *= clamped;
            }
            case SCALE -> {
                s.scale *= amount + (1 - amount) * visible;
                s.alpha *= clamped;
            }
            case POP -> {
                s.scale *= Math.max(0, visible);
                s.alpha *= Math.min(1, clamped * 2);
            }
            case ROTATE -> {
                s.rotation += (1 - visible) * amount;
                s.alpha *= clamped;
            }
            case TYPEWRITER -> s.reveal = Math.min(s.reveal, clamped);
            default -> {
            }
        }
    }

    /** Animación en bucle a {@code localMs} desde que empezó la pista; {@code durationMs} es el periodo. */
    void applyLoop(long localMs, TrackState s) {
        double phase = (localMs % durationMs) / (double) durationMs;
        double wave = Math.sin(phase * 2 * Math.PI);
        switch (type) {
            case SHAKE -> {
                s.dx += (float) (wave * amount);
                s.dy += (float) (Math.sin(phase * 4 * Math.PI + 1) * amount * 0.5);
            }
            case PULSE -> s.scale *= (float) (1 + wave * amount);
            case SPIN -> s.rotation += (float) (easing.applyAsDouble(phase) * amount);
            default -> {
            }
        }
    }
}

