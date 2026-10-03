package com.takumistudios.notifymod.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Argumentos de una notificación ({@code {jugador}} → "Notch"). Lo valida el servidor antes de enviar y el cliente al
 * recibir (§23 del plan): número máximo de argumentos, nombres cortos y valores de texto plano sin códigos de formato.
 */
public final class NotificationArgs {
    public static final int MAX_ARGS = 16;
    public static final int MAX_VALUE_LENGTH = 256;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

    public static final NotificationArgs EMPTY = new NotificationArgs(Map.of());

    private final Map<String, String> values;

    private NotificationArgs(Map<String, String> values) {
        this.values = values;
    }

    /** Valida y copia. Lanza {@link IllegalArgumentException} con un mensaje claro si algo no cumple las reglas. */
    public static NotificationArgs of(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EMPTY;
        }
        if (raw.size() > MAX_ARGS) {
            throw new IllegalArgumentException("Demasiados argumentos (" + raw.size() + ", máximo " + MAX_ARGS + ")");
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String name = e.getKey();
            String value = e.getValue() == null ? "" : e.getValue();
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Nombre de argumento no válido: " + name
                        + " (solo minúsculas, números y _; máximo 32)");
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("El argumento " + name + " es demasiado largo (máximo "
                        + MAX_VALUE_LENGTH + " caracteres)");
            }
            copy.put(name, clean(value));
        }
        return new NotificationArgs(Collections.unmodifiableMap(copy));
    }

    /** Quita caracteres de control y el símbolo de formato §, para que un argumento no pueda cambiar el estilo. */
    static String clean(String value) {
        StringBuilder out = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean bad = c == '\u00A7' || Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
            if (bad && out == null) {
                out = new StringBuilder(value.length());
                out.append(value, 0, i);
            } else if (!bad && out != null) {
                out.append(c);
            }
        }
        return out == null ? value : out.toString();
    }

    public Map<String, String> values() {
        return values;
    }

    public String get(String name) {
        return values.get(name);
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NotificationArgs other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}

