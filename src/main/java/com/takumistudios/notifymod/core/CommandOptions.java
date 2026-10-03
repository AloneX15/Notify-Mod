package com.takumistudios.notifymod.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Opciones de los comandos con la sintaxis {@code clave=valor} (§18 del plan). Las comillas dobles agrupan espacios
 * ({@code message="Reinicio en 5"}) y admiten {@code \"} y {@code \\}. Lo que no es una opción conocida es parte del
 * mensaje o, si se permite, un argumento de la plantilla.
 */
public final class CommandOptions {
    private static final Pattern OPTION_KEY = Pattern.compile("[a-z_]{1,32}");
    private static final Pattern ARG_KEY = Pattern.compile("[a-z0-9_]{1,32}");
    public static final int MAX_INPUT = 2048;

    public record Parsed(Map<String, String> options, Map<String, String> args, List<String> words) {
        public String option(String name) {
            return options.get(name);
        }

        /** El mensaje: la opción {@code message=} o, si no está, las palabras sueltas unidas por espacios. */
        public String message() {
            String explicit = options.get("message");
            if (explicit != null) {
                return explicit;
            }
            return String.join(" ", words);
        }
    }

    private CommandOptions() {
    }

    /**
     * @param known     opciones reconocidas (placement, priority...)
     * @param allowArgs si {@code true}, cualquier otro {@code nombre=valor} válido es un argumento de plantilla
     */
    public static Parsed parse(String input, Set<String> known, boolean allowArgs) {
        if (input == null) {
            input = "";
        }
        if (input.length() > MAX_INPUT) {
            throw new IllegalArgumentException("El comando es demasiado largo");
        }
        Map<String, String> options = new LinkedHashMap<>();
        Map<String, String> args = new LinkedHashMap<>();
        List<String> words = new ArrayList<>();
        for (Token token : tokenize(input)) {
            if (token.equals >= 0) {
                String name = token.text.substring(0, token.equals);
                String value = token.text.substring(token.equals + 1);
                if (OPTION_KEY.matcher(name).matches() && known.contains(name)) {
                    if (options.put(name, value) != null) {
                        throw new IllegalArgumentException("Opción repetida: " + name);
                    }
                    continue;
                }
                if (allowArgs && ARG_KEY.matcher(name).matches()) {
                    if (args.put(name, value) != null) {
                        throw new IllegalArgumentException("Argumento repetido: " + name);
                    }
                    continue;
                }
            }
            words.add(token.text);
        }
        return new Parsed(Collections.unmodifiableMap(options), Collections.unmodifiableMap(args),
                Collections.unmodifiableList(words));
    }

    /** Lee un sí/no de una opción. */
    public static boolean parseBoolean(String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "si", "sí", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> throw new IllegalArgumentException("Se esperaba true o false: " + value);
        };
    }

    /** Un trozo del comando. {@code equals} es la posición del primer '=' fuera de comillas, o -1. */
    private record Token(String text, int equals) {
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean started = false;
        int equals = -1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inQuotes) {
                if (c == '\\' && i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }
                }
                if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (started) {
                    tokens.add(new Token(current.toString(), equals));
                    current.setLength(0);
                    started = false;
                    equals = -1;
                }
                continue;
            }
            started = true;
            if (c == '"') {
                inQuotes = true;
            } else {
                if (c == '=' && equals < 0) {
                    equals = current.length();
                }
                current.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Faltan las comillas de cierre");
        }
        if (started) {
            tokens.add(new Token(current.toString(), equals));
        }
        return tokens;
    }
}

