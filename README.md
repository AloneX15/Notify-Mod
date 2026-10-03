# Notify Mod

[![Build](https://github.com/AloneX15/Notify-Mod/actions/workflows/build.yml/badge.svg)](https://github.com/AloneX15/Notify-Mod/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AloneX15/Notify-Mod?sort=semver)](https://github.com/AloneX15/Notify-Mod/releases)
![Minecraft](https://img.shields.io/badge/Minecraft-26.x-62b47a)
![Loader](https://img.shields.io/badge/loader-Fabric-dbd0b4)

A data-driven engine for cinematic presentations and on-screen notifications: chat messages, HUD cards and full-screen showcases.

> 🚧 **En desarrollo (Fase 0).** El diseño completo está en [`NotifyMod_Plan.md`](NotifyMod_Plan.md).

## Características

Previstas para la 1.0:

- **Tres canales:** chat (MOTD), tarjetas de esquina en el HUD y *Showcase* (presentaciones cinemáticas).
- **Colocación** en 7 posiciones (centro, esquinas, arriba y abajo) con versión compacta del Showcase.
- **Motor de líneas de tiempo** con pistas de texto, texturas, formas, sonido y modelos 3D, y easings.
- **Formatos:** PNG, spritesheets `.mcmeta`, GIF, WebP y Lottie (pendiente de la prueba de viabilidad).
- **Prioridades y cola** (LOW, NORMAL, HIGH, CRITICAL) con caducidad y deduplicación.
- **Disparadores** automáticos (todos desactivados por defecto) y **audiencias** por grupo de LuckPerms.
- **Menú de administración** guiado dentro del juego y **API de Java** para otros mods.
- El servidor solo envía ids y argumentos; el cliente compone y dibuja. **Cliente obligatorio.**

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

## Configuración

Todavía no hay configuración. Prevista: `config/notifymod/server.json`, `triggers_state.json`, `managed/` (servidor)
y `client.json` (cliente). Comando principal: `/notify`.

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
