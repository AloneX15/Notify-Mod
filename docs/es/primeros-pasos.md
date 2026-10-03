# Primeros pasos

## Instalación

1. Instala Fabric Loader y Fabric API para tu versión de Minecraft.
2. Descarga el jar correspondiente a **26.1.2, 26.2 o 26.3** desde las
   [releases](https://github.com/AloneX15/Notify-Mod/releases). Java 25 es necesario.
3. Pon el jar en `mods/` del servidor y de cada cliente. Por defecto, el servidor rechaza clientes sin Notify Mod
   o sin su protocolo compatible. Una release `dev` es una compilación de desarrollo, no una versión estable.
4. Inicia el juego y el servidor. Ejecuta `/notify validate` como operador.

Las compilaciones y gametests de servidor se han probado en las tres versiones. La prueba de cliente local más
reciente es de 26.3; la CI ejecuta cliente y compat pack por versión. LuckPerms todavía requiere prueba de integración.

## Primer aviso

Con OP 2:

```mcfunction
/notify send Bienvenidos al servidor
/notify hud Reinicio en 5 minutos placement=top_right chat=true
/notify showcase notifymod:motd message="Empieza el evento" duration=10s
/notify showcase notifymod:restart_warning minutos=5
/notify clear
```

`send` escribe solo al chat. `hud` muestra una tarjeta. `showcase` reproduce una presentación o plantilla; la integrada
`notifymod:motd` solo necesita texto. Los comandos de envío actuales usan todos los jugadores conectados como audiencia;
las audiencias filtradas están disponibles en [disparadores](disparadores.md).

Las opciones van como `clave=valor`; usa comillas para valores con espacios. Posiciones: `center`, `top_left`,
`top_right`, `bottom_left`, `bottom_right`, `top`, `bottom`. Prioridades: `low`, `normal`, `high`, `critical`.
HIGH requiere OP 2; CRITICAL requiere OP 3 y siempre se coloca en el centro.

## Comandos disponibles

| Comando | Uso |
|---|---|
| `/notify send <mensaje>` | Chat |
| `/notify hud <mensaje> [opciones]` | Tarjeta |
| `/notify showcase <id> [opciones] [arg=valor]` | Presentación o plantilla |
| `/notify cancel <clave>` | Cancelar por clave; `all` cancela todo |
| `/notify clear` | Vaciar la cola de clientes conectados |
| `/notify reload` | Recargar ajustes del servidor, plantillas y disparadores |
| `/notify validate` | Listar errores de plantillas y disparadores; no valida recursos visuales del cliente |
| `/notify trigger …` | [Administrar disparadores](disparadores.md) |
| `/notify help` | Ayuda de comandos |

Opciones de `hud` y `showcase`: `placement`, `priority`, `key`, `duration`, `chat`, `message`.
No están implementados `/notify gui`, `/notify var`, `/notify schedule` ni `/notify scene`.

## El jugador

En Controles → Notify Mod se puede asignar **Ocultar avisos de esquina**; la tecla viene sin asignar para evitar
conflictos. Oculta cualquier colocación distinta del centro, incluidas `top` y `bottom`. Los avisos centrales y
CRITICAL permanecen visibles. Los controles de calidad están en [configuración](configuracion.md).

## Si algo falla

- «Falta el mensaje»: añade `message="…"` al usar una presentación sin plantilla.
- Plantilla desconocida o descartada: revisa `/notify validate`, los ids y la ruta del datapack.
- Un visual no aparece: F3+T y el log del cliente; distribuye su resource pack a todos los jugadores.
- Aviso de muerte no llega: comprueba activación, audiencia, permisos y cooldown; la entrega al fallecido espera a su
  reaparición y caduca a los 60 segundos.
- Cliente rechazado: instala Notify Mod y Fabric API de la versión correcta. `require_client=false` permite clientes
  sin mod con respaldo por chat, pero no les añade visuales.
