package com.takumistudios.notifymod.server;

import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.core.Template;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Plantillas del servidor (§5): las de los datapacks ({@code data/<ns>/notify/templates/*.json}) y las de
 * {@code config/notifymod/managed/templates/<ns>/<nombre>.json}, que tienen prioridad. Se recargan con
 * {@code /reload} y {@code /notify reload}. Un archivo con errores se salta y se informa en {@code /notify validate}.
 */
public final class TemplateRegistry {
    public static final String DIRECTORY = "notify/templates";
    private static final String SUFFIX = ".json";

    private volatile Map<String, Template> templates = Map.of();
    private volatile List<String> errors = List.of();
    private final Map<String, Long> lastSent = new HashMap<>();

    public Template get(String id) {
        return templates.get(id);
    }

    public Map<String, Template> all() {
        return templates;
    }

    public List<String> errors() {
        return errors;
    }

    /** Comprueba y registra el cooldown de una plantilla. Devuelve los ms que faltan, o 0 si se puede enviar. */
    public long cooldownRemaining(Template template, long now) {
        if (template.cooldownMs() <= 0) {
            return 0;
        }
        Long last = lastSent.get(template.id());
        if (last != null && now - last < template.cooldownMs()) {
            return template.cooldownMs() - (now - last);
        }
        lastSent.put(template.id(), now);
        return 0;
    }

    public void reload(ResourceManager resources, Path managedDir) {
        Map<String, Template> loaded = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        Map<Identifier, Resource> found = resources.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath().substring(DIRECTORY.length() + 1, file.getPath().length() - SUFFIX.length());
            String id = file.getNamespace() + ":" + path;
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.put(id, Template.parse(id, read(reader)));
            } catch (IOException | RuntimeException e) {
                problems.add(id + ": " + e.getMessage());
            }
        }
        loadManaged(managedDir, loaded, problems);
        templates = Collections.unmodifiableMap(loaded);
        errors = List.copyOf(problems);
        lastSent.keySet().retainAll(loaded.keySet());
        NotifyMod.LOGGER.info("Notify Mod: {} plantillas cargadas{}", loaded.size(),
                problems.isEmpty() ? "" : ", " + problems.size() + " con errores (ver /notify validate)");
        for (String problem : problems) {
            NotifyMod.LOGGER.warn("Plantilla no válida: {}", problem);
        }
    }

    private static void loadManaged(Path dir, Map<String, Template> loaded, List<String> problems) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir, 8)) {
            for (Path file : files.filter(p -> p.toString().endsWith(SUFFIX) && Files.isRegularFile(p)).toList()) {
                Path relative = dir.relativize(file);
                if (relative.getNameCount() < 2) {
                    problems.add("managed/" + relative + ": debe estar en una carpeta con el espacio de nombres");
                    continue;
                }
                String namespace = relative.getName(0).toString();
                String path = relative.subpath(1, relative.getNameCount()).toString().replace('\\', '/');
                String id = namespace + ":" + path.substring(0, path.length() - SUFFIX.length());
                try {
                    if (Files.size(file) > Template.MAX_JSON_CHARS * 4L) {
                        throw new IllegalArgumentException("archivo demasiado grande");
                    }
                    loaded.put(id, Template.parse(id, Files.readString(file, StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException e) {
                    problems.add("managed/" + id + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            problems.add("managed/: " + e.getMessage());
        }
    }

    private static String read(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) > 0) {
            out.append(buffer, 0, n);
            if (out.length() > Template.MAX_JSON_CHARS) {
                throw new IllegalArgumentException("Plantilla demasiado grande");
            }
        }
        return out.toString();
    }
}

