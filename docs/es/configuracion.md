# Configuración y permisos

Los archivos se crean en `config/notifymod/`. Haz una copia antes de editarlos. Los valores numéricos fuera de rango
se normalizan; un archivo JSON dañado usa valores por defecto y se intenta conservar como `.bak`.

## Servidor: `server.json`

| Campo | Por defecto | Rango o comportamiento |
|---|---|---|
| `format_version` | `1` | Metadato de formato |
| `require_client` | `true` | Rechazar clientes sin mod/protocolo compatible |
| `missing_client_message` | `""` | Mensaje personalizado, hasta 512 caracteres |
| `download_url` | URL de releases de GitHub | Hasta 256 caracteres |
| `default_mirror_to_chat` | `false` | Copia al chat en envíos sin plantilla |
| `max_commands_per_minute` | `30` | 1–600 acciones por jugador; compartido por envío y comandos de disparadores |

Usa `/notify reload` tras editarlo. Las plantillas conservan sus propios canales: no heredan automáticamente
`default_mirror_to_chat`. El rate limit por jugador no se aplica a la consola; los disparadores sí tienen límites globales.

## Cliente: `client.json`

| Campo | Por defecto | Rango o comportamiento |
|---|---|---|
| `format_version` | `1` | Metadato de formato |
| `hide_corner_notifications` | `false` | Ocultar colocaciones distintas del centro |
| `card_scale` | `1.0` | 0.5–2.0 |
| `max_visible_cards` | `3` | 1–8 |
| `media_quality` | `high` | `low` reduce resolución y fotogramas |

Reinicia el cliente tras editar el archivo manualmente. La tecla de ocultación permite cambiar ese ajuste durante la
partida. No existe todavía un menú de configuración propio ni el modo seguro previsto en el plan; el límite de
destellos y los controles completos de accesibilidad siguen pendientes. Usa animaciones suaves y evita parpadeos.

## Permisos

El código utiliza la API de permisos incluida en Fabric API 26.x. Sus ids son `notifymod:command`, etc.; LuckPerms los
interpreta como `notifymod.command`. Sin gestor se usa el nivel de OP de respaldo. No se incluye la antigua biblioteca
`me.lucko:fabric-permissions-api` y la integración real con LuckPerms aún no se ha probado.

| Nodo LuckPerms | OP | Acción |
|---|---|---|
| `notifymod.command` | 2 | Acceso general, necesario además del nodo de cada acción |
| `notifymod.send`, `notifymod.hud`, `notifymod.showcase` | 2 | Enviar por cada canal |
| `notifymod.showcase.placement` | 2 | Cambiar colocación del Showcase |
| `notifymod.priority.high` | 2 | Usar HIGH |
| `notifymod.priority.critical` | 3 | Usar CRITICAL |
| `notifymod.cancel`, `notifymod.clear` | 2 | Cancelar |
| `notifymod.reload` | 3 | Recargar |
| `notifymod.validate` | 2 | Diagnóstico |
| `notifymod.trigger.list`, `notifymod.trigger.info` | 2 | Consultar; `audience` usa `.info` |
| `notifymod.trigger.manage`, `.test`, `.fire` | 3 | Gestionar, probar y lanzar |
| `notifymod.receive.chat`, `.hud`, `.showcase` | Todos | Recibir cada canal |
| `notifymod.receive.<namespace>.<plantilla>` | Todos | Recepción de plantilla en disparadores |
| `notifymod.bypass.trigger.<namespace>.<disparador>` | Ninguno | Excluir de un disparador, sin concesión por OP |

Por ejemplo, el disparador integrado usa `notifymod.bypass.trigger.notifymod.player_death` y la recepción de su
plantilla usa `notifymod.receive.notifymod.player_death`. Los nombres incluyen dos veces `notifymod` por diseño del
código actual. Los permisos por plantilla todavía no se aplican al comando `showcase`.

## Qué se guarda

Las ediciones de disparadores se guardan en `triggers_state.json`; las definiciones administradas siguen en
`managed/triggers/`. La auditoría `audit.jsonl` registra actor, acción y fecha, no constituye un historial de todos
los sucesos automáticos. Las escrituras se serializan en una cola de 256 tareas. Si está saturada, una edición se
rechaza antes de aplicarse; una entrada de auditoría que no cabe se avisa en el log.
