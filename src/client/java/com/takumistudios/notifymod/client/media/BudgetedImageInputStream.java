package com.takumistudios.notifymod.client.media;

import java.io.ByteArrayInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Flujo de entrada con presupuesto de lectura. Los decodificadores de terceros pueden entrar en bucles casi infinitos
 * con archivos dañados (comprobado en la Fase 0: el lector VP8L de TwelveMonkeys reintenta lecturas sin avanzar). Como
 * esos bucles siempre leen del flujo, limitar los bytes leídos a un múltiplo del tamaño del archivo los corta.
 *
 * <p>Se lanza una excepción no comprobada a propósito: las bibliotecas capturan {@code EOFException} o
 * {@code IOException} para seguir leyendo, pero no esta.
 */
final class BudgetedImageInputStream extends MemoryCacheImageInputStream {
    /** Una decodificación válida relee cada byte unas pocas veces (TwelveMonkeys lee 8 bytes por cada byte de bits). */
    static final int READ_FACTOR = 64;
    static final long READ_SLACK = 64 * 1024;

    private final long budget;
    private long consumed;

    BudgetedImageInputStream(byte[] data) {
        super(new ByteArrayInputStream(data));
        this.budget = data.length * (long) READ_FACTOR + READ_SLACK;
    }

    @Override
    public int read() throws java.io.IOException {
        spend(1);
        return super.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws java.io.IOException {
        spend(Math.max(1, len));
        return super.read(b, off, len);
    }

    private void spend(long bytes) {
        consumed += bytes;
        if (consumed > budget) {
            throw new ReadBudgetExceededException();
        }
    }

    /** El decodificador ha leído demasiado: el archivo está dañado o es malicioso. */
    static final class ReadBudgetExceededException extends RuntimeException {
        ReadBudgetExceededException() {
            super("el decodificador ha leído demasiado (archivo dañado)", null, false, false);
        }
    }
}

