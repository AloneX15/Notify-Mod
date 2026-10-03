package com.takumistudios.notifymod.client.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.takumistudios.notifymod.core.Durations;
import com.takumistudios.notifymod.core.Message;
import com.takumistudios.notifymod.core.Notification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compila {@code assets/<ns>/notify/presentations/<nombre>.json} en una {@link Presentation} (§8). Valida todo y
 * limita tamaños (§23: un pack con JSON abusivo no puede colgar el cliente). Los campos desconocidos se avisan, no
 * rompen la presentación.
 */
public final class PresentationParser {
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_JSON_CHARS = 256 * 1024;
    public static final int MAX_TRACKS = 64;
    public static final int MAX_DURATION_MS = Notification.MAX_DURATION_MS;

    private static final Set<String> ROOT_FIELDS = Set.of("format_version", "_comment", "id", "canvas", "background",
            "letterbox", "duration", "tracks", "docked", "preload");
    private static final Set<String> TRACK_FIELDS = Set.of("_comment", "type", "name", "time", "duration", "z", "anchor",
            "offset", "size", "opacity", "animation_in", "animation_out", "loop", "content", "color", "scale", "shadow",
            "max_width", "font", "file", "color2", "sound", "volume", "pitch", "item", "entity");

    private PresentationParser() {
    }

    public static Presentation parse(String id, String json) {
        try {
            return compile(id, json);
        } catch (ClassCastException | IllegalStateException | UnsupportedOperationException e) {
            // Gson las lanza si un campo tiene otro tipo (p. ej. un texto donde va un objeto)
            throw new IllegalArgumentException("Tipo de dato no válido: " + e.getMessage());
        }
    }

    private static Presentation compile(String id, String json) {
        if (json.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Presentación demasiado grande");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON no válido: " + e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("La presentación debe ser un objeto JSON");
        }
        JsonObject o = root.getAsJsonObject();
        List<String> warnings = new ArrayList<>();
        int version = o.has("format_version") ? o.get("format_version").getAsInt() : FORMAT_VERSION;
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException("format_version " + version + " es de una versión más nueva del mod");
        }
        unknownFields(o, ROOT_FIELDS, "", warnings);
        if (o.has("canvas") && !"16:9".equals(o.get("canvas").getAsString())) {
            warnings.add("canvas: solo se admite 16:9; se usa 16:9");
        }
        int duration = millis(o, "duration", 5000);
        if (duration < Notification.MIN_DURATION_MS || duration > MAX_DURATION_MS) {
            throw new IllegalArgumentException("duration fuera de rango (500 ms a 2 min)");
        }

        Presentation.Background background = null;
        if (o.has("background")) {
            JsonObject b = o.getAsJsonObject("background");
            String type = b.has("type") ? b.get("type").getAsString() : "dim";
            if (!"dim".equals(type)) {
                warnings.add("background: tipo '" + type + "' no soportado todavía; se usa dim");
            }
            background = new Presentation.Background(color(b, "color", 0x000000),
                    fraction(b, "opacity", 0.6f, "background.opacity"));
        }
        float letterbox = 0;
        int letterboxColor = 0x000000;
        if (o.has("letterbox")) {
            JsonObject l = o.getAsJsonObject("letterbox");
            letterbox = Math.min(0.25f, fraction(l, "height", 0.12f, "letterbox.height"));
            letterboxColor = color(l, "color", 0x000000);
        }

        JsonArray array = o.has("tracks") ? o.getAsJsonArray("tracks") : new JsonArray();
        if (array.size() > MAX_TRACKS) {
            throw new IllegalArgumentException("Demasiadas pistas (máximo " + MAX_TRACKS + ")");
        }
        List<TrackSpec> tracks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            try {
                TrackSpec track = track(i, array.get(i).getAsJsonObject(), duration, warnings);
                if (track.name() != null && !names.add(track.name())) {
                    throw new IllegalArgumentException("nombre repetido '" + track.name() + "'");
                }
                tracks.add(track);
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
                    | ClassCastException e) {
                throw new IllegalArgumentException("Pista " + i + ": " + e.getMessage());
            }
        }
        tracks.sort(Comparator.comparingInt(TrackSpec::z).thenComparingInt(TrackSpec::index));

        Set<String> omit = new HashSet<>();
        boolean mute = false;
        if (o.has("docked")) {
            JsonObject d = o.getAsJsonObject("docked");
            if (d.has("omit")) {
                for (JsonElement e : d.getAsJsonArray("omit")) {
                    omit.add(e.getAsString());
                }
            }
            mute = d.has("mute") && d.get("mute").getAsBoolean();
        }
        return new Presentation(id, duration, background, letterbox, letterboxColor, List.copyOf(tracks), Set.copyOf(omit),
                mute, o.has("preload") && o.get("preload").getAsBoolean(), List.copyOf(warnings));
    }

    private static TrackSpec track(int index, JsonObject t, int presentationDuration, List<String> warnings) {
        unknownFields(t, TRACK_FIELDS, "pista " + index + ": ", warnings);
        if (!t.has("type")) {
            throw new IllegalArgumentException("falta type");
        }
        TrackSpec.Type type;
        try {
            type = TrackSpec.Type.valueOf(t.get("type").getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo desconocido '" + t.get("type").getAsString()
                    + "' (text, texture, shape, sound, model)");
        }
        String name = t.has("name") ? t.get("name").getAsString() : null;
        int time = millis(t, "time", 0);
        if (time >= presentationDuration) {
            warnings.add("pista " + index + ": empieza después del final de la presentación y no se verá");
        }
        int duration = t.has("duration") ? millis(t, "duration", 0) : -1;
        float[] offset = pair(t, "offset", 0, 0);
        float[] size = pair(t, "size", 0.2f, 0.2f);
        AnimSpec in = anim(t, "animation_in");
        AnimSpec out = anim(t, "animation_out");
        AnimSpec loop = anim(t, "loop");
        if (loop != null && !loop.type().isLoop()) {
            throw new IllegalArgumentException("loop solo admite shake, pulse o spin");
        }
        if (in != null && in.type().isLoop() || out != null && out.type().isLoop()) {
            throw new IllegalArgumentException("shake, pulse y spin solo valen en loop");
        }

        Message content = null;
        boolean useMessage = false;
        String file = null;
        String sound = null;
        String model = null;
        boolean modelIsEntity = false;
        switch (type) {
            case TEXT -> {
                JsonElement c = t.get("content");
                if (c == null || c.isJsonPrimitive() && "{message}".equals(c.getAsString())) {
                    useMessage = true;
                } else if (c.isJsonPrimitive()) {
                    content = Message.literal(c.getAsString());
                } else {
                    JsonObject m = c.getAsJsonObject();
                    if (m.has("translate")) {
                        List<String> with = new ArrayList<>();
                        if (m.has("with")) {
                            m.getAsJsonArray("with").forEach(w -> with.add(w.getAsString()));
                        }
                        content = Message.translatable(m.get("translate").getAsString(), with);
                    } else if (m.has("text")) {
                        content = Message.literal(m.get("text").getAsString());
                    } else {
                        throw new IllegalArgumentException("content debe tener text o translate");
                    }
                }
                if (t.has("font")) {
                    warnings.add("pista " + index + ": font todavía no se aplica; se usa la fuente por defecto");
                }
            }
            case TEXTURE -> {
                file = resourceId(t, "file");
            }
            case SOUND -> sound = resourceId(t, "sound");
            case MODEL -> {
                if (t.has("item") == t.has("entity")) {
                    throw new IllegalArgumentException("un modelo necesita item o entity (solo uno)");
                }
                modelIsEntity = t.has("entity");
                model = resourceId(t, modelIsEntity ? "entity" : "item");
            }
            case SHAPE -> {
            }
        }
        float textScale = t.has("scale") ? t.get("scale").getAsFloat() : 1f;
        if (!(textScale > 0 && textScale <= 8)) {
            throw new IllegalArgumentException("scale debe estar entre 0 y 8");
        }
        return new TrackSpec(index, name, type, time, duration, t.has("z") ? t.get("z").getAsInt() : 0,
                Anchor.parse(t.has("anchor") ? t.get("anchor").getAsString() : null), offset[0], offset[1], size[0],
                size[1], fraction(t, "opacity", 1f, "opacity"), in, out, loop, content, useMessage,
                color(t, "color", 0xFFFFFF), textScale, !t.has("shadow") || t.get("shadow").getAsBoolean(),
                t.has("max_width") ? fraction(t, "max_width", 0.8f, "max_width") : 0.8f, file,
                t.has("color2") ? color(t, "color2", 0) : null, sound, clamp(t, "volume", 1f, 0, 1),
                clamp(t, "pitch", 1f, 0.5f, 2f), model, modelIsEntity);
    }

    private static AnimSpec anim(JsonObject t, String field) {
        if (!t.has(field)) {
            return null;
        }
        JsonObject a;
        if (t.get(field).isJsonPrimitive()) {
            // Forma corta: "loop": "spin" equivale a { "type": "spin" } con los valores por defecto
            a = new JsonObject();
            a.add("type", t.get(field));
        } else {
            a = t.getAsJsonObject(field);
        }
        AnimSpec.Type type = AnimSpec.Type.parse(a.has("type") ? a.get("type").getAsString() : "fade");
        int duration = millis(a, a.has("period") ? "period" : "duration", type.isLoop() ? 1000 : 400);
        float amount = a.has("amount") ? a.get("amount").getAsFloat() : type.defaultAmount;
        if (!Float.isFinite(amount)) {
            throw new IllegalArgumentException(field + ": amount no válido");
        }
        return new AnimSpec(type, Easing.parse(a.has("easing") ? a.get("easing").getAsString() : null), duration, amount);
    }

    // ------------------------------------------------------------ utilidades

    private static void unknownFields(JsonObject o, Set<String> known, String where, List<String> warnings) {
        for (String key : o.keySet()) {
            if (!known.contains(key)) {
                warnings.add(where + "campo desconocido '" + key + "'");
            }
        }
    }

    /** Milisegundos: número (ms) o texto con unidad ("1.5s" no; "1500ms", "2s"). */
    static int millis(JsonObject o, String field, int fallback) {
        if (!o.has(field)) {
            return fallback;
        }
        JsonElement e = o.get(field);
        long ms = e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : Durations.parseMillis(e.getAsString());
        if (ms < 0 || ms > MAX_DURATION_MS) {
            throw new IllegalArgumentException(field + " fuera de rango (0 a 2 min)");
        }
        return (int) ms;
    }

    private static float fraction(JsonObject o, String field, float fallback, String what) {
        float v = o.has(field) ? o.get(field).getAsFloat() : fallback;
        if (!(v >= 0 && v <= 1)) {
            throw new IllegalArgumentException(what + " debe estar entre 0 y 1");
        }
        return v;
    }

    private static float clamp(JsonObject o, String field, float fallback, float min, float max) {
        float v = o.has(field) ? o.get(field).getAsFloat() : fallback;
        return Float.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
    }

    private static float[] pair(JsonObject o, String field, float a, float b) {
        if (!o.has(field)) {
            return new float[] {a, b};
        }
        JsonArray array = o.getAsJsonArray(field);
        if (array.size() != 2) {
            throw new IllegalArgumentException(field + " debe tener 2 números");
        }
        float x = array.get(0).getAsFloat();
        float y = array.get(1).getAsFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > 4 || Math.abs(y) > 4) {
            throw new IllegalArgumentException(field + " fuera de rango (-4 a 4)");
        }
        return new float[] {x, y};
    }

    /** "#RRGGBB" o "#AARRGGBB"; devuelve RGB (el alfa va en opacity). */
    static int color(JsonObject o, String field, int fallback) {
        if (!o.has(field)) {
            return fallback;
        }
        String s = o.get(field).getAsString().trim();
        if (!s.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) {
            throw new IllegalArgumentException(field + " debe ser un color #RRGGBB");
        }
        return (int) (Long.parseLong(s.substring(1), 16) & 0xFFFFFF);
    }

    private static String resourceId(JsonObject o, String field) {
        if (!o.has(field)) {
            throw new IllegalArgumentException("falta " + field);
        }
        String id = o.get(field).getAsString().trim();
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (!Notification.isValidId(id) || id.contains("..")) {
            throw new IllegalArgumentException(field + " no es un id válido: " + id);
        }
        return id;
    }
}

