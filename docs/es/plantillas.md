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
| `placement` | Las 7 posiciones; por defecto centro para Showcase, esquina superior izquierda para HUD |
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
