package com.takumistudios.notifymod.client.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.takumistudios.notifymod.client.timeline.Presentation;
import com.takumistudios.notifymod.client.timeline.PresentationParser;
import com.takumistudios.notifymod.client.timeline.TrackSpec;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fase 3: calidad baja, pistas de modelo 3D y precarga. */
class Phase3Test {

    private static FrameSequence animation(int w, int h, int frames, int delay) {
        List<int[]> list = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            int[] px = new int[w * h];
            for (int i = 0; i < px.length; i++) {
                px[i] = 0xFF000000 | (f << 16) | i;
            }
            list.add(px);
            delays.add(delay);
        }
        return new FrameSequence(w, h, list, delays);
    }

    @Test
    void calidadAltaNoTocaNada() {
        FrameSequence in = animation(600, 300, 5, 40);
        assertSame(in, MediaQuality.HIGH.apply(in));
    }

    @Test
    void calidadBajaReduceTamanoYFotogramasSinCambiarLaDuracion() {
        FrameSequence in = animation(600, 300, 5, 40);
        FrameSequence out = MediaQuality.LOW.apply(in);
        assertEquals(256, out.width());
        assertEquals(128, out.height());
        assertEquals(3, out.frameCount()); // 0, 2, 4
        assertEquals(in.durationMs(), out.durationMs());
        assertEquals(80, out.delayMs(0));
        assertEquals(40, out.delayMs(2)); // el último no tiene pareja
        assertEquals(2, (out.frame(1)[0] >> 16) & 0xFF); // el segundo es el fotograma 2 original
    }

    @Test
    void calidadBajaRespetaLasImagenesPequenas() {
        FrameSequence still = FrameSequence.still(32, 16, new int[32 * 16]);
        assertSame(still, MediaQuality.LOW.apply(still));
    }

    @Test
    void laCalidadSeLeeSinFallar() {
        assertEquals(MediaQuality.LOW, MediaQuality.parse("low"));
        assertEquals(MediaQuality.LOW, MediaQuality.parse(" LOW "));
        assertEquals(MediaQuality.HIGH, MediaQuality.parse("ultra"));
    }

    @Test
    void pistasDeModeloYPrecarga() {
        Presentation p = PresentationParser.parse("a:b", """
                {"duration": 3000, "preload": true, "tracks": [
                  {"type": "model", "item": "minecraft:diamond_sword", "time": 0, "duration": 3000},
                  {"type": "model", "entity": "minecraft:zombie", "time": 0, "duration": 3000, "loop": "spin"},
                  {"type": "texture", "file": "a:textures/x.png", "time": 0, "duration": 3000},
                  {"type": "texture", "file": "a:textures/x.png", "time": 0, "duration": 3000}
                ]}""");
        assertTrue(p.preload());
        TrackSpec item = p.tracks().stream().filter(t -> t.type() == TrackSpec.Type.MODEL && !t.modelIsEntity())
                .findFirst().orElseThrow();
        assertEquals("minecraft:diamond_sword", item.model());
        assertTrue(p.tracks().stream().anyMatch(t -> t.modelIsEntity() && "minecraft:zombie".equals(t.model())));
        assertEquals(List.of("a:textures/x.png"), p.textureFiles());
        assertFalse(PresentationParser.parse("a:b", "{\"duration\": 1000, \"tracks\": []}").preload());
    }

    @Test
    void unModeloNecesitaItemOEntityPeroNoAmbos() {
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"duration\": 1000, \"tracks\": [{\"type\": \"model\", \"time\": 0, \"duration\": 1000}]}"));
        assertThrows(IllegalArgumentException.class, () -> PresentationParser.parse("a:b",
                "{\"duration\": 1000, \"tracks\": [{\"type\": \"model\", \"item\": \"minecraft:stone\","
                        + " \"entity\": \"minecraft:pig\", \"time\": 0, \"duration\": 1000}]}"));
    }
}

