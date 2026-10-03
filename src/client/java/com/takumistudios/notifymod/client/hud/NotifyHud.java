package com.takumistudios.notifymod.client.hud;

import com.takumistudios.notifymod.NotificationText;
import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.client.state.ActiveNotification;
import com.takumistudios.notifymod.client.state.ClientNotificationState;
import com.takumistudios.notifymod.client.timeline.PausableClock;
import com.takumistudios.notifymod.client.timeline.Presentation;
import com.takumistudios.notifymod.client.timeline.PresentationRegistry;
import com.takumistudios.notifymod.config.ClientConfig;
import com.takumistudios.notifymod.core.Channel;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.Placement;
import com.takumistudios.notifymod.core.Priority;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * Capa del HUD de Notify Mod (§10): el Showcase de texto ({@code notifymod:motd}) en el centro y las tarjetas en las
 * esquinas. El texto se compone una vez por notificación y se guarda en {@link ActiveNotification#renderCache}; en
 * cada fotograma solo se dibuja.
 */
public final class NotifyHud implements HudElement {
    static final int PADDING = 5;

    private final ClientNotificationState state;
    private final Supplier<ClientConfig> config;
    private final PresentationRegistry presentations;
    private final PresentationRenderer renderer;
    private final PausableClock clock;
    private final Set<String> warnedPresentations = new HashSet<>();
    private boolean failed;

    private record Layout(List<FormattedCharSequence> lines, int width, int height, float scale) {
    }

    public NotifyHud(ClientNotificationState state, Supplier<ClientConfig> config, PresentationRegistry presentations,
            PresentationRenderer renderer, PausableClock clock) {
        this.state = state;
        this.config = config;
        this.presentations = presentations;
        this.renderer = renderer;
        this.clock = clock;
    }

    /** {@code true} si la capa falló y quedó desactivada hasta reiniciar. */
    public boolean failed() {
        return failed;
    }

    public PresentationRenderer renderer() {
        return renderer;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (failed) {
            return;
        }
        try {
            Minecraft client = Minecraft.getInstance();
            // El reloj de las presentaciones se para con la pausa del juego (§8.2)
            clock.update(Util.getMillis(), client.isPaused());
            state.tick(com.takumistudios.notifymod.client.ClientCompat.screenOpen(client));
            long now = clock.now();
            Font font = client.font;
            ActiveNotification center = state.center();
            if (center != null) {
                Presentation presentation = presentations.get(center.notification().presentation());
                if (presentation != null) {
                    renderer.draw(graphics, font, presentation, center, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                            false, now);
                } else {
                    drawCenter(graphics, font, center, now);
                }
            }
            float scale = (float) config.get().cardScale();
            graphics.pose().pushMatrix();
            graphics.pose().scale(scale, scale);
            int width = (int) (graphics.guiWidth() / scale);
            int height = (int) (graphics.guiHeight() / scale);
            for (Placement placement : Placement.values()) {
                if (!placement.isDocked()) {
                    continue;
                }
                int offset = 0;
                for (ActiveNotification card : state.corner(placement)) {
                    offset += drawCard(graphics, font, card, placement, width, height, offset, now) + HudLayout.GAP;
                }
            }
            graphics.pose().popMatrix();
        } catch (RuntimeException e) {
            // Norma TakumiStudios: un fallo desactiva solo esta función, nunca tumba el juego
            failed = true;
            NotifyMod.LOGGER.error("La capa de notificaciones ha fallado y se desactiva hasta reiniciar", e);
        }
    }

    private void drawCenter(GuiGraphicsExtractor g, Font font, ActiveNotification active, long now) {
        float a = active.alpha(now);
        if (a <= 0.02f) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        Layout layout = centerLayout(font, active, w);
        int lineHeight = Math.round(font.lineHeight * layout.scale);
        int blockHeight = layout.lines.size() * lineHeight;
        int y = (int) (h * 0.28f) - blockHeight / 2;
        int accent = withAlpha(priorityColor(active.notification().priority()), a);
        g.fill(0, y - 9, w, y + blockHeight + 7, withAlpha(0x000000, a * 0.55f));
        g.fill(0, y - 10, w, y - 9, accent);
        g.fill(0, y + blockHeight + 7, w, y + blockHeight + 8, accent);
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, y);
        g.pose().scale(layout.scale, layout.scale);
        int color = withAlpha(0xFFFFFF, a);
        for (int i = 0; i < layout.lines.size(); i++) {
            g.centeredText(font, layout.lines.get(i), 0, i * font.lineHeight, color);
        }
        g.pose().popMatrix();
    }

    private Layout centerLayout(Font font, ActiveNotification active, int screenWidth) {
        if (active.renderCache instanceof Layout cached) {
            return cached;
        }
        Component text = text(active.notification());
        int maxWidth = (int) (screenWidth * 0.8f);
        float scale = 2f;
        List<FormattedCharSequence> lines = font.split(text, (int) (maxWidth / scale));
        if (lines.size() > 3) {
            scale = 1f;
            lines = font.split(text, maxWidth);
        }
        Layout layout = new Layout(List.copyOf(lines), maxWidth, lines.size() * font.lineHeight, scale);
        active.renderCache = layout;
        return layout;
    }

    /** @return la altura de la tarjeta */
    private int drawCard(GuiGraphicsExtractor g, Font font, ActiveNotification active, Placement placement, int screenW,
            int screenH, int offset, long now) {
        if (active.notification().has(Channel.SHOWCASE)) {
            Presentation presentation = presentations.get(active.notification().presentation());
            if (presentation != null) {
                // Versión compacta del Showcase (§6.3): recuadro 16:9 en la esquina
                int w = Math.round(screenW * PresentationRenderer.DOCKED_WIDTH);
                int h = w * 9 / 16;
                int[] origin = HudLayout.cardOrigin(placement, screenW, screenH, w, h, offset);
                renderer.draw(g, font, presentation, active, origin[0], origin[1], w, h, true, now);
                return h;
            }
        }
        CardLayout layout = cardLayout(font, active, screenW, screenH);
        float a = active.alpha(now);
        if (a <= 0.02f) return layout.height;
        int[] origin = HudLayout.cardOrigin(placement, screenW, screenH, layout.width, layout.height, offset);
        int x = origin[0] + Math.round((1 - a) * 16 * HudLayout.slideDirection(placement));
        int y = origin[1];
        var theme = active.notification().style();
        g.fill(x, y, x + layout.width, y + layout.height,
                withAlpha(theme.backgroundColor(), a * (float)theme.backgroundOpacity()));
        int titleX = x + PADDING;
        if (theme.showIcon()) {
            drawNoticeIcon(g, titleX, y + PADDING, withAlpha(theme.titleColor(), a));
            titleX += 13;
        }
        int rowY = y + PADDING;
        for (var line : layout.title) {
            g.text(font, line, titleX, rowY, withAlpha(theme.titleColor(), a), theme.shadow());
            rowY += font.lineHeight;
        }
        rowY += 4;
        for (var line : layout.body) {
            g.text(font, line, x + PADDING, rowY, withAlpha(theme.textColor(), a), theme.shadow());
            rowY += font.lineHeight;
        }
        if (!layout.hint.isEmpty()) {
            rowY += 12;
            for (var line : layout.hint) {
                g.text(font, line, x + PADDING, rowY, withAlpha(theme.hintColor(), a), theme.shadow());
                rowY += font.lineHeight;
            }
        }
        return layout.height;
    }

    private record CardLayout(List<FormattedCharSequence> title, List<FormattedCharSequence> body,
            List<FormattedCharSequence> hint, int width, int height, int screenW, int screenH, String hintKey) {}

    private CardLayout cardLayout(Font font, ActiveNotification active, int screenW, int screenH) {
        String hintKey = com.takumistudios.notifymod.client.NotifyModClient.hideCornersKey().saveString();
        if (active.renderCache instanceof CardLayout c && c.screenW == screenW && c.screenH == screenH
                && c.hintKey.equals(hintKey)) return c;
        var theme = active.notification().style();
        int width = Math.max(40, Math.min(theme.width(), screenW - HudLayout.GAP * 2));
        int textWidth = Math.max(20, width - PADDING * 2);
        var title = limitedLines(font.split(NotificationText.component(theme.title(), active.notification().args())
                .withStyle(style -> style.withBold(true)), Math.max(20, textWidth - (theme.showIcon() ? 13 : 0))), 2);
        var hint = theme.showHideHint() ? limitedLines(font.split(
                com.takumistudios.notifymod.client.NotifyModClient.hideCornersHint(), textWidth), 2)
                : List.<FormattedCharSequence>of();
        int fixedHeight = PADDING * 2 + title.size() * font.lineHeight + 4
                + (hint.isEmpty() ? 0 : 12 + hint.size() * font.lineHeight);
        var all = font.split(text(active.notification()), textWidth);
        int maxLines = Math.max(1, (screenH - HudLayout.GAP * 2 - fixedHeight) / font.lineHeight);
        var body = List.copyOf(all.subList(0, Math.min(all.size(), maxLines)));
        if (all.size() > maxLines) {
            var clipped = new java.util.ArrayList<>(body);
            clipped.set(clipped.size() - 1, Component.literal("…").getVisualOrderText());
            body = List.copyOf(clipped);
        }
        var layout = new CardLayout(title, body, hint, width, fixedHeight + body.size() * font.lineHeight, screenW, screenH, hintKey);
        active.renderCache = layout;
        return layout;
    }

    private static List<FormattedCharSequence> limitedLines(List<FormattedCharSequence> lines, int max) {
        if (lines.size() <= max) return List.copyOf(lines);
        var clipped = new java.util.ArrayList<>(lines.subList(0, max));
        clipped.set(max - 1, Component.literal("…").getVisualOrderText());
        return List.copyOf(clipped);
    }

    /** Small pixel ring and exclamation mark, drawn without an external texture. */
    private static void drawNoticeIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 3, y, x + 7, y + 1, color);
        g.fill(x + 3, y + 9, x + 7, y + 10, color);
        g.fill(x, y + 3, x + 1, y + 7, color);
        g.fill(x + 9, y + 3, x + 10, y + 7, color);
        g.fill(x + 1, y + 1, x + 3, y + 3, color);
        g.fill(x + 7, y + 1, x + 9, y + 3, color);
        g.fill(x + 1, y + 7, x + 3, y + 9, color);
        g.fill(x + 7, y + 7, x + 9, y + 9, color);
        g.fill(x + 4, y + 2, x + 6, y + 6, color);
        g.fill(x + 4, y + 7, x + 6, y + 8, color);
    }

    private Component text(Notification n) {
        if (!Notification.MOTD.equals(n.presentation()) && presentations.get(n.presentation()) == null
                && warnedPresentations.add(n.presentation())) {
            NotifyMod.LOGGER.info("Presentación desconocida {}: se muestra como texto", n.presentation());
        }
        return NotificationText.component(n.message(), n.args());
    }

    static int priorityColor(Priority priority) {
        return switch (priority) {
            case LOW -> 0x9A9A9A;
            case NORMAL -> 0x55C8FF;
            case HIGH -> 0xFFAA00;
            case CRITICAL -> 0xFF5555;
        };
    }

    static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb & 0xFFFFFF;
    }
}


