package com.takumistudios.notifymod.client.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Calidad de los medios (§9.3, modo de bajo rendimiento): en calidad baja las imágenes se reducen a un lado máximo y
 * las animaciones se quedan con uno de cada N fotogramas (sumando sus retardos, así la duración no cambia).
 */
public enum MediaQuality {
    HIGH(Integer.MAX_VALUE, 1),
    LOW(256, 2);

    private final int maxSide;
    private final int frameStep;

    MediaQuality(int maxSide, int frameStep) {
        this.maxSide = maxSide;
        this.frameStep = frameStep;
    }

    public static MediaQuality parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return HIGH;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Aplica esta calidad. Con HIGH devuelve la misma secuencia. */
    public FrameSequence apply(FrameSequence in) {
        return reduce(in, maxSide, frameStep);
    }

    static FrameSequence reduce(FrameSequence in, int maxSide, int frameStep) {
        int w = in.width();
        int h = in.height();
        int longest = Math.max(w, h);
        boolean resize = longest > maxSide;
        boolean skip = frameStep > 1 && in.frameCount() > 1;
        if (!resize && !skip) {
            return in;
        }
        int nw = resize ? Math.max(1, (int) ((long) w * maxSide / longest)) : w;
        int nh = resize ? Math.max(1, (int) ((long) h * maxSide / longest)) : h;
        List<int[]> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        int step = skip ? frameStep : 1;
        for (int i = 0; i < in.frameCount(); i += step) {
            int delay = 0;
            for (int j = i; j < Math.min(in.frameCount(), i + step); j++) {
                delay += in.delayMs(j);
            }
            frames.add(resize ? scale(in.frame(i), w, h, nw, nh) : in.frame(i));
            delays.add(delay);
        }
        return new FrameSequence(nw, nh, frames, delays);
    }

    /** Vecino más cercano: barato y conserva los bordes nítidos del pixel art. */
    private static int[] scale(int[] src, int w, int h, int nw, int nh) {
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            int sy = (int) ((long) y * h / nh);
            for (int x = 0; x < nw; x++) {
                out[y * nw + x] = src[sy * w + (int) ((long) x * w / nw)];
            }
        }
        return out;
    }
}

