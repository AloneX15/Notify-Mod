# Presentaciones (formato v1)

Una presentación es la parte visual de un Showcase: una **línea de tiempo** con pistas de texto, imágenes, formas,
modelos 3D y sonido. Vive en un resource pack:

```
assets/<espacio>/notify/presentations/<nombre>.json   →   id <espacio>:<nombre>
```

Se recarga con **F3+T**. Se lanza con `/notify showcase <espacio>:<nombre> message="..." [argumentos...]` o desde una
plantilla con `"presentation": "<espacio>:<nombre>"`. Si un jugador no tiene la presentación, ve el mensaje como texto.

## Ejemplo de uso

```json
{
  "format_version": 1,
  "_comment": "Eliminación de un jugador",
  "duration": 7000,
  "background": { "type": "dim", "color": "#220000", "opacity": 0.6 },
  "letterbox": { "height": 0.1 },
  "tracks": [
    { "type": "sound", "time": 0, "sound": "minecraft:block.note_block.bell", "pitch": 0.8 },
    {
      "type": "texture", "time": 500, "file": "mipack:notify/media/calavera.gif",
      "anchor": "center", "offset": [0, -0.08], "size": [0.25, 0.25],
      "animation_in": { "type": "pop", "easing": "ease_out_elastic", "duration": 600 }
    },
    {
      "type": "text", "time": 1000, "content": "{message}", "color": "#FF4040", "scale": 2.5,
      "anchor": "center", "offset": [0, 0.18],
      "animation_in": { "type": "slide_right", "easing": "ease_out_expo", "duration": 500 },
      "animation_out": { "type": "fade", "duration": 400 }
    },
    {
      "type": "text", "time": 1600, "content": "Jugador: {jugador}", "anchor": "center", "offset": [0, 0.3],
      "animation_in": { "type": "typewriter", "duration": 800 }
    }
  ],
  "docked": { "omit": [], "mute": false }
}
```

`/notify showcase mipack:eliminacion message="ELIMINADO" jugador=Steve`

## Campos de la presentación

| Campo | Qué es | Por defecto |
|---|---|---|
| `format_version` | Versión del formato | 1 |
| `duration` | Duración total en ms (o `"7s"`), de 500 ms a 2 min. **Manda sobre la del servidor** | 5000 |
| `background` | Fondo a pantalla completa: `{ "type": "dim", "color", "opacity" }` | sin fondo |
| `letterbox` | Barras de cine: `{ "height": 0.1, "color": "#000000" }` (alto de cada barra, máx. 0.25) | sin barras |
| `tracks` | Lista de pistas (máx. 64) | — |
| `preload` | `true`: sus imágenes se decodifican al cargar los recursos, antes de usarla (para presentaciones que se van a lanzar pronto) | `false` |
| `docked` | Versión compacta en una esquina: `omit` (nombres de pistas que no se dibujan) y `mute` | — |
| `_comment` | Texto libre | — |

El fondo y las barras **nunca** se dibujan en la versión compacta.

## Campos de cada pista

| Campo | Tipos | Qué es |
|---|---|---|
| `type` | todos | `text`, `texture`, `shape`, `model` o `sound` |
| `time` · `duration` | todos | Inicio en ms desde el principio y duración (por defecto, hasta el final) |
| `name` | todos | Nombre para `docked.omit` |
| `z` | visuales | Capa: mayor = encima |
| `anchor` | visuales | `center`, `top`, `top_left`, `top_right`, `left`, `right`, `bottom`, `bottom_left`, `bottom_right` |
| `offset` | visuales | `[x, y]` en fracciones del ancho y alto de la pantalla |
| `size` | `texture`, `shape`, `model` | `[ancho, alto]` en fracciones del **alto** de la pantalla (un cuadrado sigue siendo cuadrado en pantallas panorámicas). En texturas, alto `0` mantiene la proporción |
| `opacity` | visuales | 0 a 1 |
| `animation_in` · `animation_out` · `loop` | visuales | Ver animaciones |
| `content` | `text` | `"texto con {argumentos}"`, `{ "translate": "clave", "with": ["{arg}"] }` o `"{message}"` (el mensaje enviado, por defecto) |
| `color` · `scale` · `shadow` · `max_width` | `text` | Color `#RRGGBB`, tamaño (1 = texto normal en un lienzo de 270 de alto), sombra y ancho máximo antes de partir líneas |
| `file` | `texture` | PNG, GIF, WebP animado o Lottie (`.json`), por id: `mipack:notify/media/x.gif`. Un PNG con `.png.mcmeta` al lado es una spritesheet animada (ver abajo) |
| `item` · `entity` | `model` | Id de un ítem o bloque (`minecraft:diamond_sword`) **o** de un mob (`minecraft:zombie`), solo uno. Tamaño con `size`; `rotate` y `spin` hacen girar al mob |
| `color2` | `shape` | Segundo color para un degradado vertical |
| `sound` · `volume` · `pitch` | `sound` | Id del sonido (p. ej. `minecraft:block.note_block.bell`), volumen 0–1, tono 0.5–2 |

## Animaciones

```json
"animation_in": { "type": "slide_right", "easing": "ease_out_expo", "duration": 500, "amount": 0.15 }
```

- **Entrada y salida:** `fade`, `slide_left`, `slide_right`, `slide_up`, `slide_down` (`amount` = distancia),
  `scale` (`amount` = escala inicial), `pop`, `rotate` (`amount` = grados) y `typewriter`.
- **En bucle** (`loop`, con `period` en ms): `shake`, `pulse` y `spin`.
- **Forma corta:** `"loop": "spin"` equivale a `{ "type": "spin" }` con los valores por defecto (vale también en
  `animation_in` y `animation_out`).
- **Easings:** `linear`; `ease_in_*`, `ease_out_*` y `ease_in_out_*` de `sine`, `quad`, `cubic`, `quart`, `quint`,
  `expo`, `circ`, `back`, `elastic` y `bounce`; `ease_in`, `ease_out`, `ease_in_out`; y
  `cubic_bezier(x1,y1,x2,y2)`.

## Spritesheets (`.mcmeta`)

El mismo formato que las texturas animadas de Minecraft: junto a `x.png` va `x.png.mcmeta`.

```json
{ "animation": { "frametime": 2, "width": 16, "height": 16, "frames": [0, 1, { "index": 2, "time": 10 }] } }
```

- La imagen se corta en fotogramas de `width`×`height` (por defecto, cuadrados del lado menor) de izquierda a derecha
  y de arriba abajo. Una tira vertical de 16×64 son 4 fotogramas de 16×16.
- `frametime` en ticks (1 tick = 50 ms, por defecto 1); `frames` elige el orden y el tiempo de cada uno (por defecto,
  todos en orden).
- La hoja completa puede medir hasta 8192 px de lado; cada fotograma sigue el límite de 1024×1024.
- `interpolate` no se admite (se avisa en el log). En GIF, WebP y Lottie el `.mcmeta` se ignora.

## Modelos 3D

```json
{ "type": "model", "time": 0, "entity": "minecraft:zombie", "anchor": "center", "size": [0.3, 0.3], "loop": "spin" }
```

- Ítems y bloques se dibujan como en el inventario; los mobs, con una copia local que no se añade al mundo.
- Los modelos no admiten transparencia: aparecen cuando la opacidad de la pista pasa de 0.35.
- Si un modelo falla al dibujarse (p. ej. por otro mod), solo se desactiva ese modelo y se anota en el log.

## Qué tomar en cuenta

- El reloj es el del cliente, independiente de los FPS, y **se para con la pausa** en mundos de un jugador.
- Las imágenes se decodifican una vez y se guardan en una caché con presupuesto de memoria (128 MB). Límites por
  archivo: 8 MB, 1024×1024, 512 fotogramas.
- Los campos desconocidos y lo no soportado (`font`, fondos que no son `dim`) se avisan en el log; no rompen la
  presentación. Un error (tipo desconocido, color mal escrito, id con `..`) descarta esa presentación y se anota en el
  log.
- **Calidad baja** (`"media_quality": "low"` en `config/notifymod/client.json`): las imágenes se reducen a 256 px de
  lado y las animaciones usan uno de cada dos fotogramas, con la misma duración.
- Pendiente: fuentes propias (`font`), viñeta y límite de destellos (Fase 7).

