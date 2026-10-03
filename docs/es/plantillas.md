# Plantillas del servidor

Una plantilla define el contenido y los canales de un aviso. Una presentación define sus visuales. El servidor
lee `data/`; el cliente lee `assets/`. No pongas presentaciones en el datapack ni lógica del servidor en el resource pack.

## Crear una plantilla

En un datapack compatible con tu versión de Minecraft, crea `data/mipack/notify/templates/bienvenida.json`:

```json
{
  "format_version": 1,
  "channels": ["showcase", "chat"],
  "presentation": "notifymod:motd",
  "placement": "center",
  "priority": "NORMAL",
  "message": "Bienvenido, {jugador}",
  "key": "bienvenida",
  "cooldown": "10s",
  "duration": "6s"
}
```

Recarga y envía:

```mcfunction
/reload
/notify validate
/notify showcase mipack:bienvenida jugador=Alex
```

La ruta determina el id; un campo `id` dentro del JSON no lo cambia. Para administrar sin datapack, escribe el mismo
JSON en `config/notifymod/managed/templates/mipack/bienvenida.json`. Esa definición tiene prioridad tras recargar.

## Referencia disponible

| Campo | Comportamiento |
|---|---|
| `format_version` | 1 |
| `channels` | Lista no vacía de `chat`, `hud`, `showcase`; por defecto `showcase` |
| `presentation` | Id; por defecto `notifymod:motd` |
| `placement` | Las 7 posiciones; por defecto centro para Showcase, esquina superior derecha para HUD |
| `priority` | LOW, NORMAL, HIGH, CRITICAL; CRITICAL siempre usa centro |
| `message` | Obligatorio: texto, `{"text":"…"}` o `{"translate":"clave","with":["{arg}"]}` |
| `mirror_to_chat` | `true` añade chat a los canales |
| `key` | Clave opcional para deduplicar/cancelar; hasta 64 caracteres |
| `cooldown` | Espera entre envíos de esa plantilla con el comando `showcase` |
| `duration` | `"6s"` o milisegundos; 500 ms–120 s; una presentación visual cargada manda sobre ella |

Hasta 16 argumentos de texto plano, nombres `[a-z0-9_]` de 1–32 caracteres y valores de hasta 256 caracteres.
Las claves de traducción deben existir en los resource packs del cliente. La plantilla integrada
`notifymod:restart_warning` usa la clave `notifymod.restart.warning` y el argumento `minutos`.

El `cooldown` de plantilla se comprueba en el comando; los disparadores utilizan sus propios límites.
`audience`, `fallback` y un permiso propio en la plantilla están previstos en el plan, pero **no se interpretan todavía**.
El texto de `message` es el respaldo actual cuando faltan los visuales. Usa audiencias de disparador para filtrar destinatarios.

## Distribuir los visuales

Coloca una presentación propia en `assets/mipack/notify/presentations/bienvenida.json` y los medios en el mismo resource
pack. Asígnala mediante `presentation`. Distribuye el pack a todos los jugadores, por ejemplo mediante el resource pack
del servidor. [Referencia de presentaciones](presentaciones.md).

## Estilo del chat y de las tarjetas

El chat muestra un título verde en negrita, una línea vacía y el cuerpo del aviso en verde. Las tarjetas de texto
usan un panel oscuro translúcido, icono circular, título rojo, cuerpo rosado y una indicación para ocultarlas.
HUD usa `top_right` por defecto. La tecla se asigna en Opciones → Controles → Notify Mod; el pie muestra la tecla
asignada o indica que falta asignarla. CRITICAL conserva su regla de ir al centro.

Cada plantilla puede definir `style`. Este ejemplo funciona sin resource pack:

```json
{
  "format_version": 1,
  "channels": ["hud", "chat"],
  "placement": "top_right",
  "message": "Gracias por la ayuda en la misión de hoy.",
  "duration": "15s",
  "style": {
    "title": "MOMENTO REVILL",
    "chat_color": "#00CC33",
    "title_color": "#CC2020",
    "text_color": "#EE9999",
    "background_color": "#101010",
    "hint_color": "#AAAAAA",
    "background_opacity": 0.65,
    "width": 240,
    "shadow": true,
    "show_icon": true,
    "show_hide_hint": true
  }
}
```

Los colores usan `#RRGGBB`; la opacidad admite 0..1 (0 quita el fondo) y la anchura 120..480 unidades GUI.
El título admite texto o el mismo objeto `text`/`translate`/`with` que `message`: máximo 128 caracteres y cuatro
argumentos de traducción. El título predeterminado es «¡Atención!». Campos omitidos usan los valores del ejemplo.
Los tipos incorrectos, colores inválidos y campos de estilo desconocidos se rechazan al validar la plantilla.
La tarjeta adapta su anchura a la pantalla y recorta mensajes que no caben con una última línea «…».
El chat conserva el ajuste de líneas, fondo y escala propios de Minecraft.

Prueba los dos ejemplos incluidos:

```mcfunction
/notify showcase notifymod:styled_chat
/notify showcase notifymod:styled_corner
/notify showcase notifymod:styled_corner message="Otro mensaje" chat=true
```

Tras editar la plantilla, ejecuta `/notify reload`. Los cambios de título y colores se conservan al sobrescribir
mensaje, duración, prioridad o colocación por comando. Las presentaciones con pistas de timeline mantienen sus
propios colores: `style` se aplica al chat y a las tarjetas de texto, no reemplaza esas pistas.
El protocolo visual pasa a v2; servidor y cliente necesitan esta actualización para mostrar las tarjetas.
Con `require_client=false`, un cliente incompatible recibe el aviso por el chat vanilla.

![Chat y tarjeta con estilo](../images/styled-chat-corner.png)
