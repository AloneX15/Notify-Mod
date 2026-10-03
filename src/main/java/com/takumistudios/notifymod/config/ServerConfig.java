package com.takumistudios.notifymod.config;

import com.google.gson.JsonObject;
import java.nio.file.Path;

/** {@code config/notifymod/server.json}: ajustes del servidor (§5). */
public record ServerConfig(boolean requireClient, String missingClientMessage, String downloadUrl,
        boolean defaultMirrorToChat, int maxCommandsPerMinute) {
    public static final int FORMAT_VERSION = 1;
    public static final ServerConfig DEFAULTS = new ServerConfig(true, "", "https://github.com/AloneX15/Notify-Mod/releases",
            false, 30);

    public static ServerConfig load(Path file) {
        return JsonConfigFile.load(file, DEFAULTS, ServerConfig::read, ServerConfig::write);
    }

    static ServerConfig read(JsonObject o) {
        return new ServerConfig(
                JsonConfigFile.bool(o, "require_client", DEFAULTS.requireClient),
                JsonConfigFile.string(o, "missing_client_message", DEFAULTS.missingClientMessage, 512),
                JsonConfigFile.string(o, "download_url", DEFAULTS.downloadUrl, 256),
                JsonConfigFile.bool(o, "default_mirror_to_chat", DEFAULTS.defaultMirrorToChat),
                JsonConfigFile.clamp(o, "max_commands_per_minute", DEFAULTS.maxCommandsPerMinute, 1, 600));
    }

    static JsonObject write(ServerConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("format_version", FORMAT_VERSION);
        o.addProperty("_comment", "Notify Mod: ajustes del servidor. Creado por TakumiStudios.");
        o.addProperty("require_client", c.requireClient);
        o.addProperty("missing_client_message", c.missingClientMessage);
        o.addProperty("download_url", c.downloadUrl);
        o.addProperty("default_mirror_to_chat", c.defaultMirrorToChat);
        o.addProperty("max_commands_per_minute", c.maxCommandsPerMinute);
        return o;
    }
}

