package com.takumistudios.notifymod.client.media.lottie;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Funciones de Lottie que el archivo usa y Notify Mod no soporta. Se muestran en el validador y en el log. */
final class LottieReport {
    private final Set<String> unsupported = new TreeSet<>();

    void unsupported(String feature) {
        unsupported.add(feature);
    }

    List<String> list() {
        return Collections.unmodifiableList(List.copyOf(unsupported));
    }
}

