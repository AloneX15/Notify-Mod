package com.takumistudios.notifymod.client.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class FrameSequenceTest {
    private static FrameSequence threeFrames() {
        return new FrameSequence(1, 1, List.of(new int[1], new int[1], new int[1]), List.of(100, 200, 300));
    }

    @Test
    void eligeElFotogramaPorTiempoEnBucle() {
        FrameSequence frames = threeFrames();
        assertEquals(600, frames.durationMs());
        assertEquals(0, frames.frameIndexAt(0));
        assertEquals(0, frames.frameIndexAt(99));
        assertEquals(1, frames.frameIndexAt(100));
        assertEquals(1, frames.frameIndexAt(299));
        assertEquals(2, frames.frameIndexAt(300));
        assertEquals(2, frames.frameIndexAt(599));
        assertEquals(0, frames.frameIndexAt(600));
        assertEquals(1, frames.frameIndexAt(6_150));
        assertEquals(2, frames.frameIndexAt(-1)); // tiempos negativos también caen dentro del bucle
    }

    @Test
    void imagenFijaSiempreDevuelveElPrimero() {
        FrameSequence still = FrameSequence.still(2, 2, new int[4]);
        assertEquals(0, still.frameIndexAt(123_456));
        assertEquals(false, still.animated());
    }

    @Test
    void rechazaDatosIncoherentes() {
        assertThrows(IllegalArgumentException.class, () -> new FrameSequence(2, 2, List.of(new int[3]), List.of(100)));
        assertThrows(IllegalArgumentException.class, () -> new FrameSequence(1, 1, List.of(new int[1]), List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new FrameSequence(1, 1, List.of(), List.of()));
    }

    @Test
    void retardosMuyCortosSeTratanComoLosNavegadores() {
        assertEquals(100, MediaLimits.DEFAULT.normalizeDelay(0));
        assertEquals(100, MediaLimits.DEFAULT.normalizeDelay(10));
        assertEquals(20, MediaLimits.DEFAULT.normalizeDelay(20));
    }

    @Test
    void composicionAlfa() {
        assertEquals(0xFF00FF00, Canvas.over(0xFF00FF00, 0xFFFF0000));
        assertEquals(0xFFFF0000, Canvas.over(0x0000FF00, 0xFFFF0000));
        assertEquals(0x80123456, Canvas.over(0x80123456, 0x00000000));
        int half = Canvas.over(0x800000FF, 0xFFFF0000);
        assertEquals(0xFF, half >>> 24);
        assertEquals(0x7F, half >>> 16 & 0xFF, 1);
        assertEquals(0x80, half & 0xFF, 1);
    }
}

