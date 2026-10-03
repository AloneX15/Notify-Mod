package com.takumistudios.notifymod.client.media;

import java.awt.image.BufferedImage;

/** Lienzo ARGB (sin premultiplicar) para componer animaciones fotograma a fotograma. Solo enteros: sin Java2D. */
final class Canvas {
    final int width;
    final int height;
    private int[] pixels;

    Canvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
    }

    int[] snapshot() {
        return pixels.clone();
    }

    void restore(int[] snapshot) {
        pixels = snapshot.clone();
    }

    /** Deja transparente el rectángulo (recortado al lienzo). */
    void clear(int x, int y, int w, int h) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w), y1 = Math.min(height, y + h);
        for (int py = y0; py < y1; py++) {
            java.util.Arrays.fill(pixels, py * width + x0, py * width + x1, 0);
        }
    }

    /**
     * Dibuja la imagen en (x, y).
     *
     * @param blend {@code true}: mezcla alfa sobre lo que hay; {@code false}: sustituye los píxeles del rectángulo
     */
    void draw(BufferedImage image, int x, int y, boolean blend) {
        int w = image.getWidth(), h = image.getHeight();
        int[] src = image.getRGB(0, 0, w, h, null, 0, w);
        for (int sy = 0; sy < h; sy++) {
            int py = y + sy;
            if (py < 0 || py >= height) {
                continue;
            }
            for (int sx = 0; sx < w; sx++) {
                int px = x + sx;
                if (px < 0 || px >= width) {
                    continue;
                }
                int i = py * width + px;
                int s = src[sy * w + sx];
                pixels[i] = blend ? over(s, pixels[i]) : s;
            }
        }
    }

    /** Composición "source over" en ARGB sin premultiplicar. */
    static int over(int src, int dst) {
        int sa = src >>> 24;
        if (sa == 255) {
            return src;
        }
        if (sa == 0) {
            return dst;
        }
        int da = dst >>> 24;
        if (da == 0) {
            return src;
        }
        int inv = 255 - sa;
        int outA = sa + (da * inv + 127) / 255;
        int r = channel(src >> 16, dst >> 16, sa, da, inv, outA);
        int g = channel(src >> 8, dst >> 8, sa, da, inv, outA);
        int b = channel(src, dst, sa, da, inv, outA);
        return outA << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int s, int d, int sa, int da, int inv, int outA) {
        s &= 0xFF;
        d &= 0xFF;
        int value = (s * sa * 255 + d * da * inv + outA * 255 / 2) / (outA * 255);
        return Math.min(255, value);
    }
}

