package com.takumistudios.notifymod;

import com.takumistudios.notifymod.core.Message;
import com.takumistudios.notifymod.core.NotificationArgs;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.NotificationStyle;
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

    /** Vanilla system message: colored bold heading, blank line and matching body. */
    public static MutableComponent chat(Notification n) {
        NotificationStyle s = n.style();
        MutableComponent title = Component.empty();
        if (s.showIcon()) title.append(Component.literal("● "));
        title.append(s.title().translate() && "notifymod.notice.title".equals(s.title().text())
                ? Component.translatableWithFallback("notifymod.notice.title", "Attention!")
                : component(s.title(), n.args()));
        title.withStyle(style -> style.withColor(s.chatColor()).withBold(true));
        return Component.empty().append(title).append(Component.literal("\n\n"))
                .append(component(n.message(), n.args()).withStyle(style -> style.withColor(s.chatColor())));
    }
}
