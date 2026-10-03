package com.takumistudios.notifymod.client.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.takumistudios.notifymod.client.media.lottie.LottieDecoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Pruebas de viabilidad de la Fase 0 (§24 del plan): GIF, WebP y Lottie se decodifican a fotogramas ARGB correctos.
 * Los fixtures los genera tools/fixtures/gen-fixtures.mjs: 3 fotogramas de 32x32 con un cuadrado de 16x16 que avanza
 * 8 px en diagonal (rojo, verde, azul) sobre fondo transparente, con retardos de 100, 200 y 300 ms.
 */
class MediaDecodersTest {
    private static final int[] COLORS = {0xE62828, 0x28C846, 0x285AE6};
    private static final int[] DELAYS = {100, 200, 300};

    static byte[] fixture(String name) throws IOException {
        try (InputStream in = MediaDecodersTest.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IOException("Falta el fixture " + name);
            }
            return in.readAllBytes();
        }
    }

    /** Comprueba el contenido de los 3 fotogramas con una tolerancia de color (0 = exacto). */
    private static void assertSquares(FrameSequence frames, int tolerance) {
        assertEquals(32, frames.width());
        assertEquals(32, frames.height());
        assertEquals(3, frames.frameCount());
        for (int i = 0; i < 3; i++) {
            assertEquals(DELAYS[i], frames.delayMs(i), "retardo del fotograma " + i);
            int center = i * 8 + 8;
            assertColor(0xFF000000 | COLORS[i], frames.pixel(i, center, center), tolerance, "centro del fotograma " + i);
            // Fuera del cuadrado actual no puede quedar nada de los fotogramas anteriores
            int outside = i == 0 ? 30 : 2;
            assertEquals(0, frames.pixel(i, outside, outside) >>> 24, "transparencia del fotograma " + i);
        }
    }

    private static void assertColor(int expected, int actual, int tolerance, String what) {
        for (int shift = 0; shift <= 24; shift += 8) {
            int e = expected >>> shift & 0xFF, a = actual >>> shift & 0xFF;
            assertTrue(Math.abs(e - a) <= tolerance,
                    () -> what + ": esperado " + Integer.toHexString(expected) + ", obtenido " + Integer.toHexString(actual));
        }
    }

    @Test
    void gifAnimado() throws IOException {
        MediaDecoders.Decoded decoded = MediaDecoders.decode(fixture("sample.gif"), MediaLimits.DEFAULT);
        assertEquals(MediaDecoders.Format.GIF, decoded.format());
        assertSquares(decoded.frames(), 0);
    }

    @Test
    void webpAnimadoSinPerdida() throws IOException {
        MediaDecoders.Decoded decoded = MediaDecoders.decode(fixture("sample_lossless.webp"), MediaLimits.DEFAULT);
        assertEquals(MediaDecoders.Format.WEBP, decoded.format());
        assertSquares(decoded.frames(), 0);
    }

    @Test
    void webpAnimadoConPerdidaYAlfa() throws IOException {
        assertSquares(MediaDecoders.decode(fixture("sample_lossy_alpha.webp"), MediaLimits.DEFAULT).frames(), 16);
    }

    @Test
    void webpFijo() throws IOException {
        FrameSequence frames = MediaDecoders.decode(fixture("sample_still.webp"), MediaLimits.DEFAULT).frames();
        assertEquals(1, frames.frameCount());
        assertColor(0xFF000000 | COLORS[1], frames.pixel(0, 16, 16), 0, "centro");
        assertEquals(0, frames.pixel(0, 2, 2) >>> 24);
    }

    @Test
    void lottieSubconjunto() throws IOException {
        MediaDecoders.Decoded decoded = MediaDecoders.decode(fixture("sample_lottie.json"), MediaLimits.DEFAULT);
        assertEquals(MediaDecoders.Format.LOTTIE, decoded.format());
        FrameSequence frames = decoded.frames();
        assertEquals(64, frames.width());
        assertEquals(30, frames.frameCount());
        assertEquals(33, frames.delayMs(0));
        // La bola roja va de x=16 a x=48
        assertColor(0xFFFF0000, frames.pixel(0, 16, 32), 0, "bola al principio");
        assertEquals(0, frames.pixel(0, 48, 32) >>> 24);
        assertColor(0xFFFF0000, frames.pixel(29, 48, 32), 0, "bola al final");
        assertEquals(0, frames.pixel(29, 16, 32) >>> 24);
        // Con easing, a mitad de camino no va a velocidad constante pero sí entre los extremos
        int middle = firstRedX(frames, 15);
        assertTrue(middle > 16 && middle < 48, "posición intermedia: " + middle);
        // Barra azul al 50 % de opacidad
        int bar = frames.pixel(0, 32, 56);
        assertColor(0xFF0000FF, bar | 0xFF000000, 0, "color de la barra");
        assertTrue(Math.abs((bar >>> 24) - 128) <= 2, "alfa de la barra: " + (bar >>> 24));
        assertTrue(decoded.warnings().contains("degradados"), decoded.warnings().toString());
        assertTrue(decoded.warnings().contains("capas de texto"), decoded.warnings().toString());
    }

    private static int firstRedX(FrameSequence frames, int frame) {
        for (int x = 0; x < frames.width(); x++) {
            if (frames.pixel(frame, x, 32) == 0xFFFF0000) {
                return x + 8; // borde izquierdo + radio
            }
        }
        return -1;
    }

    @Test
    void lottieMasRapidoQueElTopeSeLimitaA30Fps() throws IOException {
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(
                new String(fixture("sample_lottie.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        json.addProperty("fr", 60);
        json.addProperty("op", 60);
        LottieDecoder.Result result = LottieDecoder.decode(
                json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), MediaLimits.DEFAULT, 30);
        assertEquals(30, result.frames().frameCount());
        assertEquals(33, result.frames().delayMs(0));
    }

    @Test
    void detectaPorFirmaNoPorExtension() throws IOException {
        assertEquals(MediaDecoders.Format.GIF, MediaDecoders.detect(fixture("sample.gif")));
        assertEquals(MediaDecoders.Format.WEBP, MediaDecoders.detect(fixture("sample_still.webp")));
        assertEquals(MediaDecoders.Format.LOTTIE, MediaDecoders.detect(fixture("sample_lottie.json")));
        assertEquals(null, MediaDecoders.detect(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12}));
    }

    /**
     * Un archivo dañado nunca puede tumbar el juego: o se decodifica lo que se pueda o sale {@link MediaException},
     * nunca otra excepción. Se prueban todos los cortes posibles y bytes cambiados. Regresión de la Fase 0: el lector
     * VP8L de TwelveMonkeys se quedaba leyendo sin fin con un WebP dañado (ver {@link BudgetedImageInputStream}).
     */
    @Test
    @Timeout(120)
    void archivosCorruptosNoRompenNada() throws IOException {
        for (String name : List.of("sample.gif", "sample_lossless.webp", "sample_lossy_alpha.webp",
                "sample_still.webp", "sample_lottie.json")) {
            byte[] data = fixture(name);
            for (int cut = 0; cut < data.length; cut++) {
                decodeSafely(Arrays.copyOf(data, cut), name + " cortado en " + cut);
            }
            java.util.Random random = new java.util.Random(name.hashCode());
            for (int i = 0; i < 300; i++) {
                byte[] damaged = data.clone();
                for (int j = 0; j < 3; j++) {
                    damaged[random.nextInt(damaged.length)] = (byte) random.nextInt(256);
                }
                decodeSafely(damaged, name + " dañado #" + i);
            }
        }
        // Estos sí o sí tienen que fallar de forma controlada
        assertThrows(MediaException.class, () -> MediaDecoders.decode(new byte[] {'G', 'I', 'F', '8', '9', 'a', 0, 0},
                MediaLimits.DEFAULT));
        byte[] webp = fixture("sample_lossless.webp");
        assertThrows(MediaException.class, () -> MediaDecoders.decode(Arrays.copyOf(webp, webp.length - 5),
                MediaLimits.DEFAULT));
        byte[] lottie = fixture("sample_lottie.json");
        assertThrows(MediaException.class, () -> MediaDecoders.decode(Arrays.copyOf(lottie, lottie.length / 2),
                MediaLimits.DEFAULT));
    }

    private static void decodeSafely(byte[] data, String what) {
        try {
            MediaDecoders.decode(data, MediaLimits.DEFAULT);
        } catch (MediaException expected) {
            // Fallo controlado: correcto
        } catch (Throwable t) {
            throw new AssertionError(what + ": excepción no controlada " + t, t);
        }
    }

    @Test
    void respetaLosLimites() throws IOException {
        MediaLimits twoFrames = new MediaLimits(1 << 20, 1024, 1024, 2, 64L << 20, 20);
        assertThrows(MediaException.class, () -> MediaDecoders.decode(fixture("sample.gif"), twoFrames));
        assertThrows(MediaException.class, () -> MediaDecoders.decode(fixture("sample_lossless.webp"), twoFrames));
        MediaLimits tiny = new MediaLimits(1 << 20, 16, 16, 512, 64L << 20, 20);
        assertThrows(MediaException.class, () -> MediaDecoders.decode(fixture("sample.gif"), tiny));
        MediaLimits smallFile = new MediaLimits(64, 1024, 1024, 512, 64L << 20, 20);
        assertThrows(MediaException.class,
                () -> MediaDecoders.decode(new ByteArrayInputStream(fixture("sample.gif")), smallFile));
    }
}



