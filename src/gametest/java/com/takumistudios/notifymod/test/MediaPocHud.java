package com.takumistudios.notifymod.test;

import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.client.media.FrameSequence;
import com.takumistudios.notifymod.client.media.MediaDecoders;
import com.takumistudios.notifymod.client.media.MediaLimits;
import com.takumistudios.notifymod.client.media.MediaTexture;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Prueba de viabilidad de la Fase 0 (§24): carga un archivo de cada formato y dibuja en el HUD todos sus fotogramas,
 * uno al lado de otro, para comprobar en la captura que la tubería de medios llega bien hasta la GPU. Solo existe en el
 * mod de pruebas y está apagada hasta que el test de cliente la enciende.
 */
public final class MediaPocHud implements ClientModInitializer, HudElement {
    static final String[] FILES = {"sample.gif", "sample_lossless.webp", "sample_lossy_alpha.webp", "sample_still.webp",
            "sample_lottie.json"};
    private static final int CELL = 32;
    private static final int MAX_COLUMNS = 6;

    static volatile boolean enabled;
    private static boolean loaded;
    private static final List<Row> ROWS = new ArrayList<>();
    static final List<String> ERRORS = new ArrayList<>();

    private record Row(String label, List<MediaTexture> frames) {
    }

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("notifymod-test", "media_poc"), this);
    }

    /** Número de formatos cargados sin errores (llamar en el hilo del cliente). */
    static int loadedRows() {
        return ROWS.size();
    }

    private static void load() {
        loaded = true;
        for (String file : FILES) {
            try (InputStream in = MediaPocHud.class.getResourceAsStream("/notifymod-poc/" + file)) {
                if (in == null) {
                    ERRORS.add(file + ": no está en el classpath");
                    continue;
                }
                MediaDecoders.Decoded decoded = MediaDecoders.decode(in, MediaLimits.DEFAULT);
                FrameSequence frames = decoded.frames();
                List<MediaTexture> textures = new ArrayList<>();
                int columns = Math.min(MAX_COLUMNS, frames.frameCount());
                for (int c = 0; c < columns; c++) {
                    // Fotogramas repartidos por toda la animación
                    int index = columns == 1 ? 0 : c * (frames.frameCount() - 1) / (columns - 1);
                    Identifier id = Identifier.fromNamespaceAndPath("notifymod-test",
                            "poc/" + file.replace('.', '_') + "_" + index);
                    MediaTexture texture = new MediaTexture(id, frames);
                    texture.showFrame(index);
                    textures.add(texture);
                }
                ROWS.add(new Row(decoded.format() + " " + file, textures));
                if (!decoded.warnings().isEmpty()) {
                    NotifyMod.LOGGER.info("[PoC] {}: funciones no soportadas {}", file, decoded.warnings());
                }
            } catch (Exception e) {
                ERRORS.add(file + ": " + e);
                NotifyMod.LOGGER.error("[PoC] No se pudo cargar {}", file, e);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!enabled) {
            return;
        }
        if (!loaded) {
            load();
        }
        var font = Minecraft.getInstance().font;
        int y = 8;
        for (Row row : ROWS) {
            graphics.text(font, row.label(), 8, y, 0xFFFFFFFF);
            y += 10;
            int x = 8;
            for (MediaTexture texture : row.frames()) {
                FrameSequence frames = texture.frames();
                // Fondo de cuadros para ver la transparencia
                graphics.fill(x, y, x + CELL, y + CELL, 0xFF404040);
                graphics.fill(x, y, x + CELL / 2, y + CELL / 2, 0xFF808080);
                graphics.fill(x + CELL / 2, y + CELL / 2, x + CELL, y + CELL, 0xFF808080);
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, 0f, 0f, CELL, CELL,
                        frames.width(), frames.height(), frames.width(), frames.height());
                x += CELL + 4;
            }
            y += CELL + 6;
        }
    }
}

