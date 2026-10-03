package com.takumistudios.notifymod.client.timeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

/**
 * Curvas de easing (§8.4): {@code linear} y la familia estándar {@code sine}, {@code quad}, {@code cubic},
 * {@code quart}, {@code quint}, {@code expo}, {@code circ}, {@code back}, {@code elastic} y {@code bounce} en sus
 * variantes {@code ease_in_*}, {@code ease_out_*} y {@code ease_in_out_*}. También {@code ease_in}, {@code ease_out} y
 * {@code ease_in_out} (cúbicas) y {@code cubic_bezier(x1,y1,x2,y2)}. Entrada y salida en [0, 1] (back y elastic se
 * salen de ese rango a propósito).
 */
public final class Easing {
    private static final Map<String, DoubleUnaryOperator> BY_NAME = new LinkedHashMap<>();

    static {
        BY_NAME.put("linear", t -> t);
        family("sine", t -> 1 - Math.cos(t * Math.PI / 2));
        family("quad", t -> t * t);
        family("cubic", t -> t * t * t);
        family("quart", t -> t * t * t * t);
        family("quint", t -> t * t * t * t * t);
        family("expo", t -> t == 0 ? 0 : Math.pow(2, 10 * t - 10));
        family("circ", t -> 1 - Math.sqrt(1 - t * t));
        family("back", t -> {
            double c1 = 1.70158, c3 = c1 + 1;
            return c3 * t * t * t - c1 * t * t;
        });
        family("elastic", t -> {
            if (t == 0 || t == 1) {
                return t;
            }
            return -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3));
        });
        family("bounce", t -> 1 - bounceOut(1 - t));
        BY_NAME.put("ease_in", BY_NAME.get("ease_in_cubic"));
        BY_NAME.put("ease_out", BY_NAME.get("ease_out_cubic"));
        BY_NAME.put("ease_in_out", BY_NAME.get("ease_in_out_cubic"));
    }

    private Easing() {
    }

    /** Registra in, out (espejo) e in_out (las dos mitades) a partir de la curva "in". */
    private static void family(String name, DoubleUnaryOperator in) {
        DoubleUnaryOperator out = t -> 1 - in.applyAsDouble(1 - t);
        DoubleUnaryOperator inOut = t -> t < 0.5 ? in.applyAsDouble(2 * t) / 2 : 1 - in.applyAsDouble(2 - 2 * t) / 2;
        BY_NAME.put("ease_in_" + name, in);
        BY_NAME.put("ease_out_" + name, out);
        BY_NAME.put("ease_in_out_" + name, inOut);
    }

    private static double bounceOut(double t) {
        double n1 = 7.5625, d1 = 2.75;
        if (t < 1 / d1) {
            return n1 * t * t;
        } else if (t < 2 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        } else if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        }
        t -= 2.625 / d1;
        return n1 * t * t + 0.984375;
    }

    /** Curva por nombre. Los extremos siempre valen exactamente 0 y 1. */
    public static DoubleUnaryOperator parse(String name) {
        if (name == null || name.isBlank()) {
            return BY_NAME.get("linear");
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.startsWith("cubic_bezier(") && n.endsWith(")")) {
            String[] parts = n.substring(13, n.length() - 1).split(",");
            if (parts.length != 4) {
                throw new IllegalArgumentException("cubic_bezier necesita 4 números: " + name);
            }
            try {
                CubicBezier bezier = new CubicBezier(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
                return bezier::ease;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cubic_bezier con números no válidos: " + name);
            }
        }
        DoubleUnaryOperator curve = BY_NAME.get(n);
        if (curve == null) {
            throw new IllegalArgumentException("Easing desconocido: " + name + " (ejemplos: linear, ease_out_expo, "
                    + "ease_out_elastic, ease_in_out_sine)");
        }
        return t -> t <= 0 ? 0 : t >= 1 ? 1 : curve.applyAsDouble(t);
    }

    public static Map<String, DoubleUnaryOperator> all() {
        return Collections.unmodifiableMap(BY_NAME);
    }
}

