package com.takumistudios.notifymod.client.hud;

import com.takumistudios.notifymod.NotificationText;
import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.client.media.FrameSequence;
import com.takumistudios.notifymod.client.media.MediaCache;
import com.takumistudios.notifymod.client.media.MediaTexture;
import com.takumistudios.notifymod.client.state.ActiveNotification;
import com.takumistudios.notifymod.client.timeline.Presentation;
import com.takumistudios.notifymod.client.timeline.TrackSpec;
import com.takumistudios.notifymod.client.timeline.TrackState;
import com.takumistudios.notifymod.core.Notification;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;

/**
 * Dibuja una presentación compilada (§8) en un rectángulo: la pantalla entera (centro) o un recuadro 16:9 en una
 * esquina (versión compacta, §6.3, sin fondo, barras de cine ni las pistas de {@code docked.omit}). Las unidades son
 * proporcionales al alto del rectángulo, con un lienzo de referencia de 270 de alto.
 */
public final class PresentationRenderer {
    static final float REFERENCE_HEIGHT = 270f;
    /** Ancho de la versión compacta en fracción del ancho de pantalla. */
    public static final float DOCKED_WIDTH = 0.28f;

    private final MediaCache media;
    private final ModelCache models = new ModelCache();
    private final TrackState state = new TrackState();

    /** Estado de reproducción de una presentación concreta (sonidos ya lanzados y textos compuestos). */
    private static final class Playback {
        final BitSet playedSounds = new BitSet();
        final Map<Integer, TextCache> texts = new HashMap<>();
    }

    private record TextCache(int wrapWidth, List<FormattedCharSequence> lines) {
    }

    public PresentationRenderer(MediaCache media) {
        this.media = media;
    }

    /** Modelos 3D desactivados por un fallo al dibujarse. */
    public int disabledModels() {
        return models.disabledCount();
    }

    public void draw(GuiGraphicsExtractor g, Font font, Presentation p, ActiveNotification active, int x, int y, int w,
            int h, boolean docked, long now) {
        Playback playback = playback(active);
        long t = now - active.start();
        playSounds(p, playback, t, docked);
        float master = active.alpha(now);
        if (master <= 0.01f) {
            return;
        }
        if (docked) {
            g.fill(x, y, x + w, y + h, NotifyHud.withAlpha(0x101010, master * 0.78f));
        } else {
            int sw = g.guiWidth();
            int sh = g.guiHeight();
            if (p.background() != null) {
                g.fill(0, 0, sw, sh, NotifyHud.withAlpha(p.background().color(), p.background().opacity() * master));
            }
            if (p.letterbox() > 0) {
                int bar = Math.round(sh * p.letterbox() * master);
                int color = NotifyHud.withAlpha(p.letterboxColor(), 1f);
                g.fill(0, 0, sw, bar, color);
                g.fill(0, sh - bar, sw, sh, color);
            }
        }
        float unit = h / REFERENCE_HEIGHT;
        for (TrackSpec track : p.tracks()) {
            if (track.type() == TrackSpec.Type.SOUND || docked && p.omittedWhenDocked(track)) {
                continue;
            }
            state.evaluate(track, t, p.durationMs());
            float alpha = state.alpha * master;
            if (!state.visible || alpha <= 0.01f) {
                continue;
            }
            float ax = x + w * track.anchor().fx + (track.offsetX() + state.dx) * w;
            float ay = y + h * track.anchor().fy + (track.offsetY() + state.dy) * h;
            if (track.type() == TrackSpec.Type.MODEL && track.modelIsEntity()) {
                // Las entidades se dibujan en un recuadro propio de la GUI, sin la matriz de la pista
                try {
                    drawEntity(g, track, ax, ay, h, alpha);
                } catch (RuntimeException e) {
                    // El render de un mob depende de Minecraft y de otros mods: si falla, solo se apaga ese modelo
                    models.disable(track.model(), true, e);
                }
                continue;
            }
            g.pose().pushMatrix();
            try {
                g.pose().translate(ax, ay);
                if (state.rotation != 0) {
                    g.pose().rotate((float) Math.toRadians(state.rotation));
                }
                switch (track.type()) {
                    case TEXT -> drawText(g, font, track, playback, active.notification(), w, unit, alpha);
                    case TEXTURE -> drawTexture(g, track, h, alpha);
                    case SHAPE -> drawShape(g, track, h, alpha);
                    case MODEL -> drawItem(g, track, h, alpha);
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                if (track.type() != TrackSpec.Type.MODEL) {
                    throw e;
                }
                models.disable(track.model(), false, e);
            } finally {
                g.pose().popMatrix();
            }
        }
    }

    /** Ítems y bloques: el dibujo de ítems de la GUI (16×16) escalado. No admite transparencia. */
    private void drawItem(GuiGraphicsExtractor g, TrackSpec track, int rectHeight, float alpha) {
        if (alpha < 0.35f) {
            return;
        }
        ItemStack stack = models.item(track.model());
        if (stack.isEmpty()) {
            return;
        }
        float s = track.width() * rectHeight / 16f * state.scale;
        if (s <= 0.01f) {
            return;
        }
        g.pose().scale(s, s);
        g.item(stack, -Math.round(16 * track.anchor().fx), -Math.round(16 * track.anchor().fy));
    }

    /** Mobs: el mismo dibujo que el inventario; la rotación de la pista hace que miren a los lados. */
    private void drawEntity(GuiGraphicsExtractor g, TrackSpec track, float ax, float ay, int rectHeight, float alpha) {
        if (alpha < 0.35f) {
            return;
        }
        LivingEntity entity = models.entity(track.model());
        if (entity == null) {
            return;
        }
        float boxH = (track.height() > 0 ? track.height() : track.width()) * rectHeight * state.scale;
        if (boxH < 4) {
            return;
        }
        float boxW = boxH * 0.75f;
        int x0 = Math.round(ax - boxW * track.anchor().fx);
        int y0 = Math.round(ay - boxH * track.anchor().fy);
        int x1 = Math.round(x0 + boxW);
        int y1 = Math.round(y0 + boxH);
        int scale = Math.max(1, Math.round(boxH * 0.8f / Math.max(0.3f, entity.getBbHeight())));
        double look = Math.toRadians(state.rotation);
        float mouseX = (float) (x0 + boxW / 2 + Math.sin(look) * boxW);
        float mouseY = y0 + boxH * 0.35f;
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, 0.0625f, mouseX, mouseY, entity);
    }

    private void drawText(GuiGraphicsExtractor g, Font font, TrackSpec track, Playback playback, Notification n,
            int rectWidth, float unit, float alpha) {
        float scale = track.textScale() * unit * state.scale;
        if (scale <= 0.01f) {
            return;
        }
        g.pose().scale(scale, scale);
        int wrap = Math.max(20, (int) (track.maxWidth() * rectWidth / scale));
        List<FormattedCharSequence> lines;
        if (state.reveal < 1) {
            String plain = component(track, n).getString();
            int chars = Math.round(plain.length() * state.reveal);
            lines = font.split(Component.literal(plain.substring(0, chars)), wrap);
        } else {
            TextCache cache = playback.texts.get(track.index());
            if (cache == null || cache.wrapWidth != wrap) {
                cache = new TextCache(wrap, List.copyOf(font.split(component(track, n), wrap)));
                playback.texts.put(track.index(), cache);
            }
            lines = cache.lines;
        }
        int color = NotifyHud.withAlpha(track.color(), alpha);
        int total = lines.size() * font.lineHeight;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            int lx = -Math.round(font.width(line) * track.anchor().fx);
            int ly = -Math.round(total * track.anchor().fy) + i * font.lineHeight;
            g.text(font, line, lx, ly, color, track.shadow());
        }
    }

    private static Component component(TrackSpec track, Notification n) {
        if (track.useMessage()) {
            return NotificationText.component(n.message(), n.args());
        }
        return NotificationText.component(track.content(), n.args());
    }

    private void drawTexture(GuiGraphicsExtractor g, TrackSpec track, int rectHeight, float alpha) {
        MediaTexture texture = media.get(track.file());
        if (texture == null) {
            return;
        }
        texture.update(state.localMs);
        FrameSequence frames = texture.frames();
        float tw = track.width() * rectHeight;
        float th = track.height() > 0 ? track.height() * rectHeight : tw * frames.height() / frames.width();
        g.pose().scale(state.scale, state.scale);
        int dx = -Math.round(tw * track.anchor().fx);
        int dy = -Math.round(th * track.anchor().fy);
        g.blit(RenderPipelines.GUI_TEXTURED, texture.id(), dx, dy, 0f, 0f, Math.round(tw), Math.round(th),
                frames.width(), frames.height(), frames.width(), frames.height(), NotifyHud.withAlpha(0xFFFFFF, alpha));
    }

    private void drawShape(GuiGraphicsExtractor g, TrackSpec track, int rectHeight, float alpha) {
        int sw = Math.round(track.width() * rectHeight);
        int sh = Math.round(track.height() * rectHeight);
        g.pose().scale(state.scale, state.scale);
        int x0 = -Math.round(sw * track.anchor().fx);
        int y0 = -Math.round(sh * track.anchor().fy);
        if (track.color2() == null || sh < 2) {
            g.fill(x0, y0, x0 + sw, y0 + sh, NotifyHud.withAlpha(track.color(), alpha));
            return;
        }
        // Degradado vertical en franjas (sin asignar memoria)
        int steps = Math.min(32, sh);
        for (int i = 0; i < steps; i++) {
            float f = steps == 1 ? 0 : i / (float) (steps - 1);
            int top = y0 + sh * i / steps;
            int bottom = y0 + sh * (i + 1) / steps;
            g.fill(x0, top, x0 + sw, bottom, NotifyHud.withAlpha(lerpColor(track.color(), track.color2(), f), alpha));
        }
    }

    private static void playSounds(Presentation p, Playback playback, long t, boolean docked) {
        for (TrackSpec track : p.tracks()) {
            if (track.type() != TrackSpec.Type.SOUND || playback.playedSounds.get(track.index()) || t < track.time()) {
                continue;
            }
            playback.playedSounds.set(track.index());
            if (docked && p.dockedMute() || t - track.time() > 1000) {
                continue; // en versión compacta silenciada, o demasiado tarde (p. ej. tras una pausa larga)
            }
            try {
                SoundEvent sound = SoundEvent.createVariableRangeEvent(Identifier.parse(track.sound()));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, track.pitch(),
                        track.volume()));
            } catch (RuntimeException e) {
                NotifyMod.LOGGER.warn("No se pudo reproducir {}: {}", track.sound(), e.toString());
            }
        }
    }

    private static Playback playback(ActiveNotification active) {
        if (active.renderCache instanceof Playback playback) {
            return playback;
        }
        Playback playback = new Playback();
        active.renderCache = playback;
        return playback;
    }

    static int lerpColor(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int gr = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return r << 16 | gr << 8 | bl;
    }
}

