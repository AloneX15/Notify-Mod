package com.takumistudios.notifymod.core;

import com.google.gson.JsonObject;
import java.util.Set;

/** Validated, immutable text-card theme; timeline presentations retain their own tracks. */
public record NotificationStyle(Message title, int chatColor, int titleColor, int textColor,
        int backgroundColor, int hintColor, double backgroundOpacity, int width, boolean shadow,
        boolean showIcon, boolean showHideHint) {
    public static final NotificationStyle DEFAULTS = new NotificationStyle(
            Message.translatable("notifymod.notice.title", java.util.List.of()),
            0x00CC33, 0xCC2020, 0xEE9999, 0x101010, 0xAAAAAA, 0.65, 240, true, true, true);
    private static final Set<String> FIELDS = Set.of("title", "chat_color", "title_color", "text_color",
            "background_color", "hint_color", "background_opacity", "width", "shadow", "show_icon", "show_hide_hint");

    public NotificationStyle {
        java.util.Objects.requireNonNull(title, "title");
        for (int color : new int[]{chatColor, titleColor, textColor, backgroundColor, hintColor}) {
            if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Color outside RGB range");
        }
        if (!Double.isFinite(backgroundOpacity) || backgroundOpacity < 0 || backgroundOpacity > 1)
            throw new IllegalArgumentException("background_opacity must be 0..1");
        if (width < 120 || width > 480) throw new IllegalArgumentException("width must be 120..480");
        if (title.text().length() > 128 || title.with().size() > 4)
            throw new IllegalArgumentException("Title too long");
    }

    public static NotificationStyle parse(JsonObject o) {
        for (String key : o.keySet()) if (!FIELDS.contains(key))
            throw new IllegalArgumentException("Unknown style field: " + key);
        NotificationStyle d = DEFAULTS;
        return new NotificationStyle(o.has("title") ? title(o) : d.title,
                color(o, "chat_color", d.chatColor), color(o, "title_color", d.titleColor),
                color(o, "text_color", d.textColor), color(o, "background_color", d.backgroundColor),
                color(o, "hint_color", d.hintColor), number(o, "background_opacity", d.backgroundOpacity),
                integer(o, "width", d.width), bool(o, "shadow", d.shadow),
                bool(o, "show_icon", d.showIcon), bool(o, "show_hide_hint", d.showHideHint));
    }

    private static Message title(JsonObject o) {
        var value = o.get("title");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return Message.literal(value.getAsString());
        if (!value.isJsonObject()) throw new IllegalArgumentException("title must be text or a message object");
        var t = value.getAsJsonObject();
        for (String key : t.keySet()) if (!Set.of("text", "translate", "with").contains(key))
            throw new IllegalArgumentException("Unknown title field: " + key);
        String key = t.has("translate") ? "translate" : "text";
        if (!t.has(key) || !t.get(key).isJsonPrimitive() || !t.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("title requires text or translate string");
        if (t.has("with")) {
            if (!t.get("with").isJsonArray()) throw new IllegalArgumentException("title.with must be an array");
            for (var arg : t.getAsJsonArray("with")) {
                if (!arg.isJsonPrimitive() || !arg.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("title.with arguments must be strings");
            }
        }
        return Template.message(t);
    }

    private static int color(JsonObject o, String key, int fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isString()
                || !o.get(key).getAsString().matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException(key + " must be #RRGGBB");
        return Integer.parseInt(o.get(key).getAsString().substring(1), 16);
    }

    private static double number(JsonObject o, String key, double fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " must be numeric");
        return o.get(key).getAsDouble();
    }

    private static int integer(JsonObject o, String key, int fallback) {
        double n = number(o, key, fallback);
        if (!Double.isFinite(n) || n != Math.rint(n) || n < 120 || n > 480)
            throw new IllegalArgumentException(key + " must be an integer in 120..480");
        return (int)n;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        if (!o.has(key)) return fallback;
        if (!o.get(key).isJsonPrimitive() || !o.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException(key + " must be boolean");
        return o.get(key).getAsBoolean();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        JsonObject t = new JsonObject();
        t.addProperty(title.translate() ? "translate" : "text", title.text());
        if (!title.with().isEmpty()) {
            com.google.gson.JsonArray with = new com.google.gson.JsonArray();
            title.with().forEach(with::add);
            t.add("with", with);
        }
        o.add("title", t);
        o.addProperty("chat_color", hex(chatColor));
        o.addProperty("title_color", hex(titleColor));
        o.addProperty("text_color", hex(textColor));
        o.addProperty("background_color", hex(backgroundColor));
        o.addProperty("hint_color", hex(hintColor));
        o.addProperty("background_opacity", backgroundOpacity);
        o.addProperty("width", width);
        o.addProperty("shadow", shadow);
        o.addProperty("show_icon", showIcon);
        o.addProperty("show_hide_hint", showHideHint);
        return o;
    }

    private static String hex(int color) { return String.format(java.util.Locale.ROOT, "#%06X", color); }
}
