package com.takumistudios.notifymod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Cola de notificaciones pendientes (§11 del plan). No es segura entre hilos: se usa siempre desde el mismo hilo (el
 * de render en el cliente o el del servidor).
 *
 * <ul>
 *   <li>Sale primero la prioridad más alta; dentro de la misma, por orden de llegada.</li>
 *   <li>Cada entrada caduca según su prioridad (o un TTL propio) y se descarta sin mostrarse.</li>
 *   <li>Deduplicación por {@code key}: la nueva sustituye a la que espera con la misma clave.</li>
 *   <li>Con la cola llena, una entrada nueva solo entra si expulsa a otra de prioridad estrictamente menor.</li>
 * </ul>
 *
 * @param <T> lo que se encola (en el cliente, la notificación recibida)
 */
public final class NotificationQueue<T> {
    public enum OfferResult {
        ADDED,
        REPLACED,
        EVICTED_OTHER,
        REJECTED_FULL
    }

    private record Entry<T>(T value, Priority priority, String key, long expiresAt, long seq) {
    }

    private final int capacity;
    private final LongSupplier clock;
    private final List<Entry<T>> entries = new ArrayList<>();
    private long nextSeq;

    /** @param clock milisegundos monotónicos (en el juego, {@code Util.getMillis()}; en tests, un reloj falso) */
    public NotificationQueue(int capacity, LongSupplier clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("La capacidad debe ser al menos 1");
        }
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock);
    }

    public OfferResult offer(T value, Priority priority, String key) {
        return offer(value, priority, key, priority.defaultTtlMs());
    }

    public OfferResult offer(T value, Priority priority, String key, long ttlMs) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(priority);
        long now = clock.getAsLong();
        purgeExpired(now);
        long expiresAt = ttlMs >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + Math.max(0, ttlMs);
        Entry<T> entry = new Entry<>(value, priority, key, expiresAt, nextSeq++);

        if (key != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (key.equals(entries.get(i).key)) {
                    // Conserva el turno de la antigua si la prioridad no cambia; si cambia, entra como nueva
                    Entry<T> old = entries.get(i);
                    entries.set(i, old.priority == priority
                            ? new Entry<>(value, priority, key, expiresAt, old.seq) : entry);
                    return OfferResult.REPLACED;
                }
            }
        }
        if (entries.size() < capacity) {
            entries.add(entry);
            return OfferResult.ADDED;
        }
        int victim = weakest();
        if (entries.get(victim).priority.compareTo(priority) < 0) {
            entries.set(victim, entry);
            return OfferResult.EVICTED_OTHER;
        }
        return OfferResult.REJECTED_FULL;
    }

    /** Saca la siguiente notificación que toca mostrar, o vacío si no hay ninguna vigente. */
    public Optional<T> poll() {
        purgeExpired(clock.getAsLong());
        int best = strongest();
        return best < 0 ? Optional.empty() : Optional.of(entries.remove(best).value);
    }

    /** Prioridad de la siguiente sin sacarla (para decidir si interrumpe lo que se está mostrando). */
    public Optional<Priority> peekPriority() {
        purgeExpired(clock.getAsLong());
        int best = strongest();
        return best < 0 ? Optional.empty() : Optional.of(entries.get(best).priority);
    }

    public boolean removeKey(String key) {
        return key != null && entries.removeIf(e -> key.equals(e.key));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        purgeExpired(clock.getAsLong());
        return entries.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    private void purgeExpired(long now) {
        entries.removeIf(e -> e.expiresAt != Long.MAX_VALUE && e.expiresAt <= now);
    }

    private int strongest() {
        int best = -1;
        for (int i = 0; i < entries.size(); i++) {
            Entry<T> e = entries.get(i);
            if (best < 0) {
                best = i;
                continue;
            }
            Entry<T> b = entries.get(best);
            int cmp = e.priority.compareTo(b.priority);
            if (cmp > 0 || cmp == 0 && e.seq < b.seq) {
                best = i;
            }
        }
        return best;
    }

    /** La de menor prioridad y, entre iguales, la más reciente (la que menos ha esperado). */
    private int weakest() {
        int worst = 0;
        for (int i = 1; i < entries.size(); i++) {
            Entry<T> e = entries.get(i);
            Entry<T> w = entries.get(worst);
            int cmp = e.priority.compareTo(w.priority);
            if (cmp < 0 || cmp == 0 && e.seq > w.seq) {
                worst = i;
            }
        }
        return worst;
    }
}

