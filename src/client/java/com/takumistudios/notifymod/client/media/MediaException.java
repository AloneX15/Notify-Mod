package com.takumistudios.notifymod.client.media;

import java.io.IOException;

/** Un recurso multimedia no se pudo cargar: formato no válido, corrupto o fuera de los límites. */
public final class MediaException extends IOException {
    public MediaException(String message) {
        super(message);
    }

    public MediaException(String message, Throwable cause) {
        super(message, cause);
    }
}

