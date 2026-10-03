# Notify Mod

[![Build](https://github.com/AloneX15/Notify-Mod/actions/workflows/build.yml/badge.svg)](https://github.com/AloneX15/Notify-Mod/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AloneX15/Notify-Mod?sort=semver)](https://github.com/AloneX15/Notify-Mod/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-26.x-62b47a)
![Loader](https://img.shields.io/badge/loader-Fabric-dbd0b4)

A data-driven engine for cinematic presentations and on-screen notifications: chat messages, HUD cards and full-screen showcases.

> 🚧 **En desarrollo: fases 0–3 implementadas y fase 4 parcial.** El alcance disponible está en la
> [wiki bilingüe](docs/index.md). [`NotifyMod_Plan.md`](NotifyMod_Plan.md) describe también funciones futuras.

## Características

Disponibles ahora:

- **Tres canales:** chat (MOTD), tarjetas de esquina en el HUD y *Showcase* (presentaciones cinemáticas).
- **Colocación** en 7 posiciones (centro, esquinas, arriba y abajo) con versión compacta del Showcase.
- **Motor de líneas de tiempo** con pistas de texto, texturas, formas, sonido y modelos 3D, y easings.
- **Formatos:** PNG, spritesheets `.mcmeta`, GIF, WebP y Lottie (un subconjunto: formas, rellenos, trazos y
  transformaciones; ver [ADR 0001](docs/adr/0001-formatos-animados-fase-0.md)).
- **Prioridades y cola** (LOW, NORMAL, HIGH, CRITICAL) con caducidad y deduplicación.
- **Primeros disparadores** automáticos, desactivados por defecto, con comandos, audiencias, filtros, límites y auditoría.
- Grupos de LuckPerms mediante la API de permisos de Fabric; **integración real todavía sin verificar**.
- El servidor solo envía ids y argumentos; el cliente compone y dibuja. **Cliente obligatorio.**

Pendientes: resto del catálogo de disparadores, menú de administración, API pública, variables compartidas,
accesibilidad completa, pack de referencia y escenas. Esta versión no es el lanzamiento 1.0.

## Documentación y wiki

La wiki vive en [`docs/`](docs/index.md), junto al código, y se genera con MkDocs:

- [Primeros pasos](docs/es/primeros-pasos.md) / [Getting started](docs/en/getting-started.md).
- [Configuración y permisos](docs/es/configuracion.md) / [Configuration](docs/en/configuration.md).
- [Plantillas](docs/es/plantillas.md) / [Templates](docs/en/templates.md).
- [Presentaciones](docs/es/presentaciones.md) / [Presentations](docs/en/presentations.md).
- [Disparadores](docs/es/disparadores.md) / [Triggers](docs/en/triggers.md).
- [Desarrollo, CI y publicación de la wiki](docs/es/desarrollo.md) / [Development](docs/en/development.md).

La CI valida enlaces y genera el artifact `wiki-html` con las capturas de cliente más recientes.
GitHub Pages se habilita con su configuración de Actions y la variable `DOCS_PUBLISH=true`;
los pasos están en la guía de desarrollo.

## Instalación

1. Instala [Fabric Loader](https://fabricmc.net/use/) y [Fabric API](https://modrinth.com/mod/fabric-api).
2. Descarga el jar de tu versión de Minecraft desde [Releases](https://github.com/AloneX15/Notify-Mod/releases) y ponlo en `mods/`.

Build de desarrollo (último `main`, ya probado): [release `dev`](https://github.com/AloneX15/Notify-Mod/releases/tag/dev).

## Compatibilidad

| Minecraft | Estado |
|---|---|
| 26.1.x | ✅ |
| 26.2.x | ✅ |
| 26.3.x | ✅ |

Probado en la CI junto a Lithium y FerriteCore. Si encuentras un conflicto con otro mod, abre un
[issue](https://github.com/AloneX15/Notify-Mod/issues).

## Uso rápido

```
/notify send <mensaje>                                   Mensaje por el chat
/notify hud <mensaje> [placement=top_right] [priority=high] [key=x] [duration=10s] [chat=true]
/notify showcase notifymod:motd message="Reinicio en 5 minutos" [placement=...] [chat=true]
/notify showcase restart_warning minutos=5               Plantilla del servidor con argumentos
/notify cancel <clave|all>  ·  /notify clear  ·  /notify reload  ·  /notify validate  ·  /notify help
```

- **Colocación:** `center`, `top_left`, `top_right`, `bottom_left`, `bottom_right`, `top`, `bottom`.
- **Prioridad:** `low`, `normal`, `high` (acorta lo que hay en el centro) y `critical` (lo interrumpe y siempre va al
  centro).
- El jugador solo puede **ocultar los avisos de esquina** (tecla sin asignar por defecto en *Controles → Notify Mod*).
  El centro nunca se oculta.

## Configuración

- `config/notifymod/server.json`: `require_client` (por defecto `true`: sin el mod no se puede entrar),
  `missing_client_message`, `download_url`, `default_mirror_to_chat` y `max_commands_per_minute`.
- `config/notifymod/client.json`: `hide_corner_notifications`, `card_scale` (0.5–2), `max_visible_cards` (1–8) y
  `media_quality` (`high` o `low`: imágenes más pequeñas y menos fotogramas en equipos modestos).
- Plantillas: `data/<ns>/notify/templates/<nombre>.json` (datapacks) y
  `config/notifymod/managed/templates/<ns>/<nombre>.json`, que tienen prioridad. Ejemplo en
  `data/notifymod/notify/templates/restart_warning.json`.

### Permisos

Con la API de permisos de Fabric (LuckPerms la usa si está instalado); sin gestor, nivel de OP de respaldo:
`notifymod:command`, `notifymod:send`, `notifymod:hud`, `notifymod:showcase` y `notifymod:showcase.placement`
(OP 2); `notifymod:priority.high` (OP 2) y `notifymod:priority.critical` (OP 3); `notifymod:cancel` y
`notifymod:clear` (OP 2); `notifymod:reload` (OP 3) y `notifymod:validate` (OP 2). Recepción, concedida a todos por
defecto: `notifymod:receive.chat`, `notifymod:receive.hud` y `notifymod:receive.showcase`.

## Desarrollo

```bash
./gradlew :26.3:build              # compila y ejecuta los tests unitarios
./gradlew :26.3:runGameTest        # gametests de servidor
./gradlew :26.3:runClientGameTest  # test de cliente con capturas
./gradlew :26.3:runClient          # abre el juego
```

## Licencia

[MIT](LICENSE).

---

Creado por **TakumiStudios**.
