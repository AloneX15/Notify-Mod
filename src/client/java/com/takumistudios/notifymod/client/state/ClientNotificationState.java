package com.takumistudios.notifymod.client.state;

import com.takumistudios.notifymod.core.Channel;
import com.takumistudios.notifymod.core.Notification;
import com.takumistudios.notifymod.core.NotificationQueue;
import com.takumistudios.notifymod.core.Placement;
import com.takumistudios.notifymod.core.Priority;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Estado del cliente (§11): un Showcase activo en el centro con su cola, y una pila de tarjetas por esquina con la
 * suya. Sin dependencias de Minecraft para poder probarlo; se usa siempre desde el hilo del cliente.
 *
 * <ul>
 *   <li>HIGH acorta la salida de lo que hay en el centro (300 ms); CRITICAL lo interrumpe (150 ms).</li>
 *   <li>Con una pantalla abierta, el centro espera, salvo CRITICAL.</li>
 *   <li>Si el jugador oculta los avisos de esquina, esos se descartan sin mostrarse (quedan en el historial).</li>
 * </ul>
 */
public final class ClientNotificationState {
    public static final long HIGH_FADE_MS = 300;
    public static final long CRITICAL_FADE_MS = 150;
    public static final int QUEUE_CAPACITY = 32;
    public static final int HISTORY_SIZE = 50;

    public interface Options {
        boolean hideCornerNotifications();

        int maxVisibleCards();

        /** Duración en pantalla. Por defecto la que manda el servidor; una presentación conocida usa la suya. */
        default int durationFor(Notification notification) {
            return notification.durationMs();
        }
    }

    public record HistoryEntry(Notification notification, long receivedAt, boolean hidden) {
    }

    private final LongSupplier clock;
    private final Options options;
    private final NotificationQueue<Notification> centerQueue;
    private ActiveNotification center;
    private final Map<Placement, NotificationQueue<Notification>> cornerQueues = new EnumMap<>(Placement.class);
    private final Map<Placement, List<ActiveNotification>> corners = new EnumMap<>(Placement.class);
    private final Deque<HistoryEntry> history = new ArrayDeque<>();

    public ClientNotificationState(LongSupplier clock, Options options) {
        this.clock = clock;
        this.options = options;
        this.centerQueue = new NotificationQueue<>(QUEUE_CAPACITY, clock);
        for (Placement p : Placement.values()) {
            if (p.isDocked()) {
                cornerQueues.put(p, new NotificationQueue<>(QUEUE_CAPACITY, clock));
                corners.put(p, new ArrayList<>());
            }
        }
    }

    /** Dónde se dibuja: el Showcase respeta la colocación; una tarjeta nunca va al centro (sube arriba). */
    public static Placement placementFor(Notification n) {
        if (n.has(Channel.SHOWCASE)) {
            return n.placement();
        }
        return n.placement() == Placement.CENTER ? Placement.TOP : n.placement();
    }

    public void receive(Notification n) {
        long now = clock.getAsLong();
        Placement placement = placementFor(n);
        boolean hidden = placement.isDocked() && n.priority() != Priority.CRITICAL
                && options.hideCornerNotifications();
        remember(new HistoryEntry(n, now, hidden));
        if (hidden || !(n.has(Channel.SHOWCASE) || n.has(Channel.HUD))) {
            return;
        }
        if (placement == Placement.CENTER) {
            if (center != null && !center.finished(now)) {
                boolean sameKey = n.key() != null && n.key().equals(center.notification().key());
                if (n.priority() == Priority.CRITICAL || sameKey) {
                    center.endEarly(now, CRITICAL_FADE_MS);
                } else if (n.priority().compareTo(center.notification().priority()) > 0) {
                    center.endEarly(now, HIGH_FADE_MS);
                }
            }
            centerQueue.offer(n, n.priority(), n.key());
            return;
        }
        List<ActiveNotification> stack = corners.get(placement);
        if (n.key() != null) {
            for (int i = 0; i < stack.size(); i++) {
                if (n.key().equals(stack.get(i).notification().key())) {
                    stack.set(i, active(n, placement, now));
                    return;
                }
            }
        }
        cornerQueues.get(placement).offer(n, n.priority(), n.key());
        fillCorner(placement, now);
    }

    /** Avanza el estado: retira lo terminado y saca de las colas lo que toca. Llamar en cada fotograma. */
    public void tick(boolean screenOpen) {
        long now = clock.getAsLong();
        if (center != null && center.finished(now)) {
            center = null;
        }
        if (center == null) {
            Priority next = centerQueue.peekPriority().orElse(null);
            if (next != null && (!screenOpen || next == Priority.CRITICAL)) {
                center = active(centerQueue.poll().orElseThrow(), Placement.CENTER, now);
            }
        }
        for (Placement p : corners.keySet()) {
            corners.get(p).removeIf(a -> a.finished(now));
            fillCorner(p, now);
        }
    }

    private void fillCorner(Placement p, long now) {
        List<ActiveNotification> stack = corners.get(p);
        NotificationQueue<Notification> queue = cornerQueues.get(p);
        while (stack.size() < options.maxVisibleCards()) {
            Notification next = queue.poll().orElse(null);
            if (next == null) {
                break;
            }
            stack.add(active(next, p, now));
        }
    }

    /** Cancela por clave; {@code "*"} lo vacía todo. Lo que está en pantalla sale con un fundido corto. */
    public void cancel(String key) {
        long now = clock.getAsLong();
        boolean all = "*".equals(key);
        if (all) {
            centerQueue.clear();
            cornerQueues.values().forEach(NotificationQueue::clear);
        } else {
            centerQueue.removeKey(key);
            cornerQueues.values().forEach(q -> q.removeKey(key));
        }
        if (center != null && (all || key.equals(center.notification().key()))) {
            center.endEarly(now, CRITICAL_FADE_MS);
        }
        for (List<ActiveNotification> stack : corners.values()) {
            for (ActiveNotification a : stack) {
                if (all || key.equals(a.notification().key())) {
                    a.endEarly(now, CRITICAL_FADE_MS);
                }
            }
        }
    }

    /** Al desconectarse: nada de un servidor puede quedarse en pantalla en otro. */
    public void reset() {
        centerQueue.clear();
        cornerQueues.values().forEach(NotificationQueue::clear);
        center = null;
        corners.values().forEach(List::clear);
    }

    private ActiveNotification active(Notification n, Placement placement, long now) {
        return new ActiveNotification(n, placement, now, options.durationFor(n));
    }

    private void remember(HistoryEntry entry) {
        history.addFirst(entry);
        while (history.size() > HISTORY_SIZE) {
            history.removeLast();
        }
    }

    public ActiveNotification center() {
        return center;
    }

    public List<ActiveNotification> corner(Placement placement) {
        List<ActiveNotification> stack = corners.get(placement);
        return stack == null ? List.of() : Collections.unmodifiableList(stack);
    }

    public List<HistoryEntry> history() {
        return List.copyOf(history);
    }

    public int pendingCenter() {
        return centerQueue.size();
    }
}

