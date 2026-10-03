package com.takumistudios.notifymod.client.media.lottie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.takumistudios.notifymod.client.timeline.CubicBezier;
import java.util.ArrayList;
import java.util.List;

/**
 * Propiedad animable de Lottie ({@code {"a":0,"k":valor}} o {@code {"a":1,"k":[keyframes]}}) evaluada como vector de
 * números. Las rutas ({@code sh}) usan el mismo mecanismo con el vector aplanado de vértices y tangentes.
 */
final class LottieProperty {
    private final double[] constant;
    private final Keyframe[] keyframes;

    private record Keyframe(double time, double[] start, double[] end, boolean hold, CubicBezier easing) {
    }

    private LottieProperty(double[] constant, Keyframe[] keyframes) {
        this.constant = constant;
        this.keyframes = keyframes;
    }

    static LottieProperty constant(double... value) {
        return new LottieProperty(value, null);
    }

    /** Lee una propiedad numérica. {@code fallback} si falta. */
    static LottieProperty parse(JsonElement json, double[] fallback, LottieReport report, String where) {
        return parse(json, fallback, report, where, LottieProperty::numbers);
    }

    /** Lee una ruta: el vector es [vx, vy, ix, iy, ox, oy] por vértice. */
    static LottieProperty parsePath(JsonElement json, LottieReport report, String where) {
        return parse(json, new double[0], report, where, LottieProperty::pathNumbers);
    }

    /** {@code true} si la ruta está cerrada (se lee del primer valor). */
    static boolean pathClosed(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return false;
        }
        JsonElement k = json.getAsJsonObject().get("k");
        JsonObject shape = null;
        if (k != null && k.isJsonObject()) {
            shape = k.getAsJsonObject();
        } else if (k != null && k.isJsonArray() && !k.getAsJsonArray().isEmpty()) {
            JsonElement s = k.getAsJsonArray().get(0).getAsJsonObject().get("s");
            if (s != null && s.isJsonArray() && !s.getAsJsonArray().isEmpty()) {
                shape = s.getAsJsonArray().get(0).getAsJsonObject();
            }
        }
        return shape != null && shape.has("c") && shape.get("c").getAsBoolean();
    }

    private interface ValueReader {
        double[] read(JsonElement value);
    }

    private static LottieProperty parse(JsonElement json, double[] fallback, LottieReport report, String where,
                                        ValueReader reader) {
        if (json == null || json.isJsonNull()) {
            return constant(fallback);
        }
        if (json.isJsonPrimitive() || json.isJsonArray() && !isKeyframeList(json)) {
            return constant(reader.read(json));
        }
        if (!json.isJsonObject()) {
            return constant(fallback);
        }
        JsonObject object = json.getAsJsonObject();
        if (object.has("x") && object.get("x").isJsonPrimitive() && object.get("x").getAsJsonPrimitive().isString()) {
            report.unsupported("expresiones (" + where + ")");
        }
        JsonElement k = object.get("k");
        if (k == null) {
            return constant(fallback);
        }
        if (!isKeyframeList(k)) {
            return constant(reader.read(k));
        }
        JsonArray list = k.getAsJsonArray();
        List<Keyframe> keyframes = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            JsonObject kf = list.get(i).getAsJsonObject();
            double time = kf.has("t") ? kf.get("t").getAsDouble() : 0;
            double[] start = kf.has("s") ? reader.read(kf.get("s")) : null;
            double[] end = kf.has("e") ? reader.read(kf.get("e")) : null;
            boolean hold = kf.has("h") && kf.get("h").getAsInt() == 1;
            CubicBezier easing = CubicBezier.LINEAR;
            if (kf.has("o") && kf.has("i")) {
                easing = new CubicBezier(first(kf.getAsJsonObject("o").get("x")), first(kf.getAsJsonObject("o").get("y")),
                        first(kf.getAsJsonObject("i").get("x")), first(kf.getAsJsonObject("i").get("y")));
            }
            keyframes.add(new Keyframe(time, start, end, hold, easing));
        }
        // El último keyframe del formato antiguo solo trae "t": hereda el final del anterior
        for (int i = 0; i < keyframes.size(); i++) {
            Keyframe kf = keyframes.get(i);
            if (kf.start == null) {
                double[] previous = i > 0 ? valueOrEnd(keyframes.get(i - 1)) : fallback;
                keyframes.set(i, new Keyframe(kf.time, previous, kf.end, kf.hold, kf.easing));
            }
        }
        if (keyframes.isEmpty()) {
            return constant(fallback);
        }
        return new LottieProperty(null, keyframes.toArray(Keyframe[]::new));
    }

    private static double[] valueOrEnd(Keyframe kf) {
        return kf.end != null ? kf.end : kf.start;
    }

    double[] value(double frame) {
        if (keyframes == null) {
            return constant;
        }
        Keyframe first = keyframes[0];
        if (frame <= first.time || keyframes.length == 1) {
            return first.start;
        }
        for (int i = 0; i < keyframes.length - 1; i++) {
            Keyframe a = keyframes[i];
            Keyframe b = keyframes[i + 1];
            if (frame < b.time) {
                double[] end = a.end != null ? a.end : b.start;
                if (a.hold || end == null || end.length != a.start.length || b.time <= a.time) {
                    return a.start;
                }
                double t = a.easing.ease((frame - a.time) / (b.time - a.time));
                double[] out = new double[a.start.length];
                for (int j = 0; j < out.length; j++) {
                    out[j] = a.start[j] + (end[j] - a.start[j]) * t;
                }
                return out;
            }
        }
        Keyframe last = keyframes[keyframes.length - 1];
        return last.start;
    }

    double scalar(double frame) {
        double[] v = value(frame);
        return v.length > 0 ? v[0] : 0;
    }

    private static boolean isKeyframeList(JsonElement k) {
        return k.isJsonArray() && !k.getAsJsonArray().isEmpty() && k.getAsJsonArray().get(0).isJsonObject()
                && k.getAsJsonArray().get(0).getAsJsonObject().has("t");
    }

    private static double first(JsonElement e) {
        if (e == null) {
            return 0;
        }
        if (e.isJsonArray()) {
            return e.getAsJsonArray().isEmpty() ? 0 : e.getAsJsonArray().get(0).getAsDouble();
        }
        return e.getAsDouble();
    }

    private static double[] numbers(JsonElement e) {
        if (e.isJsonPrimitive()) {
            return new double[] {e.getAsDouble()};
        }
        JsonArray array = e.getAsJsonArray();
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            JsonElement item = array.get(i);
            out[i] = item.isJsonPrimitive() ? item.getAsDouble() : 0;
        }
        return out;
    }

    private static double[] pathNumbers(JsonElement e) {
        JsonObject shape;
        if (e.isJsonArray()) {
            if (e.getAsJsonArray().isEmpty()) {
                return new double[0];
            }
            shape = e.getAsJsonArray().get(0).getAsJsonObject();
        } else {
            shape = e.getAsJsonObject();
        }
        JsonArray v = shape.getAsJsonArray("v");
        JsonArray in = shape.getAsJsonArray("i");
        JsonArray out = shape.getAsJsonArray("o");
        if (v == null) {
            return new double[0];
        }
        double[] flat = new double[v.size() * 6];
        for (int j = 0; j < v.size(); j++) {
            point(v, j, flat, j * 6);
            point(in, j, flat, j * 6 + 2);
            point(out, j, flat, j * 6 + 4);
        }
        return flat;
    }

    private static void point(JsonArray list, int index, double[] target, int offset) {
        if (list == null || index >= list.size()) {
            return;
        }
        JsonArray p = list.get(index).getAsJsonArray();
        target[offset] = p.get(0).getAsDouble();
        target[offset + 1] = p.size() > 1 ? p.get(1).getAsDouble() : 0;
    }
}

