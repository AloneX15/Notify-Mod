package com.takumistudios.notifymod.server;

import com.takumistudios.notifymod.NotificationText;
import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.core.Channel;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.network.NotifyNetwork;
import java.util.Collection;
import java.util.EnumSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Envía una notificación a cada jugador por los canales que puede recibir (§4.2). El chat va como mensaje de sistema
 * vanilla (compatible con los mods de chat); las tarjetas y el Showcase, como paquete. Si el jugador no tiene el mod
 * (con {@code require_client=false}), recibe el texto por el chat como respaldo.
 */
public final class NotificationDispatcher {
    private NotificationDispatcher() {
    }

    /** @return a cuántos jugadores les llegó algo */
    public static int send(Collection<ServerPlayer> targets, Notification notification) {
        int delivered = 0;
        for (ServerPlayer player : targets) {
            try {
                if (sendTo(player, notification)) {
                    delivered++;
                }
            } catch (RuntimeException e) {
                NotifyMod.LOGGER.error("No se pudo enviar la notificación a {}", player.getScoreboardName(), e);
            }
        }
        return delivered;
    }

    private static boolean sendTo(ServerPlayer player, Notification n) {
        boolean chat = n.has(Channel.CHAT) && NotifyPermissions.canReceive(player, NotifyPermissions.RECEIVE_CHAT);
        EnumSet<Channel> visual = EnumSet.noneOf(Channel.class);
        if (n.has(Channel.HUD) && NotifyPermissions.canReceive(player, NotifyPermissions.RECEIVE_HUD)) {
            visual.add(Channel.HUD);
        }
        if (n.has(Channel.SHOWCASE) && NotifyPermissions.canReceive(player, NotifyPermissions.RECEIVE_SHOWCASE)) {
            visual.add(Channel.SHOWCASE);
        }
        boolean sent = false;
        if (!visual.isEmpty()) {
            if (ServerPlayNetworking.canSend(player, NotifyNetwork.ShowPayload.TYPE)) {
                ServerPlayNetworking.send(player, new NotifyNetwork.ShowPayload(new Notification(visual,
                        n.presentation(), n.message(), n.args(), n.priority(), n.placement(), n.key(), n.durationMs(), n.style())));
                sent = true;
            } else {
                chat = true; // respaldo para clientes sin el mod
            }
        }
        if (chat) {
            player.sendSystemMessage(NotificationText.chat(n));
            sent = true;
        }
        return sent;
    }

    /** Cancela por clave ("*" = todas) en los clientes indicados. */
    public static void cancel(Collection<ServerPlayer> targets, String key) {
        NotifyNetwork.CancelPayload payload = new NotifyNetwork.CancelPayload(key);
        for (ServerPlayer player : targets) {
            if (ServerPlayNetworking.canSend(player, NotifyNetwork.CancelPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}

