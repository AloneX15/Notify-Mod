package com.takumistudios.notifymod.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Textura dinámica que muestra un fotograma de una {@link FrameSequence}. Solo vuelve a subir píxeles cuando cambia el
 * fotograma visible. Crear, actualizar y cerrar siempre en el hilo de render.
 */
public final class MediaTexture implements AutoCloseable {
    private final Identifier id;
    private final FrameSequence frames;
    private final DynamicTexture texture;
    private int shownFrame = -1;
    private boolean closed;

    public MediaTexture(Identifier id, FrameSequence frames) {
        this.id = id;
        this.frames = frames;
        this.texture = new DynamicTexture(id::toString, new NativeImage(frames.width(), frames.height(), false));
        Minecraft.getInstance().getTextureManager().register(id, texture);
        showFrame(0);
    }

    public Identifier id() {
        return id;
    }

    public FrameSequence frames() {
        return frames;
    }

    /** Muestra el fotograma que toca en ese instante (en bucle). */
    public void update(long timeMs) {
        showFrame(frames.frameIndexAt(timeMs));
    }

    public void showFrame(int index) {
        if (closed || index == shownFrame) {
            return;
        }
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        int[] argb = frames.frame(index);
        int width = frames.width();
        for (int y = 0; y < frames.height(); y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                pixels.setPixel(x, y, argb[row + x]);
            }
        }
        texture.upload();
        shownFrame = index;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }
}

