package com.takumistudios.notifymod.client.timeline;

import com.takumistudios.notifymod.core.Message;

/**
 * Una pista de la línea de tiempo, ya validada (§8.3). Posiciones y tamaños son fracciones del lienzo: {@code offset}
 * de su ancho y alto, {@code width}/{@code height} de su <b>alto</b> (así un cuadrado sigue siéndolo en pantallas
 * panorámicas). Un alto ≤ 0 en texturas mantiene la proporción de la imagen.
 */
public record TrackSpec(int index, String name, Type type, int time, int durationMs, int z, Anchor anchor,
        float offsetX, float offsetY, float width, float height, float opacity, AnimSpec in, AnimSpec out,
        AnimSpec loop,
        // texto
        Message content, boolean useMessage, int color, float textScale, boolean shadow, float maxWidth,
        // textura
        String file,
        // forma
        Integer color2,
        // sonido
        String sound, float volume, float pitch,
        // modelo 3D: id de ítem/bloque o de entidad
        String model, boolean modelIsEntity) {

    public enum Type { TEXT, TEXTURE, SHAPE, SOUND, MODEL }
}

