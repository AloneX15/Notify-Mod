package com.takumistudios.notifymod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Plantilla de servidor (§5.2 del plan): {@code data/<ns>/notify/templates/<nombre>.json}. Define canales, prioridad,
 * colocación, texto y límites; al enviarla se completan los argumentos.
 */
public record Template(String id, Set<Channel> channels, String presentation, Placement placement, Priority priority,
        Message message, String key, long cooldownMs, int durationMs, NotificationStyle style) {
    public Template(String id, Set<Channel> channels, String presentation, Placement placement, Priority priority,
            Message message, String key, long cooldownMs, int durationMs) {
        this(id, channels, presentation, placement, priority, message, key, cooldownMs, durationMs, NotificationStyle.DEFAULTS);
    }

    public static final int FORMAT_VERSION = 1;
    public static final int MAX_JSON_CHARS = 64 * 1024;

    public Template {
        style = style == null ? NotificationStyle.DEFAULTS : style;
        channels = Set.copyOf(EnumSet.copyOf(channels));
    }

    /** Crea la notificación con los argumentos y, si no son nulos, los cambios que pida quien la envía. */
    public Notification toNotification(NotificationArgs args, Placement placementOverride, Priority priorityOverride,
            Boolean mirrorToChat) {
        EnumSet<Channel> finalChannels = EnumSet.copyOf(channels);
        if (mirrorToChat != null) {
            if (mirrorToChat) {
                finalChannels.add(Channel.CHAT);
            } else if (finalChannels.size() > 1) {
                finalChannels.remove(Channel.CHAT);
            }
        }
        return new Notification(finalChannels, presentation, message, args,
                priorityOverride != null ? priorityOverride : priority,
                placementOverride != null ? placementOverride : placement, key, durationMs, style);
    }

    /**
     * Lee una plantilla. El {@code id} sale de la ruta del archivo (un campo {@code id} dentro del JSON se ignora).
     */
    public static Template parse(String id, String json) {
        if (!Notification.isValidId(id)) {
            throw new IllegalArgumentException("Id de plantilla no válido: " + id);
        }
        if (json.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Plantilla demasiado grande");
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON no válido: " + e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("La plantilla debe ser un objeto JSON");
        }
        JsonObject o = root.getAsJsonObject();
        int version = o.has("format_version") ? o.get("format_version").getAsInt() : FORMAT_VERSION;
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException("format_version " + version + " es de una versión más nueva del mod");
        }

        EnumSet<Channel> channels = EnumSet.noneOf(Channel.class);
        JsonElement ch = o.get("channels");
        if (ch == null) {
            channels.add(Channel.SHOWCASE);
        } else if (ch.isJsonArray()) {
            for (JsonElement e : ch.getAsJsonArray()) {
                channels.add(Channel.parse(e.getAsString()));
            }
        } else {
            channels.add(Channel.parse(ch.getAsString()));
        }
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("La plantilla no tiene canales");
        }
        if (bool(o, "mirror_to_chat")) {
            channels.add(Channel.CHAT);
        }

        String presentation = string(o, "presentation", Notification.MOTD);
        if (!Notification.isValidId(presentation)) {
            throw new IllegalArgumentException("Id de presentación no válido: " + presentation);
        }
        Priority priority = o.has("priority") ? Priority.parse(o.get("priority").getAsString()) : Priority.NORMAL;
        Placement placement = o.has("placement") ? Placement.parse(o.get("placement").getAsString())
                : channels.contains(Channel.SHOWCASE) ? Placement.CENTER : Placement.TOP_RIGHT;
        Message message = message(o.get("message"));
        String key = o.has("key") ? o.get("key").getAsString() : null;
        long cooldown = o.has("cooldown") ? Durations.parseMillis(o.get("cooldown").getAsString()) : 0;
        int duration = o.has("duration")
                ? (int) Math.min(Integer.MAX_VALUE, Durations.parseMillis(o.get("duration").getAsString()))
                : Notification.defaultDuration(channels);

        Template template = new Template(id, channels, presentation, placement, priority, message, key, cooldown,
                duration, o.has("style") ? NotificationStyle.parse(o.getAsJsonObject("style")) : NotificationStyle.DEFAULTS);
        // Valida el resto de reglas (clave, duración...) construyendo una notificación de prueba
        template.toNotification(NotificationArgs.EMPTY, null, null, null);
        return template;
    }

    static Message message(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            throw new IllegalArgumentException("Falta el campo message");
        }
        if (e.isJsonPrimitive()) {
            return Message.literal(e.getAsString());
        }
        JsonObject m = e.getAsJsonObject();
        if (m.has("translate")) {
            List<String> with = new ArrayList<>();
            if (m.has("with")) {
                JsonArray array = m.getAsJsonArray("with");
                for (JsonElement w : array) {
                    with.add(w.isJsonPrimitive() ? ((JsonPrimitive) w).getAsString() : w.toString());
                }
            }
            return Message.translatable(m.get("translate").getAsString(), with);
        }
        if (m.has("text")) {
            return Message.literal(m.get("text").getAsString());
        }
        throw new IllegalArgumentException("message debe tener text o translate");
    }

    private static String string(JsonObject o, String name, String fallback) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String name) {
        return o.has(name) && o.get(name).getAsBoolean();
    }
}


