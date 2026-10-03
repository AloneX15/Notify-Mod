package com.takumistudios.notifymod.core;

/**
 * Sustituye {@code {nombre}} por el valor del argumento. Una sola pasada: un valor que contenga {@code {otro}} no se
 * vuelve a expandir (evita inyecciones y bucles). Los marcadores sin argumento se dejan tal cual, y dos llaves de
 * apertura seguidas escriben una llave literal.
 */
public final class Placeholders {
    private static final int MAX_NAME = 32;

    private Placeholders() {
    }

    public static String apply(String text, NotificationArgs args) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '{') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '{') {
                    out.append('{');
                    i += 2;
                    continue;
                }
                int close = text.indexOf('}', i + 1);
                if (close > i + 1 && close - i - 1 <= MAX_NAME) {
                    String name = text.substring(i + 1, close);
                    String value = args.get(name);
                    if (value != null) {
                        out.append(value);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}


