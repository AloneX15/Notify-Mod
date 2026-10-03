package com.takumistudios.notifymod.config;

import com.google.gson.JsonObject;
import java.nio.file.Path;

/**
 * {@code config/notifymod/client.json}: opciones del jugador (§19). Solo puede ocultar los avisos de esquina; el
 * Showcase del centro no tiene opción para ocultarlo.
 *
 * @param mediaQuality {@code "high"} o {@code "low"} (imágenes reducidas y la mitad de fotogramas, §9.3)
 */
public record ClientConfig(boolean hideCornerNotifications, double cardScale, int maxVisibleCards,
        String mediaQuality) {
    public static final int FORMAT_VERSION = 1;
    public static final ClientConfig DEFAULTS = new ClientConfig(false, 1.0, 3, "high");

    public static ClientConfig load(Path file) {
        return JsonConfigFile.load(file, DEFAULTS, ClientConfig::read, ClientConfig::write);
    }

    public ClientConfig withHideCornerNotifications(boolean hide) {
        return new ClientConfig(hide, cardScale, maxVisibleCards, mediaQuality);
    }

    static ClientConfig read(JsonObject o) {
        String quality = JsonConfigFile.string(o, "media_quality", DEFAULTS.mediaQuality, 8);
        return new ClientConfig(
                JsonConfigFile.bool(o, "hide_corner_notifications", DEFAULTS.hideCornerNotifications),
                JsonConfigFile.clamp(o, "card_scale", DEFAULTS.cardScale, 0.5, 2.0),
                JsonConfigFile.clamp(o, "max_visible_cards", DEFAULTS.maxVisibleCards, 1, 8),
                "low".equalsIgnoreCase(quality) ? "low" : "high");
    }

    public static JsonObject write(ClientConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("format_version", FORMAT_VERSION);
        o.addProperty("hide_corner_notifications", c.hideCornerNotifications);
        o.addProperty("card_scale", c.cardScale);
        o.addProperty("max_visible_cards", c.maxVisibleCards);
        o.addProperty("media_quality", c.mediaQuality);
        return o;
    }
}

