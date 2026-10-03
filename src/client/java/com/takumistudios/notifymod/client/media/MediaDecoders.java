package com.takumistudios.notifymod.client.media;

import com.takumistudios.notifymod.client.media.lottie.LottieDecoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Entrada única de la tubería de medios: detecta el formato por su firma (no por la extensión) y devuelve fotogramas
 * ARGB listos para subir. No toca clases de Minecraft, así que se prueba sin el juego y se puede usar fuera del hilo
 * de render.
 */
public final class MediaDecoders {
    /** Fotogramas por segundo máximos al pre-renderizar Lottie (§23: tope de FPS de animación). */
    public static final int LOTTIE_MAX_FPS = 30;

    private MediaDecoders() {
    }

    public enum Format { PNG, GIF, WEBP, LOTTIE }

    /** Fotogramas decodificados y avisos (p. ej. funciones de Lottie no soportadas). */
    public record Decoded(Format format, FrameSequence frames, List<String> warnings) {
    }

    public static Decoded decode(InputStream in, MediaLimits limits) throws MediaException {
        return decode(readLimited(in, limits.maxFileBytes()), limits);
    }

    public static Decoded decode(byte[] data, MediaLimits limits) throws MediaException {
        if (data.length > limits.maxFileBytes()) {
            throw new MediaException("Archivo demasiado grande: " + data.length + " bytes");
        }
        Format format = detect(data);
        if (format == null) {
            throw new MediaException("Formato no reconocido (se admiten PNG, GIF, WebP y Lottie JSON)");
        }
        return switch (format) {
            case PNG -> new Decoded(format, decodePng(data, limits), List.of());
            case GIF -> new Decoded(format, GifDecoder.decode(data, limits), List.of());
            case WEBP -> new Decoded(format, WebpDecoder.decode(data, limits), List.of());
            case LOTTIE -> {
                LottieDecoder.Result result = LottieDecoder.decode(data, limits, LOTTIE_MAX_FPS);
                yield new Decoded(format, result.frames(), result.unsupported());
            }
        };
    }

    public static Format detect(byte[] data) {
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return Format.PNG;
        }
        if (GifDecoder.matches(data)) {
            return Format.GIF;
        }
        if (WebpDecoder.matches(data)) {
            return Format.WEBP;
        }
        if (LottieDecoder.matches(data)) {
            return Format.LOTTIE;
        }
        return null;
    }

    /**
     * Igual que {@link #decode(byte[], MediaLimits)}, con el {@code .mcmeta} del archivo si lo tiene: un PNG con
     * animación es una spritesheet ({@link SpriteSheet}). En los demás formatos el {@code .mcmeta} se ignora y se avisa.
     */
    public static Decoded decode(byte[] data, String mcmeta, MediaLimits limits) throws MediaException {
        if (mcmeta == null) {
            return decode(data, limits);
        }
        if (data.length > limits.maxFileBytes()) {
            throw new MediaException("Archivo demasiado grande: " + data.length + " bytes");
        }
        if (detect(data) != Format.PNG) {
            Decoded decoded = decode(data, limits);
            List<String> warnings = new ArrayList<>(decoded.warnings());
            warnings.add(".mcmeta ignorado: solo se aplica a PNG");
            return new Decoded(decoded.format(), decoded.frames(), List.copyOf(warnings));
        }
        // La hoja entera puede pasar del lado máximo (una tira vertical); el límite es su memoria
        FrameSequence sheet = decodePng(data, limits, SHEET_MAX_SIDE, SHEET_MAX_SIDE);
        List<String> warnings = new ArrayList<>();
        FrameSequence frames = SpriteSheet.apply(sheet, mcmeta, limits, warnings);
        return new Decoded(Format.PNG, frames, List.copyOf(warnings));
    }

    /** Lado máximo de una spritesheet completa (cada fotograma sigue limitado por {@link MediaLimits}). */
    static final int SHEET_MAX_SIDE = 8192;

    private static FrameSequence decodePng(byte[] data, MediaLimits limits) throws MediaException {
        return decodePng(data, limits, limits.maxWidth(), limits.maxHeight());
    }

    private static FrameSequence decodePng(byte[] data, MediaLimits limits, int maxWidth, int maxHeight)
            throws MediaException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) {
            throw new MediaException("Este Java no tiene lector de PNG");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = new BudgetedImageInputStream(data)) {
            reader.setInput(in, true, true);
            // Se mira la cabecera antes de leer: un PNG pequeño puede declarar un tamaño enorme
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
                throw new MediaException("Imagen demasiado grande o no válida: " + width + "x" + height
                        + " (máximo " + maxWidth + "x" + maxHeight + ")");
            }
            if ((long) width * height * 4L > limits.maxBytes()) {
                throw new MediaException("La imagen ocupa " + (((long) width * height * 4L) >> 20)
                        + " MiB en memoria (máximo " + (limits.maxBytes() >> 20) + " MiB)");
            }
            BufferedImage image = reader.read(0);
            return FrameSequence.still(width, height, image.getRGB(0, 0, width, height, null, 0, width));
        } catch (MediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new MediaException("PNG no válido: " + e.getMessage(), e);
        } finally {
            reader.dispose();
        }
    }

    /** Lee como mucho {@code max} bytes: un archivo enorme se rechaza sin cargarlo entero. */
    static byte[] readLimited(InputStream in, long max) throws MediaException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > max) {
                    throw new MediaException("Archivo demasiado grande (máximo " + max + " bytes)");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (MediaException e) {
            throw e;
        } catch (IOException e) {
            throw new MediaException("No se pudo leer el archivo: " + e.getMessage(), e);
        }
    }
}

