package com.takumistudios.notifymod.client.media;

/**
 * Límites de la tubería de medios (§21 y §23 del plan): un pack de recursos no puede colgar ni agotar la memoria del
 * cliente. Todo decodificador los comprueba antes de reservar memoria.
 *
 * @param maxFileBytes tamaño máximo del archivo de origen
 * @param maxWidth     ancho máximo en píxeles
 * @param maxHeight    alto máximo en píxeles
 * @param maxFrames    número máximo de fotogramas
 * @param maxBytes     memoria máxima de la secuencia decodificada (ARGB, 4 bytes por píxel)
 * @param minDelayMs   retardos menores se tratan como 100 ms, igual que los navegadores
 */
public record MediaLimits(long maxFileBytes, int maxWidth, int maxHeight, int maxFrames, long maxBytes, int minDelayMs) {
    public static final MediaLimits DEFAULT = new MediaLimits(8L << 20, 1024, 1024, 512, 64L << 20, 20);

    static final int FALLBACK_DELAY_MS = 100;

    public MediaLimits {
        if (maxFileBytes <= 0 || maxWidth <= 0 || maxHeight <= 0 || maxFrames <= 0 || maxBytes <= 0 || minDelayMs < 0) {
            throw new IllegalArgumentException("Límites no válidos");
        }
    }

    public void checkSize(int width, int height) throws MediaException {
        if (width <= 0 || height <= 0) {
            throw new MediaException("Tamaño no válido: " + width + "x" + height);
        }
        if (width > maxWidth || height > maxHeight) {
            throw new MediaException("Imagen demasiado grande: " + width + "x" + height
                    + " (máximo " + maxWidth + "x" + maxHeight + ")");
        }
    }

    public void checkFrames(int frames, int width, int height) throws MediaException {
        checkSize(width, height);
        if (frames <= 0) {
            throw new MediaException("La animación no tiene fotogramas");
        }
        if (frames > maxFrames) {
            throw new MediaException("Demasiados fotogramas: " + frames + " (máximo " + maxFrames + ")");
        }
        long bytes = (long) width * height * 4L * frames;
        if (bytes > maxBytes) {
            throw new MediaException("La animación ocupa " + (bytes >> 20) + " MiB en memoria (máximo "
                    + (maxBytes >> 20) + " MiB)");
        }
    }

    public int normalizeDelay(int delayMs) {
        return delayMs < Math.max(1, minDelayMs) ? FALLBACK_DELAY_MS : delayMs;
    }
}

