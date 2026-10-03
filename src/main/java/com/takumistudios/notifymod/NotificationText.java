package com.takumistudios.notifymod;

import com.takumistudios.notifymod.core.Message;
import com.takumistudios.notifymod.core.NotificationArgs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Convierte el texto de una notificación en un {@link Component}. Lo usan el servidor (chat) y el cliente (HUD). */
public final class NotificationText {
    private NotificationText() {
    }

    public static MutableComponent component(Message message, NotificationArgs args) {
        if (message.translate()) {
            return Component.translatable(message.text(), message.resolveWith(args).toArray());
        }
        return Component.literal(message.resolveLiteral(args));
    }

    /** Formato del canal de chat: un separador dorado y el mensaje. */
    public static MutableComponent chat(Message message, NotificationArgs args) {
        return Component.literal("\u00BB ").withStyle(ChatFormatting.GOLD).append(component(message, args));
    }
}

