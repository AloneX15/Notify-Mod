package com.takumistudios.notifymod.core;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Una notificación lista para enviar: lo que el servidor manda al cliente (§17). No lleva imágenes ni cálculos, solo
 * ids, texto, argumentos y cómo mostrarlo. Se valida al construirla, tanto en el servidor como al recibirla.
 */
public record Notification(Set<Channel> channels, String presentation, Message message, NotificationArgs args,
        Priority priority, Placement placement, String key, int durationMs) {
    public static final String MOTD = "notifymod:motd";
    public static final int MIN_DURATION_MS = 500;
    public static final int MAX_DURATION_MS = 120_000;
    public static final int DEFAULT_HUD_MS = 6_000;
    public static final int DEFAULT_SHOWCASE_MS = 7_000;
    public static final int MAX_KEY = 64;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]{1," + MAX_KEY + "}");

    public Notification {
        Objects.requireNonNull(channels, "channels");
        if (channels.isEmpty()) {
            throw new IllegalArgumentException("La notificación no tiene ningún canal");
        }
        channels = Set.copyOf(EnumSet.copyOf(channels));
        presentation = presentation == null ? MOTD : presentation;
        if (!ID.matcher(presentation).matches()) {
            throw new IllegalArgumentException("Id de presentación no válido: " + presentation);
        }
        Objects.requireNonNull(message, "message");
        args = args == null ? NotificationArgs.EMPTY : args;
        priority = priority == null ? Priority.NORMAL : priority;
        placement = Placement.effective(placement, priority);
        if (key != null && !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Clave no válida: " + key + " (letras, números y _ . : -; máximo "
                    + MAX_KEY + ")");
        }
        if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
            throw new IllegalArgumentException("Duración fuera de rango (" + Durations.format(MIN_DURATION_MS) + " a "
                    + Durations.format(MAX_DURATION_MS) + ")");
        }
    }

    public boolean has(Channel channel) {
        return channels.contains(channel);
    }

    /** Duración por defecto según el canal visual principal. */
    public static int defaultDuration(Set<Channel> channels) {
        return channels.contains(Channel.SHOWCASE) ? DEFAULT_SHOWCASE_MS : DEFAULT_HUD_MS;
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }
}

