package com.takumistudios.notifymod.client.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;

/**
 * Spritesheets con {@code .png.mcmeta} (§9): el mismo formato que las texturas animadas de Minecraft. La imagen se
 * corta en fotogramas de {@code width}×{@code height} (por defecto, cuadrados del lado menor), de izquierda a derecha y
 * de arriba abajo, y se reproducen en el orden de {@code frames} (por defecto, todos) con {@code frametime} ticks cada
 * uno. {@code interpolate} no se admite y se avisa.
 */
public final class SpriteSheet {
    /** Un tick de Minecraft. */
    static final int TICK_MS = 50;
    /** Tamaño máximo del .mcmeta: un pack no puede colgar el cliente con un JSON gigante. */
    static final int MAX_MCMETA_CHARS = 64 * 1024;

    private SpriteSheet() {
    }

    /**
     * Corta {@code sheet} (un solo fotograma, la imagen completa) según el {@code .mcmeta}.
     *
     * @param warnings recibe los avisos (campos no soportados)
     */
    public static FrameSequence apply(FrameSequence sheet, String mcmeta, MediaLimits limits, List<String> warnings)
            throws MediaException {
        if (mcmeta.length() > MAX_MCMETA_CHARS) {
            throw new MediaException(".mcmeta demasiado grande");
        }
        try {
            return cut(sheet, mcmeta, limits, warnings);
        } catch (MediaException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MediaException(".mcmeta no válido: " + e.getMessage(), e);
        }
    }

    private static FrameSequence cut(FrameSequence sheet, String mcmeta, MediaLimits limits, List<String> warnings)
            throws MediaException {
        JsonObject root = JsonParser.parseString(mcmeta).getAsJsonObject();
        if (!root.has("animation")) {
            return sheet; // un .mcmeta sin animación (p. ej. solo "texture") no cambia nada
        }
        JsonObject animation = root.getAsJsonObject("animation");
        if (animation.has("interpolate") && animation.get("interpolate").getAsBoolean()) {
            warnings.add("interpolate no se admite; los fotogramas cambian sin fundido");
        }
        int w = sheet.width();
        int h = sheet.height();
        Integer width = positive(animation, "width");
        Integer height = positive(animation, "height");
        int side = Math.min(w, h);
        int fw = width != null ? width : height != null ? w : side;
        int fh = height != null ? height : width != null ? h : side;
        if (w % fw != 0 || h % fh != 0) {
            throw new MediaException("La imagen " + w + "x" + h + " no se divide en fotogramas de " + fw + "x" + fh);
        }
        int columns = w / fw;
        int available = columns * (h / fh);
        int frameTime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
        if (frameTime < 1) {
            throw new MediaException("frametime debe ser al menos 1");
        }

        List<Integer> order = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        if (animation.has("frames")) {
            JsonArray array = animation.getAsJsonArray("frames");
            if (array.size() > limits.maxFrames()) {
                throw new MediaException("Demasiados fotogramas: " + array.size() + " (máximo " + limits.maxFrames() + ")");
            }
            for (JsonElement e : array) {
                int index;
                int time = frameTime;
                if (e.isJsonObject()) {
                    JsonObject f = e.getAsJsonObject();
                    index = f.get("index").getAsInt();
                    if (f.has("time")) {
                        time = f.get("time").getAsInt();
                    }
                } else {
                    index = e.getAsInt();
                }
                if (index < 0 || index >= available) {
                    throw new MediaException("Fotograma " + index + " fuera de la imagen (hay " + available + ")");
                }
                if (time < 1) {
                    throw new MediaException("time debe ser al menos 1");
                }
                order.add(index);
                times.add(time);
            }
        } else {
            for (int i = 0; i < available; i++) {
                order.add(i);
                times.add(frameTime);
            }
        }
        if (order.isEmpty()) {
            throw new MediaException("La animación no tiene fotogramas");
        }
        limits.checkSize(fw, fh);
        // Los fotogramas repetidos comparten el mismo array: la memoria es la de los distintos
        limits.checkFrames((int) order.stream().distinct().count(), fw, fh);
        if (order.size() > limits.maxFrames()) {
            throw new MediaException("Demasiados fotogramas: " + order.size() + " (máximo " + limits.maxFrames() + ")");
        }

        int[] source = sheet.frame(0);
        int[][] cutFrames = new int[available][];
        List<int[]> frames = new ArrayList<>(order.size());
        List<Integer> delays = new ArrayList<>(order.size());
        for (int i = 0; i < order.size(); i++) {
            int index = order.get(i);
            if (cutFrames[index] == null) {
                int[] frame = new int[fw * fh];
                int x0 = index % columns * fw;
                int y0 = index / columns * fh;
                for (int y = 0; y < fh; y++) {
                    System.arraycopy(source, (y0 + y) * w + x0, frame, y * fw, fw);
                }
                cutFrames[index] = frame;
            }
            frames.add(cutFrames[index]);
            delays.add((int) Math.min(Integer.MAX_VALUE, (long) times.get(i) * TICK_MS));
        }
        return new FrameSequence(fw, fh, frames, delays);
    }

    private static Integer positive(JsonObject o, String field) throws MediaException {
        if (!o.has(field)) {
            return null;
        }
        int v = o.get(field).getAsInt();
        if (v < 1) {
            throw new MediaException(field + " debe ser al menos 1");
        }
        return v;
    }
}
