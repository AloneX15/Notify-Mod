package com.takumistudios.notifymod.client.hud;

import com.takumistudios.notifymod.core.Placement;

/**
 * Posición de las tarjetas en pantalla (§6.2). Coordenadas de GUI. Las tarjetas se apilan alejándose del borde: hacia
 * abajo en las posiciones de arriba y hacia arriba en las de abajo. Abajo se deja sitio para la barra de objetos.
 */
public final class HudLayout {
    public static final int MARGIN = 4;
    public static final int GAP = 3;
    /** Altura reservada a la barra de objetos, la vida y la experiencia en las posiciones de abajo en el centro. */
    public static final int HOTBAR_CLEARANCE = 64;

    private HudLayout() {
    }

    /**
     * @param offset distancia ya ocupada por las tarjetas anteriores de la pila
     * @return {x, y} de la esquina superior izquierda de la tarjeta
     */
    public static int[] cardOrigin(Placement placement, int screenW, int screenH, int cardW, int cardH, int offset) {
        int left = MARGIN;
        int right = screenW - MARGIN - cardW;
        int centerX = (screenW - cardW) / 2;
        int top = MARGIN + offset;
        int bottom = screenH - MARGIN - cardH - offset;
        return switch (placement) {
            case TOP_LEFT -> new int[] {left, top};
            case TOP_RIGHT -> new int[] {right, top};
            case BOTTOM_LEFT -> new int[] {left, bottom};
            case BOTTOM_RIGHT -> new int[] {right, bottom};
            case TOP -> new int[] {centerX, top};
            case BOTTOM -> new int[] {centerX, bottom - HOTBAR_CLEARANCE};
            case CENTER -> new int[] {centerX, (screenH - cardH) / 2};
        };
    }

    /** Hacia dónde entra deslizándose la tarjeta: -1 desde la izquierda, 1 desde la derecha, 0 sin desplazamiento. */
    public static int slideDirection(Placement placement) {
        return switch (placement) {
            case TOP_LEFT, BOTTOM_LEFT -> -1;
            case TOP_RIGHT, BOTTOM_RIGHT -> 1;
            default -> 0;
        };
    }
}

