package com.takumistudios.notifymod.network;

import com.takumistudios.notifymod.NotifyMod;
import com.takumistudios.notifymod.core.Channel;
import com.takumistudios.notifymod.core.Message;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.NotificationArgs;
import com.takumistudios.notifymod.core.Placement;
import com.takumistudios.notifymod.core.Priority;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Paquetes de Notify Mod (§17). La versión del protocolo va en el id del canal: un cliente con otra versión no
 * reconoce el canal y el servidor lo detecta igual que si no tuviera el mod.
 *
 * <p>Todo lo que se lee se valida y se limita: un servidor malicioso no puede colgar el cliente.
 */
public final class NotifyNetwork {
    public static final int PROTOCOL = 2;

    private NotifyNetwork() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(NotifyMod.MOD_ID, name + "_v" + PROTOCOL));
    }

    /** S2C: mostrar una notificación (tarjeta o Showcase). El chat va como mensaje de sistema vanilla. */
    public record ShowPayload(Notification notification) implements CustomPacketPayload {
        public static final Type<ShowPayload> TYPE = payloadType("show");
        public static final StreamCodec<FriendlyByteBuf, ShowPayload> CODEC = StreamCodec.of(
                (buf, payload) -> writeNotification(buf, payload.notification), buf -> new ShowPayload(readNotification(buf)));

        @Override
        public Type<ShowPayload> type() {
            return TYPE;
        }
    }

    /** S2C: cancelar las notificaciones con una clave, o todas si la clave es "*". */
    public record CancelPayload(String key) implements CustomPacketPayload {
        public static final String ALL = "*";
        public static final Type<CancelPayload> TYPE = payloadType("cancel");
        public static final StreamCodec<FriendlyByteBuf, CancelPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeUtf(payload.key, Notification.MAX_KEY),
                buf -> new CancelPayload(buf.readUtf(Notification.MAX_KEY)));

        @Override
        public Type<CancelPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ShowPayload.TYPE, ShowPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CancelPayload.TYPE, CancelPayload.CODEC);
    }

    // ------------------------------------------------------------ códec de Notification

    static void writeNotification(FriendlyByteBuf buf, Notification n) {
        int mask = 0;
        for (Channel c : n.channels()) {
            mask |= 1 << c.ordinal();
        }
        buf.writeVarInt(mask);
        buf.writeUtf(n.presentation(), 200);
        Message m = n.message();
        buf.writeBoolean(m.translate());
        buf.writeUtf(m.text(), Message.MAX_TEXT);
        buf.writeVarInt(m.with().size());
        for (String w : m.with()) {
            buf.writeUtf(w, NotificationArgs.MAX_VALUE_LENGTH);
        }
        buf.writeVarInt(n.args().size());
        for (Map.Entry<String, String> e : n.args().values().entrySet()) {
            buf.writeUtf(e.getKey(), 32);
            buf.writeUtf(e.getValue(), NotificationArgs.MAX_VALUE_LENGTH);
        }
        buf.writeVarInt(n.priority().ordinal());
        buf.writeVarInt(n.placement().ordinal());
        buf.writeBoolean(n.key() != null);
        if (n.key() != null) {
            buf.writeUtf(n.key(), Notification.MAX_KEY);
        }
        buf.writeVarInt(n.durationMs());
        buf.writeUtf(n.style().toJson().toString(), 8192);
    }

    static Notification readNotification(FriendlyByteBuf buf) {
        int mask = buf.readVarInt();
        EnumSet<Channel> channels = EnumSet.noneOf(Channel.class);
        for (Channel c : Channel.values()) {
            if ((mask & 1 << c.ordinal()) != 0) {
                channels.add(c);
            }
        }
        String presentation = buf.readUtf(200);
        boolean translate = buf.readBoolean();
        String text = buf.readUtf(Message.MAX_TEXT);
        int withCount = limit(buf.readVarInt(), Message.MAX_WITH, "with");
        List<String> with = new ArrayList<>(withCount);
        for (int i = 0; i < withCount; i++) {
            with.add(buf.readUtf(NotificationArgs.MAX_VALUE_LENGTH));
        }
        int argCount = limit(buf.readVarInt(), NotificationArgs.MAX_ARGS, "argumentos");
        Map<String, String> args = new LinkedHashMap<>();
        for (int i = 0; i < argCount; i++) {
            args.put(buf.readUtf(32), buf.readUtf(NotificationArgs.MAX_VALUE_LENGTH));
        }
        Priority priority = enumAt(Priority.values(), buf.readVarInt());
        Placement placement = enumAt(Placement.values(), buf.readVarInt());
        String key = buf.readBoolean() ? buf.readUtf(Notification.MAX_KEY) : null;
        int duration = buf.readVarInt();
        var style = com.takumistudios.notifymod.core.NotificationStyle.parse(
                com.google.gson.JsonParser.parseString(buf.readUtf(8192)).getAsJsonObject());
        // El constructor valida el resto (ids, rangos, longitudes)
        return new Notification(channels, presentation, new Message(text, translate, with), NotificationArgs.of(args),
                priority, placement, key, duration, style);
    }

    private static int limit(int count, int max, String what) {
        if (count < 0 || count > max) {
            throw new IllegalArgumentException("Paquete de Notify Mod con demasiados " + what);
        }
        return count;
    }

    private static <E extends Enum<E>> E enumAt(E[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Valor fuera de rango en un paquete de Notify Mod");
        }
        return values[ordinal];
    }
}

