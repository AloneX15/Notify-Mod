package com.takumistudios.notifymod.client.media;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * WebP fijo y animado. El contenedor RIFF (VP8X, ANIM, ANMF) se lee aquí; cada fotograma (VP8 con o sin ALPH, o VP8L)
 * se decodifica con TwelveMonkeys como si fuera una imagen fija. Así la composición (posición, mezcla y eliminación)
 * sigue la especificación sin depender de cómo trate la biblioteca las animaciones.
 *
 * <p>El lector se crea directamente desde su SPI: el registro de ImageIO no ve las clases de los mods.
 */
public final class WebpDecoder {
    private static final int FLAG_ANIMATION = 0x02;
    private static final int FLAG_ALPHA = 0x10;

    private WebpDecoder() {
    }

    public static boolean matches(byte[] data) {
        return data.length >= 12 && fourCc(data, 0).equals("RIFF") && fourCc(data, 8).equals("WEBP");
    }

    public static FrameSequence decode(byte[] data, MediaLimits limits) throws MediaException {
        if (!matches(data)) {
            throw new MediaException("No es un archivo WebP");
        }
        try {
            List<Chunk> chunks = chunks(data, 12, Math.min(data.length, 8 + (int) Math.min(u32(data, 4), Integer.MAX_VALUE - 8)));
            Chunk vp8x = find(chunks, "VP8X");
            if (vp8x == null || (data[vp8x.offset] & FLAG_ANIMATION) == 0) {
                BufferedImage image = decodeStill(data, limits);
                limits.checkFrames(1, image.getWidth(), image.getHeight());
                return FrameSequence.still(image.getWidth(), image.getHeight(),
                        image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
            }
            return decodeAnimated(data, vp8x, chunks, limits);
        } catch (MediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new MediaException("WebP no válido: " + e.getMessage(), e);
        }
    }

    private static FrameSequence decodeAnimated(byte[] data, Chunk vp8x, List<Chunk> chunks, MediaLimits limits)
            throws IOException {
        if (vp8x.size < 10) {
            throw new MediaException("Cabecera VP8X incompleta");
        }
        int width = 1 + u24(data, vp8x.offset + 4);
        int height = 1 + u24(data, vp8x.offset + 7);
        List<Chunk> frameChunks = chunks.stream().filter(c -> c.id.equals("ANMF")).toList();
        limits.checkFrames(frameChunks.size(), width, height);

        Canvas canvas = new Canvas(width, height);
        List<int[]> frames = new ArrayList<>(frameChunks.size());
        List<Integer> delays = new ArrayList<>(frameChunks.size());
        int[] pendingClear = null; // rectángulo del fotograma anterior que pide eliminación
        for (Chunk anmf : frameChunks) {
            if (anmf.size < 16) {
                throw new MediaException("Fotograma ANMF incompleto");
            }
            int x = 2 * u24(data, anmf.offset);
            int y = 2 * u24(data, anmf.offset + 3);
            int w = 1 + u24(data, anmf.offset + 6);
            int h = 1 + u24(data, anmf.offset + 9);
            int duration = u24(data, anmf.offset + 12);
            int flags = data[anmf.offset + 15] & 0xFF;
            boolean blend = (flags & 0x02) == 0;
            boolean dispose = (flags & 0x01) != 0;
            if (x + w > width || y + h > height) {
                throw new MediaException("Un fotograma se sale del lienzo");
            }

            if (pendingClear != null) {
                canvas.clear(pendingClear[0], pendingClear[1], pendingClear[2], pendingClear[3]);
            }
            BufferedImage image = decodeStill(standalone(data, anmf.offset + 16, anmf.offset + anmf.size, w, h), limits);
            canvas.draw(image, x, y, blend);
            frames.add(canvas.snapshot());
            delays.add(limits.normalizeDelay(duration));
            pendingClear = dispose ? new int[] {x, y, w, h} : null;
        }
        return new FrameSequence(width, height, frames, delays);
    }

    /** Envuelve los subchunks de un ANMF (ALPH + VP8, o VP8L) en un WebP fijo que el lector entiende. */
    private static byte[] standalone(byte[] data, int start, int end, int w, int h) throws MediaException {
        List<Chunk> sub = chunks(data, start, end);
        boolean hasAlpha = find(sub, "ALPH") != null || find(sub, "VP8L") != null;
        ByteArrayOutputStream body = new ByteArrayOutputStream(end - start + 32);
        body.writeBytes(new byte[] {'W', 'E', 'B', 'P'});
        if (find(sub, "VP8L") == null) {
            byte[] header = new byte[10];
            header[0] = (byte) (hasAlpha ? FLAG_ALPHA : 0);
            putU24(header, 4, w - 1);
            putU24(header, 7, h - 1);
            writeChunk(body, "VP8X", header, 0, header.length);
        }
        for (Chunk c : sub) {
            if (c.id.equals("ALPH") || c.id.equals("VP8 ") || c.id.equals("VP8L")) {
                writeChunk(body, c.id, data, c.offset, c.size);
            }
        }
        byte[] payload = body.toByteArray();
        ByteArrayOutputStream riff = new ByteArrayOutputStream(payload.length + 8);
        riff.writeBytes(new byte[] {'R', 'I', 'F', 'F'});
        riff.writeBytes(le32(payload.length));
        riff.writeBytes(payload);
        return riff.toByteArray();
    }

    private static BufferedImage decodeStill(byte[] webp, MediaLimits limits) throws IOException {
        ImageReader reader = new WebPImageReaderSpi().createReaderInstance(null);
        try (ImageInputStream in = new BudgetedImageInputStream(webp)) {
            reader.setInput(in, true, true);
            // Se mira la cabecera antes de leer: una imagen gigante agotaría la memoria al reservarla
            limits.checkSize(reader.getWidth(0), reader.getHeight(0));
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private record Chunk(String id, int offset, int size) {
    }

    private static List<Chunk> chunks(byte[] data, int start, int end) throws MediaException {
        List<Chunk> list = new ArrayList<>();
        int pos = start;
        while (pos + 8 <= end) {
            String id = fourCc(data, pos);
            long size = u32(data, pos + 4);
            int payload = pos + 8;
            if (size > end - payload) {
                throw new MediaException("Chunk " + id.trim() + " truncado");
            }
            list.add(new Chunk(id, payload, (int) size));
            pos = payload + (int) size + (int) (size & 1); // los chunks van alineados a 2 bytes
        }
        return list;
    }

    private static Chunk find(List<Chunk> chunks, String id) {
        for (Chunk c : chunks) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    private static void writeChunk(ByteArrayOutputStream out, String id, byte[] src, int offset, int size) {
        out.writeBytes(id.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.writeBytes(le32(size));
        out.write(src, offset, size);
        if ((size & 1) != 0) {
            out.write(0);
        }
    }

    private static String fourCc(byte[] d, int o) {
        return new String(d, o, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static long u32(byte[] d, int o) {
        return (d[o] & 0xFFL) | (d[o + 1] & 0xFFL) << 8 | (d[o + 2] & 0xFFL) << 16 | (d[o + 3] & 0xFFL) << 24;
    }

    private static int u24(byte[] d, int o) {
        return (d[o] & 0xFF) | (d[o + 1] & 0xFF) << 8 | (d[o + 2] & 0xFF) << 16;
    }

    private static void putU24(byte[] d, int o, int v) {
        d[o] = (byte) v;
        d[o + 1] = (byte) (v >> 8);
        d[o + 2] = (byte) (v >> 16);
    }

    private static byte[] le32(int v) {
        return new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }
}

