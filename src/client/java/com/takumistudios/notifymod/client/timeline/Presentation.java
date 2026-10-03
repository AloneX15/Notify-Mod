package com.takumistudios.notifymod.client.timeline;

import java.util.List;
import java.util.Set;

/**
 * Presentación compilada (§8): inmutable, se crea al recargar recursos y en el render solo se lee. Las pistas van
 * ordenadas por capa (z) y, a igual capa, por orden de declaración.
 *
 * @param background  fondo a pantalla completa (dim) o {@code null}
 * @param letterbox   alto de cada barra de cine en fracción de pantalla (0 = sin barras)
 * @param dockedOmit  nombres de pistas que no se dibujan en versión compacta (el fondo y las barras nunca)
 * @param preload     decodificar sus imágenes al cargar los recursos, antes de que se use (§9.1)
 */
public record Presentation(String id, int durationMs, Background background, float letterbox, int letterboxColor,
        List<TrackSpec> tracks, Set<String> dockedOmit, boolean dockedMute, boolean preload, List<String> warnings) {

    public record Background(int color, float opacity) {
    }

    public boolean omittedWhenDocked(TrackSpec track) {
        return track.name() != null && dockedOmit.contains(track.name());
    }

    /** Archivos de imagen que usa, para precargarlos. */
    public List<String> textureFiles() {
        return tracks.stream().filter(t -> t.type() == TrackSpec.Type.TEXTURE).map(TrackSpec::file).distinct().toList();
    }
}

