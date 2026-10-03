package com.takumistudios.notifymod.client.media;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

/**
 * GIF animado con el lector del JDK (sin dependencias). Compone cada fotograma sobre el lienzo lógico respetando la
 * posición y el método de eliminación (disposal) de cada uno.
 */
public final class GifDecoder {
    private static final String IMAGE_FORMAT = "javax_imageio_gif_image_1.0";
    private static final String STREAM_FORMAT = "javax_imageio_gif_stream_1.0";

    private GifDecoder() {
    }

    public static boolean matches(byte[] data) {
        return data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
    }

    public static FrameSequence decode(byte[] data, MediaLimits limits) throws MediaException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new MediaException("Este Java no tiene lector de GIF");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = new BudgetedImageInputStream(data)) {
            reader.setInput(in, false, false);
            int count = reader.getNumImages(true);
            int[] screen = logicalScreen(reader.getStreamMetadata());
            int width = screen[0] > 0 ? screen[0] : reader.getWidth(0);
            int height = screen[1] > 0 ? screen[1] : reader.getHeight(0);
            limits.checkFrames(count, width, height);

            Canvas canvas = new Canvas(width, height);
            List<int[]> frames = new ArrayList<>(count);
            List<Integer> delays = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                // Se mira la cabecera antes de leer: un fotograma gigante agotaría la memoria al reservarlo
                limits.checkSize(reader.getWidth(i), reader.getHeight(i));
                BufferedImage image = reader.read(i);
                FrameInfo info = frameInfo(reader.getImageMetadata(i));
                int[] previous = "restoreToPrevious".equals(info.disposal) ? canvas.snapshot() : null;
                canvas.draw(image, info.left, info.top, true);
                frames.add(canvas.snapshot());
                delays.add(limits.normalizeDelay(info.delayCs * 10));
                switch (info.disposal) {
                    case "restoreToBackgroundColor" -> canvas.clear(info.left, info.top, image.getWidth(), image.getHeight());
                    case "restoreToPrevious" -> canvas.restore(previous);
                    default -> { } // none / doNotDispose: el fotograma se queda
                }
            }
            return new FrameSequence(width, height, frames, delays);
        } catch (MediaException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // El lector del JDK lanza excepciones no comprobadas con archivos corruptos
            throw new MediaException("GIF no válido: " + e.getMessage(), e);
        } finally {
            reader.dispose();
        }
    }

    private record FrameInfo(int left, int top, String disposal, int delayCs) {
    }

    private static FrameInfo frameInfo(IIOMetadata metadata) {
        int left = 0, top = 0, delay = 0;
        String disposal = "none";
        Node root = metadata.getAsTree(IMAGE_FORMAT);
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            switch (node.getNodeName()) {
                case "ImageDescriptor" -> {
                    left = intAttribute(node, "imageLeftPosition");
                    top = intAttribute(node, "imageTopPosition");
                }
                case "GraphicControlExtension" -> {
                    delay = intAttribute(node, "delayTime");
                    Node d = node.getAttributes().getNamedItem("disposalMethod");
                    if (d != null) {
                        disposal = d.getNodeValue();
                    }
                }
                default -> { }
            }
        }
        return new FrameInfo(left, top, disposal, delay);
    }

    private static int[] logicalScreen(IIOMetadata metadata) {
        if (metadata == null) {
            return new int[] {0, 0};
        }
        Node root = metadata.getAsTree(STREAM_FORMAT);
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if ("LogicalScreenDescriptor".equals(node.getNodeName())) {
                return new int[] {intAttribute(node, "logicalScreenWidth"), intAttribute(node, "logicalScreenHeight")};
            }
        }
        return new int[] {0, 0};
    }

    private static int intAttribute(Node node, String name) {
        Node attribute = node.getAttributes().getNamedItem(name);
        if (attribute == null) {
            return 0;
        }
        try {
            return Integer.parseInt(attribute.getNodeValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

