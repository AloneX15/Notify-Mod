package com.takumistudios.notifymod.client.timeline;

import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.client.media.MediaCache;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Presentaciones del cliente (§5): {@code assets/<ns>/notify/presentations/<nombre>.json}. Se compilan al recargar
 * recursos (F3+T) y en el render solo se consultan. Una presentación con errores se salta y se avisa en el log.
 */
public final class PresentationRegistry implements ResourceManagerReloadListener {
    public static final String DIRECTORY = "notify/presentations";
    private static final String SUFFIX = ".json";

    private final MediaCache media;
    private volatile Map<String, Presentation> presentations = Map.of();
    private volatile List<String> errors = List.of();

    public PresentationRegistry(MediaCache media) {
        this.media = media;
    }

    public Presentation get(String id) {
        return presentations.get(id);
    }

    public Map<String, Presentation> all() {
        return presentations;
    }

    public List<String> errors() {
        return errors;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        Map<String, Presentation> loaded = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        Map<Identifier, Resource> found = resources.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath().substring(DIRECTORY.length() + 1, file.getPath().length() - SUFFIX.length());
            String id = file.getNamespace() + ":" + path;
            try (Reader reader = entry.getValue().openAsReader()) {
                Presentation presentation = PresentationParser.parse(id, read(reader));
                loaded.put(id, presentation);
                for (String warning : presentation.warnings()) {
                    NotifyMod.LOGGER.warn("Presentación {}: {}", id, warning);
                }
            } catch (IOException | RuntimeException e) {
                problems.add(id + ": " + e.getMessage());
                NotifyMod.LOGGER.warn("Presentación no válida {}: {}", id, e.getMessage());
            }
        }
        presentations = Collections.unmodifiableMap(loaded);
        errors = List.copyOf(problems);
        media.clear();
        loaded.values().stream().filter(Presentation::preload)
                .forEach(p -> media.prefetch(p.textureFiles(), resources));
        NotifyMod.LOGGER.info("Notify Mod: {} presentaciones cargadas", loaded.size());
    }

    private static String read(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int n;
        while ((n = reader.read(buffer)) > 0) {
            out.append(buffer, 0, n);
            if (out.length() > PresentationParser.MAX_JSON_CHARS) {
                throw new IllegalArgumentException("Presentación demasiado grande");
            }
        }
        return out.toString();
    }
}

