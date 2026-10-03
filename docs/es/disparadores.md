# Disparadores

Los disparadores conectan sucesos del servidor con plantillas existentes. Están desactivados por defecto. Este bloque
implementa el marco y los primeros eventos; **la fase 4 y el plan completo siguen pendientes de terminar**.

## Prueba rápida

Con OP 3, desde el juego:

```mcfunction
/notify trigger list
/notify trigger info notifymod:player_death
/notify trigger test notifymod:player_death
/notify trigger enable notifymod:player_death
/notify trigger set notifymod:player_death cooldown 30s
/notify trigger audience notifymod:player_death
/notify trigger disable notifymod:player_death
```

`test` muestra una vista previa **solo a quien ejecuta el comando**, incluso si el disparador está desactivado.
No mata ni modifica jugadores. Desde consola se puede usar `execute as <jugador> run notify trigger test <id>`.
`fire` respeta activación, condiciones y límites. Los ids sin espacio de nombres usan `notifymod:`.

## Formato y ubicaciones

- Datapack: `data/<namespace>/notify/triggers/<nombre>.json`.
- Administrados: `config/notifymod/managed/triggers/<namespace>/<nombre>.json`; tienen prioridad.
- Estado de activación y campos editados: `config/notifymod/triggers_state.json`.
- Auditoría: `config/notifymod/audit.jsonl`, con rotación a `audit.previous.jsonl` al superar 4 MiB.

Recarga con `/reload` o `/notify reload`. `/notify validate` y `trigger list` muestran los errores. Una definición
inválida queda sin ejecutar; los campos todavía no soportados se rechazan. El estado dañado tiene copia `.bak`.
Las ediciones y la auditoría se escriben en un executor propio; las ediciones usan reemplazo atómico.

```json
{
  "format_version": 1,
  "type": "player_death",
  "enabled": false,
  "template": "notifymod:player_death",
  "placement": "center",
  "priority": "HIGH",
  "args": { "jugador": "$player", "causa": "$death_message" },
  "conditions": { "dimension": ["minecraft:overworld"] },
  "audience": { "include": [{ "type": "all" }], "exclude": [{ "team": "staff" }] },
  "cause_delivery": "after_respawn",
  "cooldown": "10s",
  "per_player_cooldown": "30s",
  "max_per_minute": 6
}
```

## Campos de la definición

| Campo | Por defecto | Regla |
|---|---|---|
| `format_version` | Obligatorio | Entero `1` |
| `type` | `manual` | Uno de los tipos disponibles |
| `enabled` | `false` | Booleano; el estado persistente puede sobrescribirlo |
| `template` | Obligatorio | Plantilla existente, con id cualificado |
| `placement`, `priority` | De la plantilla | Sobrescribir colocación/prioridad; CRITICAL sigue al centro |
| `args` | Vacío | Hasta 16 valores literales o variables de suceso |
| `conditions` | Vacío | Todas las condiciones deben cumplirse |
| `audience.include` | Todos | Unión de reglas; lista vacía significa nadie |
| `audience.exclude` | Vacío | Resta destinatarios después de incluir |
| `cause_delivery` | `after_respawn` | `after_respawn`, `skip` o `immediate` según el tipo |
| `cooldown`, `per_player_cooldown` | `0` | 0–24 h, por disparador y jugador causante |
| `max_per_minute` | `6` | Entero 1–120 |
| `allow_coordinates` | `false` | Permitir argumentos `$x`, `$y`, `$z` |
| `_comment` | Vacío | Comentario opcional |
| `id` | De la ruta | Un campo `id` dentro del JSON no cambia el id efectivo |

El estado persistente prevalece sobre la definición al recargar. `set` permite `enabled`, `cooldown`,
`per_player_cooldown`, `max_per_minute`, `placement` y `priority`; no crea definiciones ni modifica la audiencia.
Para esos cambios edita el JSON y recarga. `info` muestra la definición configurada; si un callback falla, consulta
el log porque el disparador puede quedar desactivado para la sesión aunque la definición diga `enabled=true`.

## Funciones disponibles

Tipos: `player_death`, `player_respawn`, `player_join`, `player_leave`, `chat_keyword`, `server_ready`,
`server_stopping` y `manual`. `chat_keyword` compara el mensaje completo, sin distinguir mayúsculas; declara
`conditions.keyword`. Los eventos no cancelan ni cambian el comportamiento vanilla.

Condiciones: `dimension`, `damage_type`, `killer_type`, `cause_permission`, `cause_group`, `keyword`.
Todas las condiciones deben cumplirse; una lista de valores acepta cualquiera de ellos.

Audiencias: `all`, `involved` (quien causa el suceso), `team`, `dimension`, `permission` y `group`. La unión de los
`include` se calcula entre jugadores conectados y después se resta `exclude`. Un `include` vacío no selecciona nadie.
Grupos sin LuckPerms seleccionan nadie y producen un aviso en el log. Esta integración **no se ha verificado con un
servidor LuckPerms**. Los permisos aceptan `notifymod:algo` o `notifymod.algo`; LuckPerms convierte la primera forma
a la segunda, según su [adaptador de Fabric](https://github.com/LuckPerms/LuckPerms/blob/master/fabric/src/main/java/me/lucko/luckperms/fabric/listeners/FabricPermissionsApiV1Listener.java).

Variables: `$player`, `$killer`, `$death_message`, `$dimension`, `$time`. `$x`, `$y`, `$z` requieren
`allow_coordinates: true`. Los valores se limitan a 256 caracteres y se limpian antes de enviarlos.

Límites: hasta 1024 definiciones; 32 reglas por lista de audiencia; 16 argumentos; cooldowns de hasta 24 horas;
`max_per_minute` de 1 a 120 (6 por defecto); tope global de 20 ejecuciones aceptadas de disparadores por segundo (cada una puede tener varios destinatarios).

Al morir, la entrega a quien causó el suceso espera hasta reaparecer (`after_respawn`) o se omite (`skip`); `immediate`
se rechaza para `player_death`. La cola diferida tiene 16 entradas por jugador y caduca a los 60 segundos.
Al reaparecer se comprueban otra vez activación, audiencia y permisos. Desconectar, desactivar o recargar vacía las
entradas correspondientes. Una prueba manual de un jugador vivo no espera a una reaparición.

Los listeners de cada tipo se registran al activarlo por primera vez. Fabric no tiene una API para retirarlos;
desactivarlos deja una comprobación constante sin construir datos ni resolver audiencias. Los errores de un disparador
lo desactivan durante la sesión; una edición o recarga permite reintentarlo.

## Permisos

Acceso general: `notifymod.command` (OP 2). `notifymod.trigger.list` e `.info` (OP 2); `.manage`, `.test` y `.fire`
(OP 3). `audience` usa `.info`. Los comandos tienen el límite por jugador de `server.json`.
El envío respeta los permisos de recepción de cada canal y `notifymod.receive.<namespace>.<plantilla>`;
`notifymod.bypass.trigger.<namespace>.<disparador>` excluye a quien tenga ese permiso (sin concesión por OP).

## Pendiente para completar el plan

Catálogo A restante (primer ingreso, contadores de muerte, dimensión, bloques, jefes y fechas), catálogo B y C,
coalescencia, filtros de etiquetas/hardcore/probabilidad, selectores/radio/asesino implicado, prefijos y sufijos,
pruebas como otro jugador, permiso de lanzamiento por id y compatibilidad real con LuckPerms.
Menú, API pública, variables, pulido completo, pack de referencia, publicación 1.0 y escenas siguen pendientes.
Esta primera wiki documenta las funciones disponibles; no completa toda la fase 8 del plan.

## Verificación

`TriggerTest` prueba validación, privacidad de coordenadas y límites. `TriggerIdArgumentTest` prueba ids cualificados.
Los gametests de servidor verifican carga, comandos y rechazo de gestión con OP 2. El de cliente prueba la vista previa,
un suceso sintético activado/desactivado y una muerte/reaparición real. La última prueba local del cliente es de 26.3.
La CI ejecuta cada versión. LuckPerms sigue pendiente de prueba de integración.

![Vista previa del disparador](../images/phase4-trigger-preview.png)

![Entrega tras reaparecer](../images/phase4-after-respawn.png)
