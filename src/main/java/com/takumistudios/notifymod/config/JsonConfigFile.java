package com.takumistudios.notifymod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lectura y escritura de archivos de configuración JSON (norma TakumiStudios): versionados, con valores por defecto
 * seguros, copia {@code .bak} si el archivo está dañado y escritura atómica (archivo temporal + renombrado).
 */
public final class JsonConfigFile {
    private static final Logger LOGGER = LoggerFactory.getLogger("notifymod");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonConfigFile() {
    }

    /**
     * Carga un archivo. Si no existe, lo crea con los valores por defecto. Si está dañado, lo copia a {@code .bak},
     * avisa en el log y usa los valores por defecto. Nunca lanza excepciones.
     *
     * @param reader  convierte el JSON en la configuración (validando rangos)
     * @param writer  convierte la configuración en JSON
     */
    public static <T> T load(Path file, T defaults, Function<JsonObject, T> reader, Function<T, JsonObject> writer) {
        try {
            if (!Files.exists(file)) {
                save(file, writer.apply(defaults));
                return defaults;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            T value = reader.apply(JsonParser.parseString(text).getAsJsonObject());
            // Reescribe para añadir campos nuevos y normalizar valores fuera de rango
            JsonObject normalized = writer.apply(value);
            if (!normalized.equals(JsonParser.parseString(text))) {
                save(file, normalized);
            }
            return value;
        } catch (RuntimeException | IOException e) {
            LOGGER.warn("Configuración dañada en {} ({}). Se guarda una copia en .bak y se usan los valores por defecto.",
                    file, e.toString());
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                save(file, writer.apply(defaults));
            } catch (IOException | RuntimeException ignored) {
                // Sin disco no hay nada más que hacer: se sigue con los valores por defecto
            }
            return defaults;
        }
    }

    /** Escritura atómica: si el juego se cierra a mitad, el archivo anterior queda intacto. */
    public static void save(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Guarda en segundo plano (para cambios hechos durante la partida). Los errores solo se registran. */
    public static void saveAsync(Path file, JsonObject json, Consumer<Runnable> executor) {
        executor.accept(() -> {
            try {
                save(file, json);
            } catch (IOException e) {
                LOGGER.warn("No se pudo guardar {}", file, e);
            }
        });
    }

    static int clamp(JsonObject o, String name, int fallback, int min, int max) {
        if (!o.has(name)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, o.get(name).getAsInt()));
    }

    static double clamp(JsonObject o, String name, double fallback, double min, double max) {
        if (!o.has(name)) {
            return fallback;
        }
        double v = o.get(name).getAsDouble();
        return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : fallback;
    }

    static boolean bool(JsonObject o, String name, boolean fallback) {
        return o.has(name) ? o.get(name).getAsBoolean() : fallback;
    }

    static String string(JsonObject o, String name, String fallback, int maxLength) {
        if (!o.has(name) || o.get(name).isJsonNull()) {
            return fallback;
        }
        String s = o.get(name).getAsString();
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }
}

