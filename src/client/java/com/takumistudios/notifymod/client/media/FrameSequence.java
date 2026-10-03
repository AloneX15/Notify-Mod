package com.takumistudios.notifymod.client.media;

import java.util.Arrays;
import java.util.List;

/**
 * Resultado común de la tubería de medios: todos los formatos (PNG, spritesheet, GIF, WebP, Lottie) acaban aquí como
 * fotogramas ARGB ya compuestos, del mismo tamaño, con su duración. Es inmutable y no depende de Minecraft.
 */
public final class FrameSequence {
    private final int width;
    private final int height;
    private final int[][] frames;
    private final int[] delaysMs;
    private final long[] endTimesMs;
    private final long durationMs;

    public FrameSequence(int width, int height, List<int[]> frames, List<Integer> delaysMs) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Tamaño no válido: " + width + "x" + height);
        }
        if (frames.isEmpty() || frames.size() != delaysMs.size()) {
            throw new IllegalArgumentException("Fotogramas y retardos no coinciden");
        }
        this.width = width;
        this.height = height;
        this.frames = new int[frames.size()][];
        this.delaysMs = new int[frames.size()];
        this.endTimesMs = new long[frames.size()];
        long total = 0;
        for (int i = 0; i < frames.size(); i++) {
            int[] frame = frames.get(i);
            if (frame.length != width * height) {
                throw new IllegalArgumentException("El fotograma " + i + " no mide " + width + "x" + height);
            }
            int delay = delaysMs.get(i);
            if (delay <= 0) {
                throw new IllegalArgumentException("Retardo no válido en el fotograma " + i + ": " + delay);
            }
            this.frames[i] = frame;
            this.delaysMs[i] = delay;
            total += delay;
            this.endTimesMs[i] = total;
        }
        this.durationMs = total;
    }

    /** Imagen fija: un solo fotograma. */
    public static FrameSequence still(int width, int height, int[] argb) {
        return new FrameSequence(width, height, List.of(argb), List.of(1000));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int frameCount() {
        return frames.length;
    }

    public boolean animated() {
        return frames.length > 1;
    }

    public long durationMs() {
        return durationMs;
    }

    public int delayMs(int index) {
        return delaysMs[index];
    }

    /** Píxeles ARGB del fotograma, fila a fila. No se copian: no modificar el array devuelto. */
    public int[] frame(int index) {
        return frames[index];
    }

    public int pixel(int frame, int x, int y) {
        return frames[frame][y * width + x];
    }

    /** Fotograma visible en el instante dado (en bucle). Sin asignaciones: se puede llamar en cada fotograma. */
    public int frameIndexAt(long timeMs) {
        if (frames.length == 1) {
            return 0;
        }
        long t = Math.floorMod(timeMs, durationMs);
        int index = Arrays.binarySearch(endTimesMs, t);
        // Un instante igual al final de un fotograma ya pertenece al siguiente
        return index >= 0 ? index + 1 : -index - 1;
    }

    /** Memoria aproximada de los píxeles decodificados. */
    public long estimatedBytes() {
        return (long) width * height * 4L * frames.length;
    }
}

