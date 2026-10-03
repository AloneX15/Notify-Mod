# ADR 0001: Formatos animados (GIF, WebP y Lottie). Puerta de decisión de la Fase 0

> Registro histórico de la prueba de viabilidad. Las referencias a trabajo pendiente describen ese momento:
> la decodificación en segundo plano y la caché LRU se implementaron en la fase 3.
> `/notify validate` todavía no consulta el decodificador del cliente; los avisos de medios aparecen en su log.
> El comportamiento actual está en [presentaciones](../es/presentaciones.md).

- **Estado:** aceptada
- **Fecha:** 2026-10-03
- **Contexto en el plan:** §9 (tubería de medios), §27 Fase 0, decisión abierta D6

## Contexto

El plan exigía comprobar en la Fase 0 que GIF, WebP animado y Lottie pueden convertirse en una secuencia de
fotogramas **sin librerías nativas**, con límites de memoria y sin que un archivo dañado tumbe el juego. Si Lottie no
era viable, había que acotarlo o pasarlo a la 1.1.

## Resultado de las pruebas

| Formato | Técnica | Resultado |
|---|---|---|
| GIF | Lector del JDK (`ImageIO`) con composición propia (métodos de eliminación) | ✅ Fotogramas y retardos exactos |
| WebP fijo y animado (con y sin pérdida, con alfa) | Contenedor RIFF (`VP8X`, `ANIM`, `ANMF`) leído por Notify; cada fotograma lo decodifica **TwelveMonkeys `imageio-webp` 3.15.2** | ✅ Sin pérdida exacto; con pérdida dentro de tolerancia |
| Lottie (`.json`) | Rasterizador propio con Java2D, prerrenderizado a fotogramas (máx. 30 FPS) | ✅ Para el subconjunto de abajo |

Pruebas: `MediaDecodersTest` (unitarias, incluye 300 archivos dañados y todos los cortes posibles por formato) y
`MediaPocHud` en el gametest de cliente (los 5 ejemplos dibujados en el HUD de 26.3).

## Decisión

1. **WebP:** TwelveMonkeys (BSD-3-Clause, Java puro). Va en el jar con `include` (6 módulos, solo cliente). El lector
   se crea desde su SPI porque el registro de `ImageIO` no ve las clases de los mods.
2. **Lottie entra en la 1.0, acotado a un subconjunto documentado:**
   - **Soportado:** capas de forma, sólidas y nulas; jerarquía (`parent`); grupos; rectángulos (con radio), elipses y
     trazados; rellenos (no-cero y par-impar) y trazos (extremos, uniones, inglete); transformaciones (ancla,
     posición unida o separada, escala, rotación, opacidad); fotogramas clave con easing Bézier y `hold`;
     `ip`/`op`/`st`/`sr` de capa.
   - **No soportado (se ignora y se avisa):** texto y fuentes, imágenes y precomposiciones, degradados, máscaras,
     mates, efectos, expresiones, recorte de trazos, repetidores, estrellas y polígonos, esquinas redondeadas, fusión
     de trazados y sesgado.
   - Lo no soportado **no rompe la animación**: se omite y aparece en la lista de avisos del decodificador (que usará
     `/notify validate`) y una vez en el log.
3. **Límites fijos del decodificador:** tamaño de archivo, ancho y alto, número de fotogramas y memoria total
   (`MediaLimits`); Lottie además con máximo de 20 000 nodos y 32 niveles de anidación.
4. **Lectura con presupuesto:** todo lo que lee TwelveMonkeys pasa por `BudgetedImageInputStream` (ver el registro de
   errores, 2026-10-03).

## Consecuencias

- La tubería única de fotogramas (§9.1) queda confirmada: en el render solo se elige el fotograma.
- Ampliar el subconjunto de Lottie (degradados y recorte de trazos son lo más pedido) es trabajo de la Fase 3 o
  posterior y no cambia la arquitectura.
- Pendiente para la Fase 3: decodificar en un hilo de trabajo, presupuesto de memoria de vídeo con LRU y probar los
  mismos ejemplos en 26.1.2 y 26.2 dentro del juego.

