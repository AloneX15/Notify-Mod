package com.takumistudios.notifymod.core;

import com.google.gson.*;
import java.util.*;

/** Validated, immutable datapack trigger. Unknown fields fail instead of silently changing its meaning. */
public record Trigger(String id, String type, boolean enabled, String template, Placement placement,
        Priority priority, Map<String, String> args, Map<String, List<String>> conditions,
        List<AudienceRule> include, List<AudienceRule> exclude, String causeDelivery,
        long cooldownMs, long playerCooldownMs, int maxPerMinute, boolean coordinates) {
    public static final Set<String> TYPES = Set.of("player_death", "player_join", "player_leave",
            "player_respawn", "chat_keyword", "server_ready", "server_stopping", "manual");
    private static final Set<String> FIELDS = Set.of("format_version", "_comment", "id", "type", "enabled",
            "template", "placement", "priority", "args", "conditions", "audience", "cause_delivery", "cooldown",
            "per_player_cooldown", "max_per_minute", "allow_coordinates");
    private static final Set<String> FILTERS = Set.of("dimension", "damage_type", "killer_type", "cause_permission",
            "cause_group", "keyword");
    private static final Set<String> VARIABLES = Set.of("player", "killer", "death_message", "dimension", "time", "x", "y", "z");

    public record AudienceRule(String type, String value) {
        public AudienceRule {
            if (!Set.of("all", "involved", "team", "permission", "group", "dimension").contains(type)) {
                throw new IllegalArgumentException("Unsupported audience: " + type);
            }
            if (value.length() > 256 || ((!type.equals("all") && !type.equals("involved")) && value.isBlank())) {
                throw new IllegalArgumentException("Invalid audience value");
            }
        }
    }

    public Trigger {
        args = Map.copyOf(args);
        Map<String, List<String>> copy = new LinkedHashMap<>();
        conditions.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        conditions = Map.copyOf(copy);
        include = List.copyOf(include);
        exclude = List.copyOf(exclude);
    }

    public static Trigger parse(String id, String text) {
        if (!isValidId(id) || text.length() > Template.MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Invalid trigger id or oversized JSON");
        }
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        for (String key : o.keySet()) if (!FIELDS.contains(key)) {
            throw new IllegalArgumentException("Unsupported trigger field: " + key);
        }
        if (!o.has("format_version") || integer(o, "format_version", 1) != 1) {
            throw new IllegalArgumentException("Expected format_version: 1");
        }
        String type = string(o, "type", "manual");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unavailable trigger type: " + type);
        String template = string(o, "template", "");
        if (!Notification.isValidId(template)) throw new IllegalArgumentException("Invalid template id");
        Map<String, String> args = new LinkedHashMap<>();
        if (o.has("args")) o.getAsJsonObject("args").entrySet().forEach(e -> args.put(e.getKey(), e.getValue().getAsString()));
        NotificationArgs.of(args);
        boolean coordinates = bool(o, "allow_coordinates");
        for (String value : args.values()) if (value.startsWith("$")) {
            String variable = value.substring(1);
            if (!VARIABLES.contains(variable)) throw new IllegalArgumentException("Unknown event variable: " + value);
            if (Set.of("x", "y", "z").contains(variable) && !coordinates) {
                throw new IllegalArgumentException("Coordinates require allow_coordinates: true");
            }
        }
        Map<String, List<String>> conditions = new LinkedHashMap<>();
        if (o.has("conditions")) for (var e : o.getAsJsonObject("conditions").entrySet()) {
            if (!FILTERS.contains(e.getKey())) throw new IllegalArgumentException("Unsupported condition: " + e.getKey());
            List<String> values = new ArrayList<>();
            if (e.getValue().isJsonArray()) e.getValue().getAsJsonArray().forEach(v -> values.add(v.getAsString()));
            else values.add(e.getValue().getAsString());
            if (values.isEmpty() || values.size() > 32 || values.stream().anyMatch(v -> v.isBlank() || v.length() > 256)) {
                throw new IllegalArgumentException("Invalid condition values");
            }
            conditions.put(e.getKey(), values);
        }
        List<AudienceRule> include = List.of(new AudienceRule("all", ""));
        List<AudienceRule> exclude = List.of();
        if (o.has("audience")) {
            JsonObject audience = o.getAsJsonObject("audience");
            for (String k : audience.keySet()) if (!Set.of("include", "exclude").contains(k)) {
                throw new IllegalArgumentException("Unsupported audience field: " + k);
            }
            if (audience.has("include")) include = rules(audience.getAsJsonArray("include"));
            if (audience.has("exclude")) exclude = rules(audience.getAsJsonArray("exclude"));
        }
        String delivery = string(o, "cause_delivery", "after_respawn");
        if (!Set.of("after_respawn", "skip", "immediate").contains(delivery)
                || (type.equals("player_death") && delivery.equals("immediate"))) {
            throw new IllegalArgumentException("Invalid cause_delivery for " + type);
        }
        int max = integer(o, "max_per_minute", 6);
        if (max < 1 || max > 120) throw new IllegalArgumentException("max_per_minute must be 1..120");
        return new Trigger(id, type, bool(o, "enabled"), template,
                o.has("placement") ? Placement.parse(o.get("placement").getAsString()) : null,
                o.has("priority") ? Priority.parse(o.get("priority").getAsString()) : null,
                args, conditions, include, exclude, delivery, duration(o, "cooldown"),
                duration(o, "per_player_cooldown"), max, coordinates);
    }

    public static boolean isValidId(String id) {
        if (!Notification.isValidId(id)) return false;
        String path = id.substring(id.indexOf(':') + 1);
        return Arrays.stream(path.split("/", -1)).noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
    }

    private static List<AudienceRule> rules(JsonArray array) {
        if (array.size() > 32) throw new IllegalArgumentException("Too many audience rules");
        List<AudienceRule> rules = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject rule = element.getAsJsonObject();
            if (rule.has("type")) {
                for (String k : rule.keySet()) if (!Set.of("type", "value").contains(k)) {
                    throw new IllegalArgumentException("Unsupported audience rule field: " + k);
                }
                rules.add(new AudienceRule(rule.get("type").getAsString(), string(rule, "value", "")));
            } else {
                if (rule.size() != 1) throw new IllegalArgumentException("Expected one audience rule");
                var e = rule.entrySet().iterator().next();
                rules.add(new AudienceRule(e.getKey(), e.getValue().getAsString()));
            }
        }
        return List.copyOf(rules);
    }

    private static long duration(JsonObject o, String field) {
        long ms = o.has(field) ? Durations.parseMillis(o.get(field).getAsString()) : 0;
        if (ms < 0 || ms > 86_400_000) throw new IllegalArgumentException("Invalid " + field);
        return ms;
    }

    private static String string(JsonObject o, String name, String fallback) {
        return o.has(name) ? o.get(name).getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String name) {
        if (!o.has(name)) return false;
        JsonElement value = o.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected boolean: " + name);
        }
        return value.getAsBoolean();
    }

    private static int integer(JsonObject o, String name, int fallback) {
        if (!o.has(name)) return fallback;
        JsonElement value = o.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected integer: " + name);
        }
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Expected integer: " + name, ex); }
    }

    public NotificationArgs resolveArgs(Map<String, String> event) {
        Map<String, String> values = new LinkedHashMap<>();
        args.forEach((k, v) -> {
            String value = v.startsWith("$") ? event.getOrDefault(v.substring(1), "") : v;
            values.put(k, value.substring(0, Math.min(value.length(), NotificationArgs.MAX_VALUE_LENGTH)));
        });
        return NotificationArgs.of(values);
    }
}
