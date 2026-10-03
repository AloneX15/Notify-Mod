package com.takumistudios.notifymod.server;

import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.config.ServerConfig;
import com.takumistudios.notifymod.core.RateLimiter;
import com.takumistudios.notifymod.network.NotifyNetwork;
import java.nio.file.Path;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Estado del servidor de Notify Mod: configuración, plantillas, límites y eventos. */
public final class NotifyServer {
    private static final NotifyServer INSTANCE = new NotifyServer();

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve(NotifyMod.MOD_ID);
    private final TemplateRegistry templates = new TemplateRegistry();
    private final RateLimiter limiter = new RateLimiter(60_000);
    private volatile ServerConfig config = ServerConfig.DEFAULTS;

    private NotifyServer() {
    }

    public static NotifyServer get() {
        return INSTANCE;
    }

    public ServerConfig config() {
        return config;
    }

    public TemplateRegistry templates() {
        return templates;
    }

    public RateLimiter limiter() {
        return limiter;
    }

    public void init() {
        config = ServerConfig.load(configDir.resolve("server.json"));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            try {
                NotifyCommand.register(dispatcher);
            } catch (RuntimeException e) {
                NotifyMod.LOGGER.error("No se pudo registrar /notify", e);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> safe("cargar plantillas", () -> reloadTemplates(server)));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) {
                safe("recargar plantillas", () -> reloadTemplates(server));
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                safe("comprobar el cliente", () -> checkClient(handler.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                safe("limpiar límites", () -> limiter.forget(handler.player.getStringUUID())));
    }

    /** {@code /notify reload}: vuelve a leer server.json y las plantillas. */
    public void reload(MinecraftServer server) {
        config = ServerConfig.load(configDir.resolve("server.json"));
        reloadTemplates(server);
    }

    private void reloadTemplates(MinecraftServer server) {
        templates.reload(server.getResourceManager(), configDir.resolve("managed").resolve("templates"));
    }

    /** Cliente obligatorio (§17.2): sin el mod, o con otra versión del protocolo, no se puede entrar. */
    private void checkClient(ServerPlayer player) {
        if (!config.requireClient() || ServerPlayNetworking.canSend(player, NotifyNetwork.ShowPayload.TYPE)) {
            return;
        }
        String custom = config.missingClientMessage();
        String text = custom.isBlank()
                ? "Este servidor necesita Notify Mod (protocolo v" + NotifyNetwork.PROTOCOL + ").\n"
                        + "This server requires Notify Mod (protocol v" + NotifyNetwork.PROTOCOL + ").\n\n"
                        + config.downloadUrl()
                : custom;
        NotifyMod.LOGGER.info("{} no tiene Notify Mod (o tiene otra versión): se le desconecta",
                player.getScoreboardName());
        player.connection.disconnect(Component.literal(text));
    }

    private static void safe(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            NotifyMod.LOGGER.error("Notify Mod: error al {}", what, e);
        }
    }
}

