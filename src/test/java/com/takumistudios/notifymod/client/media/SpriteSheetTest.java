package com.takumistudios.notifymod.client.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Fase 3 (§9): spritesheets con .mcmeta, como las texturas animadas de Minecraft. */
class SpriteSheetTest {
    /** Tira vertical de {@code count} fotogramas de 4×4; el píxel (0,0) de cada uno guarda su número. */
    private static FrameSequence strip(int count) {
        int[] px = new int[4 * 4 * count];
        for (int f = 0; f < count; f++) {
            px[f * 16] = 0xFF000000 | f;
        }
        return FrameSequence.still(4, 4 * count, px);
    }

    private static int number(FrameSequence s, int frame) {
        return s.pixel(frame, 0, 0) & 0xFF;
    }

    @Test
    void tiraVerticalConFrametime() throws MediaException {
        FrameSequence out = SpriteSheet.apply(strip(3), "{\"animation\": {\"frametime\": 2}}", MediaLimits.DEFAULT,
                new ArrayList<>());
        assertEquals(4, out.width());
        assertEquals(4, out.height());
        assertEquals(3, out.frameCount());
        assertEquals(100, out.delayMs(0)); // 2 ticks
        assertEquals(2, number(out, 2));
    }

    @Test
    void ordenYTiemposPropios() throws MediaException {
        FrameSequence out = SpriteSheet.apply(strip(3),
                "{\"animation\": {\"frames\": [2, {\"index\": 0, \"time\": 5}, 2]}}", MediaLimits.DEFAULT,
                new ArrayList<>());
        assertEquals(3, out.frameCount());
        assertEquals(2, number(out, 0));
        assertEquals(0, number(out, 1));
        assertEquals(250, out.delayMs(1));
        assertEquals(50, out.delayMs(2));
        assertSame(out.frame(0), out.frame(2), "los fotogramas repetidos no duplican memoria");
    }

    @Test
    void cuadriculaConTamanoExplicito() throws MediaException {
        // 8×4 con fotogramas de 4×4: dos columnas, una fila
        int[] px = new int[8 * 4];
        px[4] = 0xFF000007;
        FrameSequence out = SpriteSheet.apply(FrameSequence.still(8, 4, px),
                "{\"animation\": {\"width\": 4, \"height\": 4}}", MediaLimits.DEFAULT, new ArrayList<>());
        assertEquals(2, out.frameCount());
        assertEquals(7, number(out, 1));
    }

    @Test
    void sinAnimacionNoCambiaNada() throws MediaException {
        FrameSequence in = strip(2);
        assertSame(in, SpriteSheet.apply(in, "{\"texture\": {\"blur\": true}}", MediaLimits.DEFAULT,
                new ArrayList<>()));
    }

    @Test
    void interpolateSeAvisa() throws MediaException {
        List<String> warnings = new ArrayList<>();
        SpriteSheet.apply(strip(2), "{\"animation\": {\"interpolate\": true}}", MediaLimits.DEFAULT, warnings);
        assertEquals(1, warnings.size());
    }

    @Test
    void mcmetaInvalidoNoRompeNada() {
        String[] bad = {
            "no es json",
            "[1, 2]",
            "{\"animation\": 3}",
            "{\"animation\": {\"frametime\": 0}}",
            "{\"animation\": {\"frames\": [7]}}",
            "{\"animation\": {\"frames\": [-1]}}",
            "{\"animation\": {\"frames\": []}}",
            "{\"animation\": {\"frames\": [{\"index\": 0, \"time\": 0}]}}",
            "{\"animation\": {\"width\": 3}}",
            "{\"animation\": {\"width\": 0}}",
            "{\"animation\": {\"frames\": [\"x\"]}}",
        };
        for (String mcmeta : bad) {
            assertThrows(MediaException.class,
                    () -> SpriteSheet.apply(strip(3), mcmeta, MediaLimits.DEFAULT, new ArrayList<>()), mcmeta);
        }
    }

    @Test
    void respetaLosLimites() {
        MediaLimits tight = new MediaLimits(1 << 20, 64, 64, 2, 1 << 20, 20);
        assertThrows(MediaException.class,
                () -> SpriteSheet.apply(strip(3), "{\"animation\": {}}", tight, new ArrayList<>()));
        assertThrows(MediaException.class, () -> SpriteSheet.apply(strip(1),
                "{\"animation\": {\"frames\": [0, 0, 0]}}", tight, new ArrayList<>()));
    }

    @Test
    void pngConMcmetaPorLaTuberia() throws Exception {
        // Tira de 16×2048 (más alta que el lado máximo de una imagen suelta): válida como spritesheet
        BufferedImage image = new BufferedImage(16, 2048, BufferedImage.TYPE_INT_ARGB);
        byte[] png = png(image);
        assertThrows(MediaException.class, () -> MediaDecoders.decode(png, MediaLimits.DEFAULT));
        MediaDecoders.Decoded decoded = MediaDecoders.decode(png, "{\"animation\": {}}", MediaLimits.DEFAULT);
        assertEquals(MediaDecoders.Format.PNG, decoded.format());
        assertEquals(128, decoded.frames().frameCount());
        assertEquals(16, decoded.frames().height());
    }

    @Test
    void mcmetaEnOtroFormatoSeIgnoraConAviso() throws Exception {
        byte[] gif = SpriteSheetTest.class.getResourceAsStream("/fixtures/sample.gif").readAllBytes();
        MediaDecoders.Decoded decoded = MediaDecoders.decode(gif, "{\"animation\": {}}", MediaLimits.DEFAULT);
        assertEquals(MediaDecoders.Format.GIF, decoded.format());
        assertTrue(decoded.warnings().stream().anyMatch(w -> w.contains(".mcmeta")));
    }

    @Test
    void pngConCabeceraGiganteSeRechazaSinReservarMemoria() throws IOException {
        // Un PNG de 1×1 al que se le cambia el tamaño de la cabecera IHDR a 30000×30000
        byte[] png = png(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        writeInt(png, 16, 30000);
        writeInt(png, 20, 30000);
        MediaException e = assertThrows(MediaException.class, () -> MediaDecoders.decode(png, MediaLimits.DEFAULT));
        assertTrue(e.getMessage().contains("30000"), e.getMessage());
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }
}
