package com.takumistudios.notifymod.core;

import java.util.*;

/** Bounded, monotonic, server-thread limiter. Rejected events do not consume cooldowns. */
public final class TriggerThrottle {
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> minutes = new HashMap<>();
    private final ArrayDeque<Long> global = new ArrayDeque<>();

    public boolean acquire(Trigger trigger, String player, long now) {
        String playerKey = trigger.id() + "|" + player;
        if (now < cooldowns.getOrDefault(trigger.id(), Long.MIN_VALUE)
                || now < cooldowns.getOrDefault(playerKey, Long.MIN_VALUE)) return false;
        ArrayDeque<Long> minute = minutes.computeIfAbsent(trigger.id(), k -> new ArrayDeque<>());
        prune(minute, now - 60_000);
        prune(global, now - 1000);
        if (minute.size() >= trigger.maxPerMinute() || global.size() >= 20) return false;
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        if (cooldowns.size() >= 4096) return false;
        if (trigger.cooldownMs() > 0) cooldowns.put(trigger.id(), now + trigger.cooldownMs());
        if (trigger.playerCooldownMs() > 0) cooldowns.put(playerKey, now + trigger.playerCooldownMs());
        minute.addLast(now);
        global.addLast(now);
        return true;
    }

    private static void prune(ArrayDeque<Long> deque, long cutoff) {
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) deque.removeFirst();
    }

    public void forget(String player) { cooldowns.keySet().removeIf(k -> k.endsWith("|" + player)); }
    public void clear() { cooldowns.clear(); minutes.clear(); global.clear(); }
}
