package com.takumistudios.notifymod.client.media;

import com.takumistudios.notifymod.NotifyMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * Caché de texturas de las presentaciones (§9.1). Cualquier formato de la tubería (PNG, GIF, WebP, Lottie) se lee y
 * decodifica <b>en segundo plano</b>; mientras tanto la pista no se dibuja y el juego no se detiene. Solo la subida a
 * la GPU ocurre en el hilo de render. Presupuesto de memoria con descarte del menos usado (LRU). Se vacía al recargar
 * recursos (F3+T).
 *
 * <p>Todos los métodos públicos se llaman desde el hilo de render.
 */
public final class MediaCache {
    private final long budgetBytes;
    private final Executor background;
    private final Supplier<MediaQuality> quality;
    private final LinkedHashMap<String, MediaTexture> textures = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Long> sizes = new HashMap<>();
    private final Map<String, CompletableFuture<FrameSequence>> pending = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private long usedBytes;
    private int generation;

    public MediaCache(long budgetBytes, Executor background, Supplier<MediaQuality> quality) {
        this.budgetBytes = budgetBytes;
        this.background = background;
        this.quality = quality;
    }

    /**
     * La textura de {@code id} ("ns:ruta/archivo.png") si ya está lista. Si no, empieza a cargarla en segundo plano y
     * devuelve {@code null} (se dibujará en cuanto termine). También {@code null} si no existe o es inválida.
     */
    public MediaTexture get(String id) {
        MediaTexture texture = textures.get(id);
        if (texture != null || failed.contains(id)) {
            return texture;
        }
        CompletableFuture<FrameSequence> future = pending.get(id);
        if (future == null) {
            start(id, Minecraft.getInstance().getResourceManager());
            return null;
        }
        if (!future.isDone()) {
            return null;
        }
        pending.remove(id);
        try {
            return upload(id, future.join());
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            fail(id, cause.getMessage());
            return null;
        }
    }

    /** Empieza a cargar en segundo plano lo que todavía no esté (presentaciones con {@code preload}). */
    public void prefetch(Collection<String> ids) {
        prefetch(ids, Minecraft.getInstance().getResourceManager());
    }

    /** Igual, con un gestor de recursos concreto (el de la recarga en curso). */
    public void prefetch(Collection<String> ids, ResourceProvider resources) {
        for (String id : ids) {
            if (!textures.containsKey(id) && !failed.contains(id) && !pending.containsKey(id)) {
                start(id, resources);
            }
        }
    }

    public boolean isReady(String id) {
        return textures.containsKey(id);
    }

    private void start(String id, ResourceProvider resources) {
        Identifier file = Identifier.tryParse(id);
        if (file == null) {
            fail(id, "id no válido");
            return;
        }
        // El resource manager se consulta aquí (hilo de render); leer y decodificar va aparte
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            fail(id, "no está en ningún resource pack");
            return;
        }
        Resource res = resource.get();
        // Spritesheet: "x.png" con "x.png.mcmeta" al lado, como las texturas animadas de Minecraft
        Resource meta = resources.getResource(Identifier.fromNamespaceAndPath(file.getNamespace(),
                file.getPath() + ".mcmeta")).orElse(null);
        MediaQuality q = quality.get();
        pending.put(id, CompletableFuture.supplyAsync(() -> {
            try (InputStream in = res.open()) {
                byte[] data = MediaDecoders.readLimited(in, MediaLimits.DEFAULT.maxFileBytes());
                String mcmeta = meta == null ? null : readMcmeta(meta);
                MediaDecoders.Decoded decoded = MediaDecoders.decode(data, mcmeta, MediaLimits.DEFAULT);
                if (!decoded.warnings().isEmpty()) {
                    NotifyMod.LOGGER.info("{}: funciones no soportadas {}", id, decoded.warnings());
                }
                return q.apply(decoded.frames());
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, background));
    }

    private static String readMcmeta(Resource meta) throws IOException, MediaException {
        try (InputStream in = meta.open()) {
            return new String(MediaDecoders.readLimited(in, SpriteSheet.MAX_MCMETA_CHARS), StandardCharsets.UTF_8);
        }
    }

    private MediaTexture upload(String id, FrameSequence frames) {
        Identifier file = Identifier.parse(id);
        Identifier textureId = Identifier.fromNamespaceAndPath(NotifyMod.MOD_ID,
                "media/" + generation + "/" + file.getNamespace() + "/" + file.getPath());
        MediaTexture texture = new MediaTexture(textureId, frames);
        long bytes = frames.estimatedBytes();
        textures.put(id, texture);
        sizes.put(id, bytes);
        usedBytes += bytes;
        evict(id);
        return texture;
    }

    private void evict(String keep) {
        Iterator<Map.Entry<String, MediaTexture>> it = textures.entrySet().iterator();
        while (usedBytes > budgetBytes && it.hasNext()) {
            Map.Entry<String, MediaTexture> eldest = it.next();
            if (eldest.getKey().equals(keep)) {
                continue;
            }
            eldest.getValue().close();
            usedBytes -= sizes.remove(eldest.getKey());
            it.remove();
        }
    }

    private void fail(String id, String why) {
        failed.add(id);
        NotifyMod.LOGGER.warn("No se pudo cargar la imagen {}: {}", id, why);
    }

    public long usedBytes() {
        return usedBytes;
    }

    /** Lo vacía todo (recarga de recursos o cambio de calidad). Las cargas en curso se descartan. */
    public void clear() {
        textures.values().forEach(MediaTexture::close);
        textures.clear();
        sizes.clear();
        pending.values().forEach(f -> f.cancel(false));
        pending.clear();
        failed.clear();
        usedBytes = 0;
        generation++; // ids de textura nuevos: nada de una carga antigua puede pisar una nueva
    }
}

