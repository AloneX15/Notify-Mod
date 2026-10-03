package com.takumistudios.notifymod.core;

import java.util.List;
import java.util.Objects;

/**
 * Texto de una notificación: literal o clave de traducción, con marcadores {@code {nombre}}. Para las traducciones,
 * {@code with} son los argumentos posicionales ({@code %s}), que también admiten marcadores.
 */
public record Message(String text, boolean translate, List<String> with) {
    public static final int MAX_TEXT = 512;
    public static final int MAX_WITH = 8;

    public Message {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("El mensaje está vacío");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("El mensaje es demasiado largo (máximo " + MAX_TEXT + " caracteres)");
        }
        with = with == null ? List.of() : List.copyOf(with);
        if (with.size() > MAX_WITH) {
            throw new IllegalArgumentException("Demasiados argumentos de traducción (máximo " + MAX_WITH + ")");
        }
        for (String w : with) {
            if (w.length() > NotificationArgs.MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Argumento de traducción demasiado largo");
            }
        }
    }

    public static Message literal(String text) {
        return new Message(text, false, List.of());
    }

    public static Message translatable(String key, List<String> with) {
        return new Message(key, true, with);
    }

    /** Texto literal con los marcadores resueltos (solo para mensajes literales). */
    public String resolveLiteral(NotificationArgs args) {
        return translate ? text : Placeholders.apply(text, args);
    }

    /** Argumentos de traducción con los marcadores resueltos. */
    public List<String> resolveWith(NotificationArgs args) {
        if (with.isEmpty()) {
            return with;
        }
        return with.stream().map(w -> Placeholders.apply(w, args)).toList();
    }
}

