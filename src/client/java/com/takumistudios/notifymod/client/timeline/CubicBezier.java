package com.takumistudios.notifymod.client.timeline;

/**
 * Curva de easing cúbica de Bézier con extremos (0,0) y (1,1), como {@code cubic-bezier()} de CSS y las tangentes de
 * los keyframes de Lottie. Inmutable; {@link #ease(double)} no asigna memoria.
 */
public final class CubicBezier {
    public static final CubicBezier LINEAR = new CubicBezier(0, 0, 1, 1);
    public static final CubicBezier EASE_IN_OUT = new CubicBezier(0.42, 0, 0.58, 1);

    private static final double EPSILON = 1e-6;

    private final double cx, bx, ax;
    private final double cy, by, ay;
    private final boolean linear;

    public CubicBezier(double x1, double y1, double x2, double y2) {
        if (!Double.isFinite(x1 + y1 + x2 + y2)) {
            throw new IllegalArgumentException("Puntos de control no válidos");
        }
        // Las x fuera de [0, 1] harían la curva no monótona en el tiempo
        x1 = Math.clamp(x1, 0, 1);
        x2 = Math.clamp(x2, 0, 1);
        cx = 3 * x1;
        bx = 3 * (x2 - x1) - cx;
        ax = 1 - cx - bx;
        cy = 3 * y1;
        by = 3 * (y2 - y1) - cy;
        ay = 1 - cy - by;
        linear = x1 == y1 && x2 == y2;
    }

    /** Progreso suavizado para un progreso lineal {@code t} en [0, 1]. */
    public double ease(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        if (linear) {
            return t;
        }
        return sample(ay, by, cy, solveX(t));
    }

    private double solveX(double x) {
        // Newton-Raphson y, si no converge, bisección
        double t = x;
        for (int i = 0; i < 8; i++) {
            double error = sample(ax, bx, cx, t) - x;
            if (Math.abs(error) < EPSILON) {
                return t;
            }
            double slope = (3 * ax * t + 2 * bx) * t + cx;
            if (Math.abs(slope) < EPSILON) {
                break;
            }
            t -= error / slope;
        }
        double lo = 0, hi = 1;
        t = x;
        for (int i = 0; i < 40 && lo < hi; i++) {
            double value = sample(ax, bx, cx, t);
            if (Math.abs(value - x) < EPSILON) {
                return t;
            }
            if (value < x) {
                lo = t;
            } else {
                hi = t;
            }
            t = (lo + hi) / 2;
        }
        return t;
    }

    private static double sample(double a, double b, double c, double t) {
        return ((a * t + b) * t + c) * t;
    }
}

