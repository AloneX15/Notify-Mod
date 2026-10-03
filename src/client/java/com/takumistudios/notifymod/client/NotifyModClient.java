package com.takumistudios.notifymod.client;

import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.client.hud.NotifyHud;
import com.takumistudios.notifymod.client.hud.PresentationRenderer;
import com.takumistudios.notifymod.client.media.MediaCache;
import com.takumistudios.notifymod.client.media.MediaQuality;
import com.takumistudios.notifymod.client.state.ClientNotificationState;
import com.takumistudios.notifymod.client.timeline.PausableClock;
import com.takumistudios.notifymod.client.timeline.PresentationRegistry;
import com.takumistudios.notifymod.config.ClientConfig;
import com.takumistudios.notifymod.config.JsonConfigFile;
import com.takumistudios.notifymod.network.NotifyNetwork;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/** Punto de entrada del cliente: red, estado de las notificaciones, capa del HUD y la tecla de ocultar esquinas. */
public final class NotifyModClient implements ClientModInitializer {
    /** Uses the platform unknown key (GLFW -1, SDL 0). */
    private static final int UNBOUND = com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue();

    private static Path configFile;
    private static volatile ClientConfig config = ClientConfig.DEFAULTS;
    private static ClientNotificationState state;
    private static KeyMapping hideCornersKey;
    /** Presupuesto de memoria de las texturas de las presentaciones (§9.1). */
    private static final long MEDIA_BUDGET = 128L << 20;
    private static final PausableClock clock = new PausableClock();
    private static final MediaCache media = new MediaCache(MEDIA_BUDGET, task -> Util.backgroundExecutor().execute(task),
            () -> MediaQuality.parse(config.mediaQuality()));
    private static final PresentationRegistry presentations = new PresentationRegistry(media);
    private static NotifyHud hud;

    public static PresentationRegistry presentations() {
        return presentations;
    }

    public static MediaCache media() {
        return media;
    }

    /** La capa del HUD (para los tests: comprobar que no se ha desactivado por un fallo). */
    public static NotifyHud hud() {
        return hud;
    }

    public static ClientNotificationState state() {
        return state;
    }

    public static ClientConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        try {
            configFile = FabricLoader.getInstance().getConfigDir().resolve(NotifyMod.MOD_ID).resolve("client.json");
            config = ClientConfig.load(configFile);
            clock.update(Util.getMillis(), false);
            state = new ClientNotificationState(clock::now, new ClientNotificationState.Options() {
                @Override
                public boolean hideCornerNotifications() {
                    return config.hideCornerNotifications();
                }

                @Override
                public int maxVisibleCards() {
                    return config.maxVisibleCards();
                }

                @Override
                public int durationFor(com.takumistudios.notifymod.core.Notification notification) {
                    var presentation = presentations.get(notification.presentation());
                    return presentation != null ? presentation.durationMs() : notification.durationMs();
                }
            });
            ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                    Identifier.fromNamespaceAndPath(NotifyMod.MOD_ID, "presentations"), presentations);

            ClientPlayNetworking.registerGlobalReceiver(NotifyNetwork.ShowPayload.TYPE,
                    (payload, context) -> safe("recibir una notificación", () -> {
                        state.receive(payload.notification());
                        // Empieza a decodificar sus imágenes ya, mientras espera su turno en la cola
                        var presentation = presentations.get(payload.notification().presentation());
                        if (presentation != null) {
                            media.prefetch(presentation.textureFiles());
                        }
                    }));
            ClientPlayNetworking.registerGlobalReceiver(NotifyNetwork.CancelPayload.TYPE,
                    (payload, context) -> safe("cancelar", () -> state.cancel(payload.key())));
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> safe("limpiar", state::reset));

            HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                    Identifier.fromNamespaceAndPath(NotifyMod.MOD_ID, "notifications"),
                    hud = new NotifyHud(state, NotifyModClient::config, presentations, new PresentationRenderer(media),
                            clock));

            KeyMapping.Category category = KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(NotifyMod.MOD_ID, "main"));
            hideCornersKey = KeyMappingHelper.registerKeyMapping(
                    new KeyMapping("key.notifymod.hide_corners", UNBOUND, category));
            if ("key.keyboard.-1".equals(hideCornersKey.saveString())) {
                hideCornersKey.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            }
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                while (hideCornersKey.consumeClick()) {
                    safe("cambiar la opción", () -> toggleHideCorners(client));
                }
            });
        } catch (RuntimeException e) {
            NotifyMod.LOGGER.error("Notify Mod (cliente) no se pudo iniciar del todo", e);
        }
    }

    /** El único interruptor del jugador (§6.4): ocultar los avisos de esquina. El centro nunca se oculta. */
    public static KeyMapping hideCornersKey() { return hideCornersKey; }

    public static Component hideCornersHint() {
        return hideCornersKey == null || hideCornersKey.isUnbound()
                ? Component.translatable("notifymod.notice.hide_unbound")
                : Component.translatable("notifymod.notice.hide_hint", hideCornersKey.getTranslatedKeyMessage());
    }

    public static void toggleHideCorners(Minecraft client) {
        config = config.withHideCornerNotifications(!config.hideCornerNotifications());
        JsonConfigFile.saveAsync(configFile, ClientConfig.write(config), task -> Util.ioPool().execute(task));
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(config.hideCornerNotifications()
                    ? "notifymod.hide_corners.on" : "notifymod.hide_corners.off"));
        }
    }

    private static void safe(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            NotifyMod.LOGGER.error("Notify Mod: error al {}", what, e);
        }
    }
}
