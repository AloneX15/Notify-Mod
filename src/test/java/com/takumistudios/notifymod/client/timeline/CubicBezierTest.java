package com.takumistudios.notifymod.client.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CubicBezierTest {
    @Test
    void extremosFijos() {
        CubicBezier curve = new CubicBezier(0.1, 0.7, 0.9, 0.2);
        assertEquals(0, curve.ease(0));
        assertEquals(1, curve.ease(1));
        assertEquals(0, curve.ease(-5));
        assertEquals(1, curve.ease(5));
    }

    @Test
    void linealEsIdentidad() {
        for (double t = 0; t <= 1; t += 0.05) {
            assertEquals(t, CubicBezier.LINEAR.ease(t), 1e-9);
        }
    }

    @Test
    void easeInOutEsSimetricaYMonotona() {
        CubicBezier curve = CubicBezier.EASE_IN_OUT;
        assertEquals(0.5, curve.ease(0.5), 1e-4);
        double previous = 0;
        for (int i = 1; i <= 100; i++) {
            double t = i / 100.0;
            double value = curve.ease(t);
            assertTrue(value >= previous - 1e-9, "no monótona en " + t);
            assertEquals(1 - curve.ease(1 - t), value, 1e-4);
            previous = value;
        }
    }

    @Test
    void valoresConocidosDeCss() {
        // cubic-bezier(0.25, 0.1, 0.25, 1) ("ease" de CSS) en x = 0.5 ≈ 0.8024
        assertEquals(0.8024, new CubicBezier(0.25, 0.1, 0.25, 1).ease(0.5), 1e-3);
    }

    @Test
    void permiteRebotes() {
        // y fuera de [0, 1] (overshoot) es válido; x se recorta para que el tiempo no retroceda
        CubicBezier back = new CubicBezier(0.34, 1.56, 0.64, 1);
        double max = 0;
        for (int i = 0; i <= 100; i++) {
            max = Math.max(max, back.ease(i / 100.0));
        }
        assertTrue(max > 1, "debería pasarse de 1: " + max);
    }
}

