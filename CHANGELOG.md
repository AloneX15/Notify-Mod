# Changelog

## Sin publicar

- Fase 4 (primer bloque, todavía incompleta): registro validado de disparadores, eventos de muerte/reaparición,
  entrada/salida, chat, ciclo de vida y lanzamiento manual; audiencias, filtros, cooldowns y límites; comandos
  `/notify trigger`; estado persistente y auditoría; ejemplo de muerte desactivado y documentación es/en.

- Fase 0: decodificadores de GIF, WebP (fijo y animado, con TwelveMonkeys) y Lottie (subconjunto) a secuencias de
  fotogramas, con límites de tamaño, fotogramas y memoria. Decisión en `docs/adr/0001-formatos-animados-fase-0.md`.
- Un WebP dañado ya no puede dejar el decodificador leyendo sin fin.
- Fase 1 (núcleo, sin dependencias de Minecraft): prioridades, canales, colocación con las reglas de ocultación
  (CRITICAL siempre al centro), cola con tope, caducidad y deduplicación por clave, validación de argumentos,
  marcadores `{nombre}` y duraciones (`30s`, `5m`).
- Fase 1 (en el juego): comando `/notify` (`send`, `hud`, `showcase`, `cancel`, `clear`, `reload`, `validate`,
  `help`) con opciones `clave=valor`; permisos con la API de Fabric (LuckPerms) y nivel de OP de respaldo; plantillas
  de datapack y de `config/notifymod/managed/`; paquetes de red versionados; cliente obligatorio
  (`require_client`); Showcase de texto `notifymod:motd` en el centro y tarjetas apiladas en las 7 posiciones con
  fundidos; HIGH acorta y CRITICAL interrumpe; tecla "Ocultar avisos de esquina"; `server.json` y `client.json`
  versionados con copia `.bak`. Probado en 26.1.2, 26.2 y 26.3.
- Fase 2 (motor de líneas de tiempo): presentaciones en `assets/<ns>/notify/presentations/*.json`, compiladas al
  recargar recursos (F3+T); pistas `text`, `texture` (PNG, GIF, WebP y Lottie), `shape` (con degradado) y `sound`;
  anclas y coordenadas proporcionales a la pantalla; animaciones `fade`, `slide_*`, `scale`, `pop`, `rotate`,
  `typewriter` y en bucle `shake`, `pulse`, `spin`; 34 easings y `cubic_bezier()`; fondo `dim` y barras de cine;
  versión compacta en las esquinas; reloj que se para con la pausa; caché de texturas con presupuesto y LRU.
  Referencia en `docs/es/presentaciones.md`.
- Fase 3 (tubería de medios): spritesheets `.png.mcmeta` con el formato de las texturas animadas de Minecraft; pista
  `model` con ítems, bloques y mobs; `preload` para decodificar las imágenes al recargar recursos y al recibir la
  notificación; calidad baja (`media_quality`) con menos resolución y fotogramas; todo se decodifica fuera del hilo de
  render, con presupuesto de memoria y LRU.
- Las animaciones admiten la forma corta `"loop": "spin"`.
- Un campo de una presentación con un tipo equivocado da un error claro en el log en vez de una excepción de Gson.
- Un PNG que declara un tamaño enorme en su cabecera se rechaza antes de reservar memoria.
- Los mobs con equipo (zombis, esqueletos…) en una pista `model` desactivaban toda la capa de notificaciones hasta
  reiniciar. Ahora se dibujan, y si un modelo falla solo se desactiva ese modelo.

## 0.1.0

- Primera versión.
