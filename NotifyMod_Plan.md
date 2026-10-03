# Notify Mod: documento de diseño (Fabric · Minecraft 26.x+)

> **Estado:** Diseño aprobado. Fases 0 a 3 implementadas (preparación, núcleo, motor de líneas de tiempo y tubería de medios); siguiente: Fase 4 (disparadores).
> **Nombre:** Notify Mod · **Autor:** TakumiStudios · **modid:** `notifymod` · **Paquete Java:** `com.takumistudios.notifymod` · **Comando:** `/notify`
> **Tipo:** Framework de interfaz, utilidad de servidor y librería para otros mods.
> *El motor de presentaciones cinemáticas e interfaces temporales para Minecraft Fabric.*

---

## 0. Índice

1. Visión y requisitos
2. Revisión del borrador (correcciones)
3. Plataforma y estándar TakumiStudios
4. Arquitectura general
5. Contenido: dónde vive cada cosa
6. Canales, colocación y ocultación
7. Modo texto (MOTD)
8. Motor de líneas de tiempo
9. Formatos y tubería de medios
10. Capas del HUD y renderizado
11. Prioridades y cola
12. Variables compartidas y datos en vivo
13. Disparadores
14. Audiencia y LuckPerms
15. Menú de administración
16. API de Java
17. Red
18. Comandos
19. Cliente: opciones del jugador
20. Accesibilidad y seguridad visual
21. Rendimiento
22. Compatibilidad
23. Seguridad
24. Pack de referencia, wiki y documentación
25. Estructura del proyecto
26. Pruebas y calidad
27. Hoja de ruta
28. Próximas mejoras (1.1): Escenas, pantallas de espera y de carga
29. Riesgos
30. Decisiones abiertas
31. Casos de uso

---

## 1. Visión y requisitos

**Notify Mod** no es solo un mod para enviar mensajes. Es un **motor de interfaces basado en datos** que permite a administradores, creadores de modpacks y modders crear notificaciones y presentaciones audiovisuales en pantalla.

**Filosofía**
1. **Vanilla por defecto:** estética limpia que parece parte del juego base.
2. **Cinemático cuando importa:** presentaciones a pantalla completa para eventos, temporadas, jefes y sorteos.
3. **Basado en datos:** todo se configura con resource packs, datapacks y JSON, sin tocar código Java.
4. **API extensible:** los desarrolladores pueden crear sus propios elementos visuales y disparadores.
5. **Se configura sin escribir comandos:** un menú de administración didáctico dentro del juego.

| # | Requisito | Implicación de diseño |
|---|-----------|-----------------------|
| R1 | Tres canales: chat, tarjetas de esquina y Showcase | Una sola `Notification` se envía por uno o varios canales (§6) |
| R2 | **Colocación elegida por el admin**: centro o cualquier esquina | 7 posiciones para cualquier presentación, con modo compacto (§6) |
| R3 | **El jugador solo puede ocultar los avisos de esquina**; lo del centro no | Regla de ocultación por colocación (§6) |
| R4 | Notificación en **modo texto MOTD**, con copia opcional al chat | Presentación integrada `notifymod:motd` y `mirror_to_chat` (§7) |
| R5 | Presentaciones cinemáticas con línea de tiempo, easings y audio sincronizado | Motor de líneas de tiempo compilado (§8) |
| R6 | Formatos: texturas, spritesheets, GIF, WebP, Lottie y modelos 3D | Una sola tubería de fotogramas (§9) |
| R7 | **Disparadores** automáticos (por ejemplo, "un jugador muere"), **todos desactivados por defecto** | Marco de disparadores (§13) |
| R8 | **LuckPerms** administra permisos y audiencias por grupo | `fabric-permissions-api` con respaldo por nivel de OP (§14) |
| R9 | **Menú de administración** en el cliente, solo para admins, didáctico | Pantalla guiada con paquetes validados (§15) |
| R10 | Prioridades y cola inteligente | LOW, NORMAL, HIGH y CRITICAL con caducidad (§11) |
| R11 | Rendimiento: el servidor solo envía ids y variables | El cliente compone y dibuja todo (§17, §21) |
| R12 | **Cliente obligatorio** | Handshake que rechaza al cliente sin el mod (§17) |
| R13 | **Pack de referencia, wiki y documentación completa** | Entregable de la 1.0 (§24) |
| R14 | Pantallas de espera y de carga personalizables (próxima mejora) | Escenas en la versión 1.1 (§28) |

---

## 2. Revisión del borrador (correcciones)

| # | Borrador | Problema | Propuesta |
|---|----------|----------|-----------|
| 1 | `new Identifier("mimod", "ruleta")` | El constructor ya no es público y el nombre exacto cambia entre versiones | `Identifier.fromNamespaceAndPath(...)`. ⚠ Confirmar en 26.x |
| 2 | `registerElement(id, RuletaWidget.class)` | Registrar por clase obliga a usar reflexión y no valida parámetros | Registrar un `ElementType` con **codec de parámetros** (`MapCodec`), fábrica y renderizador (§16) |
| 3 | Presentaciones en `assets/` pero "el servidor define la lógica" | El servidor no ve los *assets* y no puede comprobar que el id existe | **Visual en `assets/`** (cliente) y **plantilla en `data/`** (servidor). Si el cliente no encuentra la presentación, usa el texto de respaldo (§5) |
| 4 | `duration: 7000` en ms | No dice qué reloj se usa ni qué pasa al pausar | Reloj monotónico del cliente, independiente de los FPS, que se **pausa con el juego** en mundos de un jugador (§8) |
| 5 | CRITICAL "interrumpe con flash y sonido" | Riesgo de fotosensibilidad y de abuso | Fundido de ~150 ms, flash limitado, permiso propio y límite de frecuencia. **CRITICAL siempre va al centro** (§11) |
| 6 | Prioridades sin límites | Una cola sin tope puede crecer o entregar avisos viejos | Tope, **caducidad**, deduplicación por clave y reglas por estado del juego (§11) |
| 7 | "Barras de progreso globales" sin mecanismo | Una barra compartida necesita estado vivo | **Variables compartidas** actualizadas por deltas (§12) |
| 8 | La ruleta con `winner="Notch"` | Cada cliente podría decidir un resultado distinto | **El servidor decide el resultado** y manda una semilla; el cliente solo anima hasta él (§16.4) |
| 9 | Placeholders sin límites | Inyección de formato y paquetes enormes | Máximo de argumentos y de longitud; solo texto, números y claves de traducción (§23) |
| 10 | Interacción de la ruleta | Cualquier botón exige paquetes cliente → servidor | **Sin interacción en la 1.0** |
| 11 | Sin accesibilidad | Parpadeos, movimiento y texto largo | Límite de destellos fijo en el motor, contraste, escalas de GUI y subtítulos (§20) |
| 12 | "0 % de lag de red" | Falta qué se envía y con qué límites | Lista cerrada de paquetes y protocolo versionado (§17) |
| 13 | Referencias a *El Dedsafio* y *Squid Craft* | Marcas y contenidos de terceros | Solo como inspiración. El pack de ejemplo usa **recursos propios o CC0** (§24) |
| 14 | Sin automatización | El admin tendría que lanzar todo a mano | **Disparadores** configurables (§13) |
| 15 | Audiencia solo implícita ("a todos") | No se podía enviar "a un grupo" | Audiencia por **grupo de LuckPerms**, permiso, equipo, selector, dimensión o radio (§14) |
| 16 | Showcase solo "en el centro" | No se podía mandar una presentación a una esquina | **Colocación** en 7 posiciones para cualquier presentación (§6) |
| 17 | Notificación simple sin definir | Un aviso de texto no necesita una presentación completa | `notifymod:motd` (**solo texto**) con `mirror_to_chat` opcional (§7) |
| 18 | Todo se configuraba con JSON y comandos | Un admin sin conocimientos técnicos no podría usarlo | **Menú de administración** guiado (§15) |
| 19 | Espacio de nombres `notify:` | Lo habitual en Fabric es que el espacio de nombres sea el modid, y mezclar dos aliases confunde | Todo el contenido propio usa **`notifymod:`**. El comando sigue siendo `/notify` |

---

## 3. Plataforma y estándar TakumiStudios

| Elemento | Decisión | Nota |
|----------|----------|------|
| Minecraft | **26.x en adelante**, todas las 26.x estables con Fabric API, **un jar por versión** | Al empezar: 26.1.2, 26.2 y 26.3. ⚠ Comprobarlo con la API de versiones de Fabric al crear el proyecto |
| Java | **25** | |
| Build | Gradle + Fabric Loom + **Stonecutter** (multiversión) | Se genera con `new-mod.sh` de la skill `takumistudios-fabric-mod` |
| Lado | **Cliente y servidor**, ambos obligatorios (R12) | `"environment": "*"` |
| Dependencias obligatorias | Fabric API | |
| Incluidas en el jar | `fabric-permissions-api` | |
| Dependencias opcionales | LuckPerms (API, para listar grupos), Mod Menu, LiveEvents Mod | Siempre `compileOnly`, en `suggests` y en clases aisladas |
| Mixins | **Ninguno previsto**, salvo los disparadores del grupo C (§13.2) | Cada uno con `require = 0` y documentado en `MIXINS.md` |

**Normas obligatorias de TakumiStudios que aplica este diseño**
- **Autoría:** `"authors": ["TakumiStudios"]`, "Creado por **TakumiStudios**" en el README y en las notas de cada release, y `Implementation-Vendor: TakumiStudios` en el jar.
- **Fiabilidad:** un fallo del mod nunca tumba el juego. Todo callback va dentro de `try/catch` que desactiva solo esa función. El servidor **nunca confía en el cliente**: cada paquete se valida. Config versionada con copia `.bak`. Escrituras atómicas fuera del hilo principal.
- **Rendimiento:** nada pesado en el tick ni en el render, sin asignaciones por fotograma, y red por deltas y solo a quien interesa.
- **Compatibilidad:** APIs de Fabric antes que mixins, integraciones opcionales aisladas y un *compat pack* en la CI (Lithium, FerriteCore y LuckPerms).
- **Calidad:** SemVer, `CHANGELOG.md`, `SECURITY.md`, `LICENSE`, `en_us.json` y `es_es.json` con las mismas claves, teclas sin conflicto y `docs/registro-de-errores.md`.

> ⚠ **Hay que comprobar los nombres exactos de la API en 26.x antes de programar:** `HudElementRegistry`, `Identifier`, renderizado de GUI, entidades en pantalla, eventos de Fabric API y payloads de red. Es una tarea de la Fase 0.

---

## 4. Arquitectura general

### 4.1 Principio rector: el servidor decide y el cliente dibuja

```
┌───────────────────────────── SERVIDOR (autoridad) ─────────────────────────────┐
│                                                                                │
│  Comando /notify · Menú admin · Disparador · API ──► PermissionGate            │
│                                                          │                     │
│                                                          ▼                     │
│                          TemplateRegistry ──► NotificationDispatcher           │
│                          (data/ + managed/)    (audiencia, argumentos,         │
│                                                 límites, cooldowns)            │
│                                                          │                     │
│        TriggerManager ◄── eventos de Fabric API          ▼                     │
│        (solo los activados)                        NetworkSync                 │
│        VariableStore ── deltas ───────────────────────────┤                    │
└───────────────────────────────────────────────────────────┼────────────────────┘
                                                            │ id + argumentos + colocación
┌───────────────────────────── CLIENTE (presentación) ──────▼────────────────────┐
│  ClientNotificationState ──► NotificationQueue (prioridades, caducidad)        │
│                                  │                                             │
│                                  ▼                                             │
│  PresentationRegistry ──► TimelineRuntime ──► Renderer (HUD cards / Showcase)  │
│  (assets/, compiladas)    (reloj, easings)     MediaCache (atlas, LRU)         │
└────────────────────────────────────────────────────────────────────────────────┘
```

**Consecuencias**
- El servidor solo envía el **id de la plantilla, los argumentos y la colocación**. No manda imágenes, animaciones ni cálculos.
- El cliente hace todo el cálculo y el dibujado, con interpolación para animaciones fluidas a 60 FPS o más.
- Los disparadores, las audiencias y los permisos **viven solo en el servidor**.

### 4.2 Componentes

| Componente | Lado | Responsabilidad |
|------------|------|-----------------|
| `TemplateRegistry` | Servidor | Carga las plantillas (`data/` y `config/notifymod/managed/`) |
| `NotificationDispatcher` | Servidor | Resuelve audiencia, valida argumentos, aplica límites y envía |
| `TriggerManager` | Servidor | Registra *listeners* solo para los disparadores activados (§13) |
| `AudienceResolver` | Servidor | Resuelve `include` y `exclude` por grupo, permiso, equipo, selector... |
| `PermissionGate` | Servidor | Comprueba permisos con `fabric-permissions-api` (§14) |
| `VariableStore` | Servidor | Variables compartidas y envío de deltas (§12) |
| `AdminService` | Servidor | Valida y aplica las ediciones del menú de administración (§15) |
| `AuditLog` | Servidor | Registro de quién hizo qué |
| `PresentationRegistry` | Cliente | Carga y **compila** las presentaciones al recargar recursos |
| `NotificationQueue` | Cliente | Cola por prioridad con caducidad y deduplicación (§11) |
| `TimelineRuntime` | Cliente | Reloj monotónico, pistas, animaciones y easings (§8) |
| `MediaCache` | Cliente | Atlas de fotogramas, presupuesto de memoria y LRU (§9) |
| `HudRenderer` | Cliente | Dibuja tarjetas y Showcase en sus capas del HUD (§10) |
| `AdminScreens` | Cliente | Pantallas del menú de administración (§15) |

---

## 5. Contenido: dónde vive cada cosa

| Ubicación | Qué contiene | Lado |
|-----------|--------------|------|
| `assets/<ns>/notify/presentations/*.json` | **Presentación visual**: líneas de tiempo, texturas, textos, sonidos. Se recarga con F3+T | Cliente |
| `assets/<ns>/lang/*.json` | Textos traducibles | Cliente |
| `data/<ns>/notify/templates/*.json` | **Plantilla**: canales, prioridad, audiencia, colocación, cooldown, permiso y texto de respaldo | Servidor |
| `data/<ns>/notify/triggers/*.json` | **Disparadores** (todos con `enabled: false`) | Servidor |
| `config/notifymod/server.json` | Ajustes del servidor | Servidor |
| `config/notifymod/triggers_state.json` | Qué disparadores ha activado el admin y los cambios de parámetros | Servidor |
| `config/notifymod/managed/` | Plantillas y disparadores **creados desde el menú**. Tienen **prioridad sobre el datapack** y se pueden editar a mano | Servidor |
| `config/notifymod/client.json` | Opciones del jugador | Cliente |

Todos los archivos de configuración están **versionados** (`format_version`), con valores por defecto seguros, validación de rangos y copia `.bak` si se corrompen. Las escrituras son **atómicas y fuera del hilo principal**.

### 5.1 Por qué hay dos mitades

El servidor define la lógica y el cliente los visuales. El servidor **no puede ver** los *assets*, así que no comprueba que una presentación exista. Por eso:
- La plantilla incluye un **texto de respaldo** (`fallback`) para el chat y para las tarjetas.
- Si el cliente no encuentra la presentación, muestra el respaldo y lo anota **una sola vez** en el log.
- Para que los visuales coincidan en todos los clientes se recomienda **distribuir el resource pack desde el servidor** (`resource-pack` en `server.properties`).

### 5.2 Ejemplo de plantilla (servidor)

```json
{
  "format_version": 1,
  "_comment": "Aviso de reinicio del servidor",
  "id": "notifymod:restart_warning",
  "channels": ["showcase", "chat"],
  "presentation": "notifymod:motd",
  "placement": "top",
  "priority": "HIGH",
  "audience": { "include": [ { "type": "all" } ] },
  "message": { "translate": "notifymod.restart.warning", "with": ["{minutos}"] },
  "mirror_to_chat": true,
  "fallback": { "chat": { "translate": "notifymod.restart.warning", "with": ["{minutos}"] } },
  "key": "restart_warning",
  "cooldown": "30s"
}
```

---

## 6. Canales, colocación y ocultación

### 6.1 Los tres canales

Una `Notification` = plantilla + argumentos + prioridad + audiencia + canales + colocación. Se puede enviar por uno o por varios canales a la vez.

| Canal | Qué es | Uso ideal |
|-------|--------|-----------|
| 💬 **Chat** (MOTD) | Mensajes en el chat con separadores, colores, iconos y placeholders | Registros permanentes y recordatorios |
| 🎴 **Tarjetas (HUD)** | Tarjetas emergentes no intrusivas, apiladas y con tiempo de vida | Avisos de reinicio, pequeños logros, información nueva |
| ✨ **Showcase** | Presentaciones a pantalla completa o en versión compacta, con fondos, animaciones y audio sincronizado | Inicio de temporada, eventos, jefes y sorteos |

### 6.2 Colocación (siete posiciones)

`center`, `top_left`, `top_right`, `bottom_left`, `bottom_right`, `top` y `bottom`.

- Por defecto, el Showcase va en `center` y las tarjetas en `top_left`.
- El admin elige la colocación **en la plantilla, en el disparador, al lanzar o en el menú**: `/notify showcase <id> placement=bottom_right`. Hace falta el permiso `notifymod.showcase.placement`.
- **Cualquier presentación se puede mandar a cualquiera de las siete posiciones**, también la más cinemática.

### 6.3 Versión compacta de un Showcase

La presentación se dibuja en un **lienzo de referencia (16:9)**, y el motor proyecta ese lienzo a un rectángulo de la zona elegida (por ejemplo, el 28 % del ancho de la pantalla).
- Las pistas de pantalla completa (`dim`, viñeta, barras de cine, destello y efectos de cámara) se **omiten** en este modo.
- La presentación puede definir una variante `docked` con sus propias pistas o ajustes para la versión compacta.
- El audio se mantiene, salvo que la variante `docked` lo desactive.

### 6.4 Qué puede ocultar el jugador

| Colocación | Ejemplos | ¿Puede ocultarlo el jugador? |
|------------|----------|------------------------------|
| `center` | Presentación cinemática a pantalla completa | **No, nunca** |
| `top_left`, `top_right`, `bottom_left`, `bottom_right`, `top`, `bottom` | Tarjeta del HUD **o Showcase en versión compacta** | **Sí** |
| **CRITICAL** | Cierre del servidor, muerte permanente | **No.** Siempre va al centro e ignora la colocación |

- El jugador tiene **un solo interruptor**, "Ocultar avisos de esquina" (en Mod Menu y con una tecla opcional). Al activarlo, las notificaciones de esquina **se descartan sin mostrarse** (quedan en el historial y en el chat de respaldo si la plantilla lo define).
- El Showcase **del centro** no tiene ninguna opción de ocultar. Tampoco puede saltarlo el jugador: cancelar una presentación es cosa del admin (`/notify cancel`).
- **Quitar un canal a un grupo es decisión del admin**, con LuckPerms (`notifymod.receive.showcase`, por ejemplo para un grupo de cámaras) y nunca del jugador.

---

## 7. Modo texto (MOTD)

- Presentación integrada **`notifymod:motd`**: solo texto con formato (colores, negrita e iconos de fuente), con entrada y salida suaves. No necesita ningún recurso extra. Es el Showcase más simple.
- Se coloca en el centro o en cualquier esquina, como cualquier otra presentación.
- **`mirror_to_chat`:** si el admin lo pide, **el mismo texto se envía también por el chat**, con el formato del canal de chat (separadores, colores, iconos y placeholders). Es una opción por plantilla, por disparador y por envío:
  `/notify showcase notifymod:motd message="Reinicio en 5 minutos" chat=true`
  Por defecto está desactivada. El ajuste del servidor `default_mirror_to_chat` cambia ese valor por defecto.
- **Un texto, dos canales:** la plantilla define un único `message` con claves de traducción y el chat y el Showcase lo usan a la vez. Se traduce al idioma de cada jugador.
- **Atajos**
  - `/notify send <mensaje>`: chat.
  - `/notify hud <mensaje>`: tarjeta de esquina.
  - `/notify showcase notifymod:motd message=...`: centro o la colocación elegida.

---

## 8. Motor de líneas de tiempo

Funciona como un "After Effects" dentro de Minecraft: una **línea de tiempo** sincroniza visuales y audio.

### 8.1 Ejemplo de presentación (cliente)

```json
{
  "format_version": 1,
  "_comment": "Presentación cinemática de ejemplo (versión del borrador, corregida)",
  "id": "notifymod_example:cinematic_purga",
  "canvas": "16:9",
  "background": { "type": "dim", "color": "#220000", "opacity": 0.8 },
  "duration": 7000,
  "tracks": [
    { "type": "sound", "time": 0, "sound": "notifymod_example:siren_alarm" },
    {
      "type": "texture", "time": 500,
      "file": "notifymod_example:textures/skull.gif",
      "anchor": "center", "size": [0.25, 0.25],
      "animation_in": { "type": "pop", "easing": "ease_out_elastic", "duration": 600 }
    },
    {
      "type": "text", "time": 1500,
      "content": { "translate": "notifymod_example.purga.title" },
      "font": "minecraft:illageralt", "color": "#FF0000",
      "anchor": "center", "offset": [0, 0.2],
      "animation_in": { "type": "slide_right", "easing": "ease_out_expo", "duration": 500 }
    }
  ],
  "docked": { "omit": ["background"] }
}
```

### 8.2 Reloj y tiempos

- Los tiempos van en **milisegundos desde el inicio de la presentación**.
- El reloj es **monotónico del cliente**, independiente de los FPS, y se **pausa con el juego** en mundos de un jugador.
- La interpolación usa el tiempo real entre fotogramas, así que las animaciones son fluidas a cualquier FPS.
- Las presentaciones se **compilan al recargar recursos** en un plan inmutable: sin parsear JSON ni crear objetos en el render.

### 8.3 Pistas

| Pista | Qué hace |
|-------|----------|
| `sound` | Reproduce un sonido sincronizado con la línea de tiempo |
| `texture` | Imagen estática o animada (§9) |
| `text` | Texto con fuente, color, formato y claves de traducción |
| `shape` | Rectángulos, degradados y líneas |
| `model` | Ítem, bloque o entidad girando en pantalla |
| `widget` | Elemento registrado por la API, como una ruleta (§16) |
| `group` | Agrupa pistas para moverlas o animarlas juntas |
| `progress_bar`, `counter`, `countdown` | Elementos enlazados a variables compartidas (§12) |

- **Capas** con orden Z.
- **Coordenadas normalizadas con anclas** (centro, esquinas, bordes) que respetan la escala de GUI y las pantallas ultrapanorámicas.
- **Marcadores de texto** `{nombre}` reemplazados por los argumentos que manda el servidor.

### 8.4 Animaciones y easings

- **Entrada, salida y bucle:** `fade`, `slide`, `scale`, `pop`, `rotate`, `shake`, `typewriter` y `wipe`.
- **Curvas (easings):** `linear` y la familia estándar `sine`, `quad`, `cubic`, `expo`, `back`, `elastic` y `bounce`, cada una en `in`, `out` e `in_out` (por ejemplo `ease_out_elastic` para un rebote o `ease_out_expo` para un frenado suave).

### 8.5 Fondos y efectos de pantalla

`dim` (oscurecido), viñeta, **barras de cine** (letterbox) y destello limitado (§20). Estas pistas se omiten en la versión compacta.

### 8.6 Otros campos

`on_end` (qué hacer al terminar), `preemptible` (si otra presentación de mayor prioridad puede interrumpirla), `show_when_hud_hidden` y `render_over_screens` (§10).

---

## 9. Formatos y tubería de medios

| Formato | Uso |
|---------|-----|
| **PNG (texturas) y fuentes** | Iconos estáticos y tipografías propias |
| **Spritesheets `.mcmeta`** | Animaciones nativas de Minecraft |
| **GIF** | Archivos animados |
| **WebP animado** | Archivos animados con menos peso |
| **Lottie (`.json`)** | Animaciones vectoriales de alta calidad y poco peso |
| **Modelos 3D** | Ítems, bloques y mobs girando en pantalla |

### 9.1 Una sola tubería

Todo lo animado (PNG animado, `.mcmeta`, GIF, WebP y Lottie) se convierte en una **secuencia de fotogramas en un atlas** durante la carga, en un **hilo de trabajo**. En el render solo se elige el fotograma, con el mismo coste para todos los formatos.

- **Presupuesto de memoria de vídeo configurable**, límites de fotogramas y de tamaño, y **descarte por inactividad** (LRU).
- `preload` opcional para las presentaciones que se van a usar pronto.
- **Calidad baja** = menos fotogramas y menor resolución.
- **Sin librerías nativas.** El jar debe funcionar en todos los sistemas.

### 9.2 Cómo se decodifica cada formato

| Formato | Técnica | Notas |
|---------|---------|-------|
| GIF | Lector del JDK | Se limita el número de fotogramas y el tamaño |
| WebP | Decodificador **Java puro** (por ejemplo el plugin de TwelveMonkeys) | ⚠ Comprobar licencia, peso y que sirva para WebP animado |
| Lottie | **Rasterizado previo** de un subconjunto (formas, rellenos, trazos, transformaciones y recortes de trazo) con un renderizador propio o una librería Java pura | ⚠ Evaluar en la Fase 0. Las librerías nativas quedan descartadas |

> **Puerta de decisión (Fase 0):** ✅ superada. Lottie entra en la 1.0 acotado a un subconjunto documentado (formas, rellenos, trazos, transformaciones y fotogramas clave); WebP usa TwelveMonkeys. Detalles en `docs/adr/0001-formatos-animados-fase-0.md`.

### 9.3 Modelos 3D

- **Ítems y bloques:** con el renderizado de GUI de Minecraft.
- **Mobs:** mediante una **entidad de cliente que no se añade al mundo** y no hace *tick*, que se cachea y se reutiliza.
- ⚠ Confirmar los nombres exactos de la API en 26.x.

---

## 10. Capas del HUD y renderizado

- Dos capas con **`HudElementRegistry`**: `notifymod:hud_cards` y `notifymod:showcase`. Se colocan con orden relativo a las capas vanilla para convivir con minimapas y mods de armadura.
- **Showcase no bloquea:** el jugador sigue jugando con normalidad. `block_input: true` solo para presentaciones que lo pidan expresamente.
- **`show_when_hud_hidden` (F1):** por presentación. **F1 oculta todas las capas del HUD, incluidas las de Notify**, así que una presentación marcada así necesita un camino de dibujo distinto. ⚠ Comprobar en 26.x.
- **`render_over_screens`:** se dibuja aunque haya un inventario abierto. Por defecto, solo CRITICAL. ⚠ Comprobar cómo se dibuja bajo una `Screen` en 26.x.
- **Sin asignaciones por fotograma:** `Component`, textos formateados, anchos de texto y texturas se **cachean** y se recalculan solo cuando cambian los datos.
- Todo el código que toca el renderizado va detrás de una capa de aislamiento para que un cambio de versión solo afecte a esa capa.

---

## 11. Prioridades y cola

| Prioridad | Comportamiento |
|-----------|----------------|
| 🟢 **LOW** | Información secundaria. Espera su turno en la cola y **caduca** si tarda demasiado |
| 🟡 **NORMAL** | Anuncios del servidor. Entran por orden |
| 🟠 **HIGH** | Eventos importantes. **Acorta la salida** de la presentación actual (fundido rápido) para entrar antes |
| 🔴 **CRITICAL** | **Interrumpe** cualquier presentación con un fundido corto (~150 ms) más una alerta (sonido y flash limitado). Siempre va al centro. Requiere permiso propio y tiene límite de frecuencia |

- **Tarjetas de esquina:** apiladas (máximo visible configurable) y con tiempo de vida.
- **Showcase:** uno activo a la vez en el centro, y otro compacto por esquina si lo pide el admin.
- **Cola con tope**, **caducidad por prioridad**, **deduplicación por `key`** (la nueva sustituye a la antigua) y agrupación de repetidos.
- **Estados del juego:**
  - No se muestran presentaciones en pantallas de carga, menú principal ni pantalla de muerte.
  - Con una `Screen` abierta, la cola espera, salvo CRITICAL.
  - Los avisos caducados no se muestran al volver.
  - Un CRITICAL persistente (por ejemplo, el cierre del servidor) sí se muestra.

---

## 12. Variables compartidas y datos en vivo

Una variable es un valor que el servidor actualiza y que los elementos de la presentación **muestran en vivo**.

- `/notify var set <nombre> <valor>` o la API.
- Elementos enlazados: **`progress_bar`**, **`counter`** y **`countdown`**.
- Se envían **deltas** y solo a quien tiene una presentación que usa la variable. Cada variable tiene un tope de frecuencia de actualización.
- **`countdown`:** usa la **hora del servidor** con corrección de desfase, para que todos los jugadores vean el mismo tiempo restante (reinicios y cuentas atrás de eventos).
- Casos de uso: barras de progreso globales, contadores de eliminaciones, "quedan 12 jugadores" y cuentas atrás de reinicio.

---

## 13. Disparadores

Un **disparador** conecta un suceso del juego con una notificación, de forma automática.

> **Todos los disparadores vienen desactivados** (`enabled: false`). El admin activa cada uno. Un disparador desactivado **no registra ningún *listener*** y no cuesta nada.

### 13.1 Ejemplo

`data/notifymod/notify/triggers/player_death.json`

```json
{
  "format_version": 1,
  "_comment": "Cuando un jugador muere, mostrar la animación a un grupo",
  "type": "player_death",
  "enabled": false,
  "conditions": { "dimension": ["minecraft:overworld"], "cause_group": ["jugadores"] },
  "template": "notifymod:death_cinematic",
  "placement": "center",
  "priority": "HIGH",
  "audience": {
    "include": [ { "group": "vip" }, { "group": "staff" } ],
    "exclude": [ { "permission": "notifymod.bypass.trigger.player_death" } ]
  },
  "args": { "jugador": "$player", "causa": "$death_message", "prefijo": "$prefix" },
  "cause_delivery": "after_respawn",
  "cooldown": "10s",
  "max_per_minute": 6,
  "coalesce": { "window": "3s", "mode": "queue" }
}
```

### 13.2 Catálogo

⚠ Los nombres de los eventos de Fabric API se confirman en la Fase 0.

| Grupo | Disparadores | Cómo se detectan |
|-------|--------------|------------------|
| **Jugador (A: evento directo)** | muere · muere a manos de un jugador · muere por un mob · muere por un tipo de daño (lava, caída, vacío, explosión) · reaparece · entra · sale · cambia de dimensión · escribe una palabra clave en el chat | Eventos de Fabric API |
| **Jugador (A: con estado)** | **primera vez que entra** · primera muerte · N-ésima muerte | Datos guardados en el servidor con escritura atómica |
| **Mundo y entidades (A)** | matar un jefe o una entidad concreta (Dragón, Wither, entidad con nombre) · aparece un jefe · romper o colocar un bloque concreto | Eventos de Fabric API filtrados por tipo o etiqueta |
| **Servidor (A)** | servidor listo · servidor a punto de cerrar · programado (fecha y hora) · cuenta atrás de reinicio | Ciclo de vida del servidor y programador propio |
| **Con sondeo (B)** | amanecer, anochecer, medianoche · cambio de clima · entrar o salir de una zona definida · alcanzar N jugadores conectados | Comprobación cada N ticks, **solo si el disparador está activo** |
| **Con mixin (C) ⚠** | **logro conseguido** · subida de nivel · cambio de modo de juego · estructura descubierta | Puede necesitar un mixin con `require = 0`, documentado en `MIXINS.md`. Si no es viable en alguna versión, el disparador queda **"no disponible"** y se explica en `/notify trigger info` |
| **Manual y de integración** | `/notify trigger fire <id>` (bloques de comandos y funciones) · `NotifyAPI.fireTrigger(...)` (otros mods) · eventos de LiveEvents Mod (opcional) | Comando y API |

### 13.3 Datos de cada suceso

Placeholders disponibles: `$player`, `$killer`, `$death_message`, `$entity`, `$block`, `$dimension`, `$advancement`, `$time`, `$prefix` y `$suffix`.

Las **coordenadas** (`$x`, `$y`, `$z`) solo están disponibles si el disparador declara `allow_coordinates: true`, para no revelar posiciones por accidente.

### 13.4 Condiciones y filtros

Dimensión, tipo o etiqueta de daño, tipo de asesino, tipo de entidad o bloque, **grupo o permiso de quien lo causa** (por ejemplo, solo cuando muere un jugador del grupo `jugadores` y no un `staff`), mundo hardcore o no, y `chance`.

### 13.5 Entrega a quien lo causa

`cause_delivery`:
- **`after_respawn`** (por defecto): el Showcase no se muestra en la pantalla de muerte, así que quien murió lo ve al reaparecer.
- `skip`: no se le muestra.
- `immediate`: solo para sucesos sin pantalla de muerte.

### 13.6 Control de avalanchas

`cooldown` global, `per_player_cooldown`, `max_per_minute`, `coalesce` (`replace`, `queue` o `aggregate`) y un **límite de envíos por segundo para todos los disparadores**. Evita que una muerte en masa o un jugador que entra y sale sin parar sature la cola.

### 13.7 Gestión por el admin

```
/notify trigger list
/notify trigger info <id>
/notify trigger enable <id>   · disable <id>
/notify trigger set <id> <campo> <valor>
/notify trigger test <id> [as <jugador>]
/notify trigger fire <id>
/notify trigger audience <id>
```

- `test` simula el suceso con datos de prueba, **sin que muera nadie**.
- `audience` lista **qué jugadores recibirían el aviso ahora mismo**.
- Los cambios se guardan en `triggers_state.json` y quedan en el registro de auditoría. Activar un disparador no obliga a editar el datapack.

---

## 14. Audiencia y LuckPerms

LuckPerms se integra con **`fabric-permissions-api`**, la misma librería que usa LiveEvents Mod. Con otro gestor compatible o sin ninguno, el mod usa el **nivel de OP de respaldo**.

### 14.1 Audiencia

Se combina con `include` y `exclude`:

| Tipo | Qué selecciona |
|------|----------------|
| `all` | Todos los jugadores conectados |
| **`group`** | Miembros de un **grupo de LuckPerms** |
| `permission` | Quien tenga un nodo de permiso |
| `team` | Un equipo de Minecraft |
| `selector` | `@a`, `@a[distance=..50]`... |
| `dimension` y `radius` | Por dimensión o cercanía |
| `involved` | Quien causó el suceso o su asesino |

- **Grupo de LuckPerms:** se resuelve comprobando el nodo **`group.<nombre>`**, que LuckPerms concede a los miembros del grupo (con herencia). ⚠ Probarlo en el compat pack con LuckPerms. Sin LuckPerms, `group` se ignora con un aviso y se usan `permission` o `team`.
- La audiencia se resuelve **una vez por envío**, solo entre los jugadores conectados, y respeta los *contextos* de LuckPerms (mundo, servidor) porque la comprobación se hace con el propio jugador.
- **Etiquetas de LuckPerms en los mensajes:** `$prefix` y `$suffix` salen de los metadatos del jugador para escribir "[VIP] Notch ha muerto". ⚠ Confirmar en la librería.

### 14.2 Nodos de permiso

Todos con nivel de OP de respaldo.

| Nodo | OP | Para qué |
|------|----|----------|
| `notifymod.command` | 2 | Acceso a `/notify` |
| `notifymod.send` · `.hud` · `.showcase` | 2 | Enviar por cada canal |
| `notifymod.showcase.placement` | 2 | Elegir la colocación al lanzar |
| `notifymod.priority.high` · `.critical` | 2 · 3 | Usar esas prioridades |
| `notifymod.cancel` · `.clear` | 2 | Cancelar o vaciar |
| `notifymod.reload` · `.validate` · `.debug` | 3 · 2 · 3 | Mantenimiento |
| `notifymod.var` · `.schedule` | 3 | Variables compartidas y programación |
| `notifymod.trigger.list` · `.info` | 2 | Ver disparadores |
| `notifymod.trigger.manage` | 3 | **Activar, desactivar y editar** disparadores |
| `notifymod.trigger.test` | 3 | Probar un disparador |
| `notifymod.trigger.fire` · `.fire.<id>` | 3 | Lanzar a mano (todos o uno concreto) |
| `notifymod.gui` | 3 | **Abrir el menú de administración** |
| `notifymod.gui.<pestaña>` (`send`, `templates`, `triggers`, `audiences`, `schedule`, `variables`, `settings`, `audit`) | 3 | Ver y usar cada pestaña |
| `notifymod.gui.edit` | 3 | Guardar cambios desde el menú (sin él, el menú es de solo lectura) |
| `notifymod.receive.chat` · `.hud` · `.showcase` | 0 | **Recibir** cada canal. Quitárselo a un grupo lo deja sin avisos |
| `notifymod.receive.<plantilla>` | 0 | Recibir una plantilla concreta |
| `notifymod.bypass.trigger.<id>` | — | Quedar exento de un disparador |
| `notifymod.scene.start` · `.end` (1.1) | 2 | Iniciar y terminar escenas |
| `notifymod.scene.receive` (1.1) | 0 | Recibir escenas |
| `notifymod.bypass.scene` (1.1) | — | Quedar exento de las escenas |

Todos los cambios de disparadores, los lanzamientos manuales y las ediciones del menú se anotan en el **registro de auditoría** (quién, qué y cuándo).

### 14.3 Recetas

| Quiero... | Permisos |
|-----------|----------|
| Que los moderadores solo envíen avisos | `notifymod.command`, `notifymod.send`, `notifymod.hud` |
| Que un grupo de organizadores configure los disparadores | `notifymod.trigger.*`, `notifymod.gui`, `notifymod.gui.triggers`, `notifymod.gui.edit` |
| Que el grupo `cámaras` no reciba presentaciones | Negar `notifymod.receive.showcase` al grupo |
| Que el personal no vea el aviso de muertes | `notifymod.bypass.trigger.player_death` |

---

## 15. Menú de administración

### 15.1 Acceso

`/notify gui`, o una tecla (**sin asignar por defecto**, para no chocar con otros mods). Solo se abre con `notifymod.gui`. El servidor **no envía datos de administración a nadie más**. Cada pestaña tiene su permiso, así que un moderador solo ve lo que puede usar.

### 15.2 Principios de diseño (didáctico)

- **Modo guiado** por defecto: asistentes paso a paso con lenguaje sencillo ("¿Cuándo quieres que salga el aviso?"), ejemplos y una frase que resume lo configurado. Por ejemplo: *"Cuando un jugador del grupo jugadores muere, mostrar Eliminación a todos en el centro."*
- **Ayuda en contexto:** cada campo tiene un tooltip con qué hace, valores típicos y un ejemplo, y un botón "?" que abre la ayuda.
- **Vista previa en vivo** en cada paso, con el mismo motor que ven los jugadores. Botones **"Probar en mí"** (solo el admin) y **"Probar para todos"**.
- **Selector visual de colocación:** una pantalla dibujada con 7 zonas donde se hace clic.
- **Avisos previos y errores claros:** "Este aviso llegará a 148 jugadores. ¿Seguro?" y validación en lenguaje llano.
- **Deshacer y restaurar:** nada se aplica hasta pulsar "Guardar", cada cambio se puede deshacer y hay "Restaurar valores por defecto".
- **Modo avanzado:** interruptor para ver y editar el JSON, con el esquema como guía.
- **Tour de primer uso** de un minuto y plantillas de ejemplo ("Aviso de reinicio", "Bienvenida", "Eliminación de jugador").
- **Accesible:** navegación con teclado, contraste suficiente, escalas de GUI 1–4 y textos traducidos.

### 15.3 Pestañas

| Pestaña | Qué permite |
|---------|-------------|
| Inicio | Estado: cola actual, disparadores activos, últimos avisos y alertas de configuración |
| Enviar aviso | Asistente: mensaje → canales (chat, esquina, centro, o MOTD con copia al chat) → colocación → prioridad → audiencia → vista previa → enviar o programar |
| Presentaciones | Galería con miniaturas y descripción; elegir una y ajustar sus textos y parámetros |
| Plantillas | Crear y editar plantillas de servidor |
| Disparadores | Lista con **interruptores de activar y desactivar**; asistente "Cuándo / Quién lo causa / Qué se muestra / A quién / Límites"; botón "Probar" |
| Audiencias y grupos | Elegir grupos de LuckPerms, permisos, equipos, dimensión o jugadores concretos; ver "quién lo recibiría ahora" |
| Programación | Calendario de avisos y cuentas atrás de reinicio |
| Variables | Barras de progreso y contadores compartidos |
| Escenas (1.1) | Crear y lanzar pantallas de espera y de instrucciones con asistente y vista previa |
| Ajustes del servidor | Modo seguro, límites, `require_client` y valores por defecto |
| Historial y auditoría | Quién envió o cambió qué y cuándo |
| Ayuda | Guía rápida, glosario y ejemplos |

### 15.4 Cómo funciona por dentro (seguro)

- Las **acciones sencillas** (activar un disparador, cancelar, enviar una prueba) reutilizan **los mismos comandos** y sus comprobaciones.
- Las **ediciones estructuradas** (crear o editar una plantilla o un disparador) usan paquetes `AdminEdit` con códec propio, **validados en el servidor**: permiso en cada paquete, existencia de lo referenciado, límite de tamaño, **límite de frecuencia** por admin, rechazo con registro si algo es inválido y entrada en el **registro de auditoría**.
- El servidor manda un **`AdminSnapshot`** (plantillas, disparadores, estado, variables y grupos) **solo a quien tenga permiso**, y después solo los cambios (`AdminDelta`) mientras el menú está abierto. Nunca incluye secretos (por ejemplo la URL de un webhook).
- Los cambios se aplican por **el mismo camino que `/notify trigger set`**, así que no hay dos lógicas distintas. Se guardan en `config/notifymod/managed/`.
- **Lista de grupos:** si LuckPerms está instalado, una integración **opcional y aislada** con su API ofrece un desplegable con los grupos reales. Si no, es un campo de texto con sugerencias. ⚠ Comprobar.
- **Limitación:** las presentaciones visuales viven en resource packs y el menú lista las que **carga el cliente del admin**. Para que coincida con lo que ven los jugadores se recomienda distribuir el resource pack desde el servidor.
- El **editor visual de líneas de tiempo** queda fuera de la 1.0.

---

## 16. API de Java

### 16.1 Principios

- **Solo `com.takumistudios.notifymod.api` es estable** y sigue SemVer. Todo lo demás es interno.
- Entrypoint **`notifymod`** en `fabric.mod.json`.
- La API se publica en **Maven** (la CI ya lo soporta).

### 16.2 Registrar un elemento visual

```java
// Un ElementType con codec de parámetros, fábrica y renderizador (no una clase suelta)
NotifyAPI.registerElement(
    Identifier.fromNamespaceAndPath("mimod", "ruleta"),
    ElementType.of(RuletaParams.CODEC, RuletaWidget::new)
);
```

El codec valida los parámetros del JSON, y el esquema los incluye para el autocompletado y para `/notify validate`.

### 16.3 Enviar una notificación

```java
NotifyAPI.presentation("notifymod_example:cinematic_purga")
    .placeholder("jugador", player.getName())
    .priority(Priority.CRITICAL)
    .placement(Placement.CENTER)
    .audience(Audience.group("vip"))
    .send(server);
```

### 16.4 Disparadores y elementos interactivos

- `NotifyAPI.registerTrigger(...)` y `NotifyAPI.fireTrigger(...)`: otros mods pueden **crear y lanzar sus propios disparadores**, que el admin activa igual que los demás.
- **Resultados decididos por el servidor:** un elemento como la ruleta recibe del servidor el resultado y una semilla. Cada cliente solo anima hasta ese resultado, y todos ven lo mismo.
- **Sin interacción del jugador en la 1.0.**

### 16.5 Eventos

| Evento | Lado | ¿Cancelable? |
|--------|------|--------------|
| `SHOWCASE_DISPATCHED` | Servidor | No |
| `SHOWCASE_ENDED` | Servidor | No |
| `BEFORE_SHOW` | Servidor | Sí |
| `TRIGGER_FIRED` | Servidor | No |
| `SHOWCASE_STARTED` | Cliente | No |
| `SHOWCASE_INTERRUPTED` | Cliente | No |

> **Aclaración:** el servidor sabe **cuándo envió** una presentación y cuánto dura según su plantilla, pero **no cuándo la ve el jugador**. Un mod que quiera "pausar el daño mientras hay una cinemática" usa `SHOWCASE_DISPATCHED` con la duración de la plantilla.

---

## 17. Red

### 17.1 Paquetes

| Dirección | Paquete | Cuándo | Contenido |
|-----------|---------|--------|-----------|
| S2C | `Handshake` | Al conectarse | Versión del protocolo y capacidades |
| S2C | `ShowNotification` | Al enviar | id de plantilla, argumentos, prioridad, colocación y `key` |
| S2C | `HudNotification` | Al enviar | Tarjeta de esquina |
| S2C | `CancelNotification` | Al cancelar | id o `key` |
| S2C | `ClearQueue` | Con `/notify clear` | — |
| S2C | `VarUpdate` | Al cambiar una variable | Nombre y valor (delta) |
| S2C | `AdminSnapshot` · `AdminDelta` | Al abrir el menú | **Solo para admins con permiso** |
| C2S | `AdminEdit` · `AdminAction` | Desde el menú | **Solo para admins.** Validados (§23) |
| C2S | `ClientReport` | Presentación desconocida | Diagnóstico opcional, con límite de frecuencia |

- Los jugadores normales **nunca reciben la definición de los disparadores ni de las plantillas**.
- Los paquetes tienen **tamaño máximo**. El servidor valida los argumentos (cantidad y longitud) y nunca confía en el cliente.

### 17.2 Cliente obligatorio

- Notify Mod **no añade bloques, ítems ni efectos**, así que Fabric no impide por sí solo que entre un cliente sin el mod. Por eso el servidor lo comprueba en el **handshake** y **rechaza la conexión** si falta el mod o la versión del protocolo no coincide, con un mensaje claro y el enlace de descarga (`require_client`, activado por defecto).
- El mensaje es traducible y configurable por el admin.

### 17.3 Coste de red

Una notificación son unos pocos cientos de bytes. Las variables envían solo cambios. Los recursos (imágenes, sonidos, animaciones) **nunca viajan por la red del mod**: están en el resource pack.

---

## 18. Comandos

```
/notify send <mensaje>                         Envío rápido por el chat
/notify hud <mensaje>                          Envío directo a una tarjeta de esquina
/notify showcase <id> [args...]                Dispara una presentación
    Ejemplo: /notify showcase mimod:ruleta winner="Notch" placement=bottom_right audience=group:vip
/notify cancel [id|all]   · clear              Cancela o vacía la cola
/notify queue                                  Muestra la cola
/notify var set|get|list ...                   Variables compartidas
/notify schedule ...                           Programar avisos
/notify trigger list|info|enable|disable|set|test|fire|audience ...
/notify reload                                 Recarga la configuración sin reiniciar
/notify validate [id|all]                      Valida plantillas, disparadores y presentaciones
/notify debug [perf|report]                    Diagnóstico y rendimiento
/notify gui                                    Abre el menú de administración
/notify help [tema]                            Ayuda de comandos
```

Solo en el cliente: `/notify preview <id>` (ver una presentación sin servidor) y `/notify history`.

- Los argumentos aceptan la sintaxis `clave=valor` o un compuesto `{clave:"valor"}`.
- Opciones en cualquier envío: **`audience=`**, **`placement=`** y **`chat=true`** (copia al chat).
- Todos los comandos funcionan desde consola, bloques de comandos y funciones.

---

## 19. Cliente: opciones del jugador

| Opción | Por defecto |
|--------|-------------|
| **Ocultar avisos de esquina** (Mod Menu y tecla opcional) | Desactivado |
| Escala de las tarjetas de esquina | 100 % |
| Bandeja de historial (tecla opcional) | Disponible |

- El jugador **no tiene** ninguna opción para ocultar, saltar o reducir el Showcase del centro.
- Conserva los controles de volumen vanilla.
- El **menú de administración** solo aparece para quien tiene el permiso.
- La **bandeja de historial** guarda las últimas notificaciones, incluidas las ocultadas.

---

## 20. Accesibilidad y seguridad visual

Como el Showcase del centro no se puede ocultar, la seguridad visual la garantiza **el motor**, no el jugador:

- **Límite de destellos:** como máximo **3 destellos o cortes grandes por segundo**, y **ningún destello de rojo saturado** que ocupe una parte grande de la pantalla. Se aplica siempre al contenido del Showcase, sumando todas las pistas. No es una opción que el jugador pueda apagar.
- **`showcase_safe_mode`** (ajuste del servidor): lo activa el admin, por ejemplo en un directo con público amplio. Reduce la intensidad de todo el servidor.
- **`/notify validate`** avisa de presentaciones que superen los límites.
- Los parpadeos siguen el **tiempo real**, no los fotogramas.
- **Contraste suficiente**, textos que caben en idiomas largos y **escalas de GUI 1 a 4 probadas**.
- **Subtítulos** para los sonidos propios del mod.
- Pantallas ultrapanorámicas y FOV alto: las coordenadas son proporcionales a la pantalla.
- Quitar un canal a un grupo (por ejemplo, para alguien que lo necesite) es una decisión del admin con `notifymod.receive.showcase`.

---

## 21. Rendimiento

| Aspecto | Medida |
|---------|--------|
| Render | **Cero asignaciones por fotograma.** `Component`, textos, anchos y texturas cacheados |
| Carga | Decodificación de GIF, WebP y Lottie **fuera del hilo principal** |
| Memoria | **Presupuesto de memoria de vídeo**, límites de tamaño y fotogramas, LRU |
| Red | Solo ids, argumentos y deltas, y solo a quien los necesita |
| Servidor | Disparadores desactivados = **coste cero**. Los de sondeo corren cada N ticks y solo si están activos |
| Audiencia | Se resuelve una vez por envío |
| Disco | Escrituras atómicas fuera del hilo principal |

**Medición:** un gametest de cliente con **presupuesto de tiempo por fotograma** y los gametests de servidor registran la duración de las operaciones críticas y fallan si se superan. Perfilado con spark ante cualquier sospecha.

---

## 22. Compatibilidad

| Mod | Plan |
|-----|------|
| Xaero, JEI/REI/EMI, Jade, AppleSkin | `HudElementRegistry` con orden relativo para no pisar sus capas |
| Sodium, Iris | La GUI no se ve afectada. Probar con shaderpacks |
| Mod Menu | Pantalla de opciones del jugador |
| Mods de chat | El canal de chat usa mensajes de sistema vanilla |
| **LuckPerms** | Permisos y grupos. Va en el compat pack de la CI |
| Replay Mod, Flashback | Documentar qué se graba del HUD |
| Resource packs de servidor | Distribución recomendada de las presentaciones |
| **LiveEvents Mod** | Integración opcional: módulo `notify_presentation` para avisos y cuentas atrás, y disparadores a partir de sus eventos |

---

## 23. Seguridad

| Riesgo | Medida |
|--------|--------|
| Un cliente modificado intenta editar la configuración | Cada paquete `AdminEdit` o `AdminAction` valida **permiso, tamaño, existencia y frecuencia**. Lo inválido se descarta y se registra |
| Un cliente sin permiso intenta leer datos de administración | El `AdminSnapshot` **solo se envía a quien tiene permiso** |
| Un servidor malicioso intenta colgar el cliente | El cliente valida y limita todo lo que recibe: tamaño, número de elementos e intensidades |
| Un pack de la comunidad con JSON abusivo | Tamaño máximo por archivo, profundidad máxima de herencia y detección de ciclos |
| Placeholders con formato malicioso | Máximo de argumentos y de longitud. Solo texto, números y claves de traducción, sin JSON arbitrario |
| Revelar datos por accidente | Coordenadas solo con `allow_coordinates: true`. Los secretos (como la URL de un webhook) nunca van al cliente |
| Abuso de comandos | Límites de frecuencia por persona, `cooldown` por plantilla y registro de auditoría |
| Path traversal | Los nombres de archivo derivados de datos se sanean (sin `..`, `/`, `\`) |
| Datos personales | El registro de auditoría guarda nombres y UUID y se borra pasado un plazo configurable. **Sin telemetría** |

---

## 24. Pack de referencia, wiki y documentación

> **Regla transversal: documentar todo.** Una función no está terminada hasta que tiene su página de la wiki, un ejemplo en el pack de referencia, capturas y traducciones al español y al inglés.

### 24.1 Pack de referencia "Notify Example Pack"

Resource pack + datapack con **recursos propios o CC0**, pensado para que otros lo usen como **plantilla**.

- Carpeta `examples/notify-example-pack/{resourcepack,datapack,template}/` y un script que genera los `.zip`.
- **`template/`**: un pack mínimo ("copia esto y empieza").
- Cubre **todas** las funciones con al menos un ejemplo:
  - MOTD con copia al chat, tarjeta del HUD, Showcase cinemático (estilo "eliminación", "inicio de temporada", "aparece un jefe") y la misma presentación en versión compacta en una esquina.
  - Cada formato: textura, spritesheet, GIF, WebP, Lottie, y un ítem, un bloque y un mob en 3D.
  - Cuenta atrás de reinicio, barra de progreso global y una galería con todos los easings.
  - Plantillas, disparadores de ejemplo (**desactivados**) y audiencias con grupos de LuckPerms. En la 1.1, las escenas.
- Como el JSON no admite comentarios, el esquema permite un campo **`_comment`**, y cada carpeta lleva un `README` que explica cada archivo.
- Se publica también en Modrinth como resource pack y datapack, con licencia permisiva (CC0 recomendada) y un **`CREDITS.md`** con el origen y la licencia de cada recurso.

### 24.2 Wiki

Carpeta **`docs/`** versionada en el repositorio y publicada con GitHub Pages, en **español e inglés** (`docs/es` y `docs/en`).

| Sección | Contenido |
|---------|-----------|
| Inicio rápido | Instalar, primer aviso en 5 minutos e instalar el pack de ejemplo |
| Conceptos | Canales, colocación, prioridades, plantillas, presentaciones, disparadores y audiencias |
| Guía del admin | El menú de administración paso a paso, comandos y recetas de permisos con LuckPerms |
| **Cómo se hizo el pack de referencia** | Diario paso a paso: de la idea al JSON, de la animación al GIF o Lottie, del sonido a la línea de tiempo, con las decisiones tomadas y los errores comunes |
| **Cómo crear tu presentación** | Tutorial desde cero con capturas: estructura de carpetas, primera pista, animaciones y easings, probar con `/notify preview` y F3+T, validar |
| **Qué tomar en cuenta** | Rendimiento (peso, tamaños, fotogramas y presupuesto de memoria), accesibilidad (destellos, contraste, textos largos, escalas de GUI), idiomas, resoluciones y pantallas ultrapanorámicas, distribución del resource pack desde el servidor y licencias de los recursos |
| Recetas | "Eliminación de jugador", "Reinicio con cuenta atrás", "Inicio de temporada", "Bienvenida VIP", "Muerte permanente" y "Sorteo" |
| Desarrolladores | API, `registerElement`, `registerTrigger`, un addon de ejemplo y Javadoc |
| Referencia | Comandos, permisos, esquema JSON, placeholders, variables y easings (galería animada) |
| Solución de problemas | Errores frecuentes, `/notify debug report` y preguntas frecuentes |
| Contribuir | Cómo enviar presentaciones y traducciones, y el estilo |

### 24.3 Capturas y ejemplos de uso reproducibles

- Cada ejemplo del pack se captura **automáticamente** con los gametests de cliente (que el estándar ya exige) en momentos concretos y se exporta a `docs/assets/`, en PNG y en secuencias para GIF o vídeo corto.
- Un trabajo **`docs`** de la CI las regenera y avisa si cambian: **las capturas nunca se quedan desactualizadas.**
- Cada página termina con un bloque **"Ejemplo de uso"**: el JSON, el comando y la captura.

### 24.4 Qué más se documenta

- **Javadoc** de la API pública.
- **Esquema JSON** publicado, con la **referencia generada desde el propio esquema**.
- Ayuda de comandos (`/notify help`).
- Decisiones de arquitectura en `docs/adr/`.
- `docs/registro-de-errores.md` (norma TakumiStudios), `CHANGELOG.md` y este documento de diseño.
- La CI comprueba **enlaces rotos**, que las páginas **es/en coincidan**, que la referencia salga del esquema y que las capturas estén al día.
- La pestaña **Ayuda** del menú de administración usa textos cortos de los archivos de idioma enlazados a la wiki.

---

## 25. Estructura del proyecto

```
notifymod/
├─ build.gradle.kts · settings.gradle.kts · stonecutter.properties.toml
├─ src/main/java/com/takumistudios/notifymod/
│  ├─ NotifyMod.java                    # entrypoint común
│  ├─ api/                              # API pública y estable (§16)
│  ├─ core/                             # plantillas, notificaciones, prioridades, cola, variables
│  ├─ trigger/                          # TriggerManager, tipos (A/B/C), condiciones, avalanchas
│  ├─ audience/                         # AudienceResolver
│  ├─ permission/                       # PermissionGate, integración opcional con LuckPerms
│  ├─ admin/                            # AdminService, snapshots, ediciones, auditoría
│  ├─ network/                          # payloads y códecs
│  ├─ command/                          # /notify
│  └─ config/                           # server.json, triggers_state.json, managed/
├─ src/client/java/com/takumistudios/notifymod/client/
│  ├─ NotifyModClient.java
│  ├─ timeline/                         # compilador, runtime, pistas, easings
│  ├─ media/                            # tubería de fotogramas, atlas, GIF, WebP, Lottie, modelos
│  ├─ hud/                              # capas, tarjetas, Showcase, modo compacto
│  ├─ gui/                              # pantallas del menú de administración, asistentes, vista previa
│  ├─ state/                            # cola y estado del cliente
│  ├─ compat/                           # integraciones opcionales
│  └─ config/                           # client.json y Mod Menu
├─ src/main/resources/
│  ├─ fabric.mod.json
│  ├─ assets/notifymod/                 # lang (es_es, en_us), presentación integrada notifymod:motd
│  └─ data/notifymod/notify/{templates,triggers}/   # plantillas y disparadores por defecto (desactivados)
├─ src/test/java/...                    # pruebas unitarias
├─ src/gametest/java/...                # gametests de servidor y de cliente
├─ schema/                              # JSON Schema publicado
├─ examples/notify-example-pack/        # pack de referencia (§24)
├─ docs/{es,en,assets,adr}/ · docs/registro-de-errores.md
├─ .github/workflows/                   # build, standards, docs y publicación
└─ README.md · CHANGELOG.md · SECURITY.md · MIXINS.md · LICENSE · CREDITS.md
```

Se usan los **source sets separados** de cliente y servidor de Loom, para que el servidor nunca cargue clases del cliente.

---

## 26. Pruebas y calidad

| Tipo | Qué se prueba |
|------|---------------|
| **Unitarias** | Cola y prioridades, easings, compilación de líneas de tiempo, **resolución de audiencias**, condiciones de disparadores, control de avalanchas, validación de argumentos y migración de `format_version` |
| **Gametests de servidor** | Comandos, permisos (con un proveedor de permisos de prueba), disparadores activados y desactivados, validación de argumentos y red. **Un paquete `AdminEdit` de un jugador sin permiso se descarta** y ningún dato de administración llega a quien no debe |
| **Gametests de cliente** | **Capturas** de una presentación en el centro y en una esquina, y de **cada pestaña del menú de administración** en las escalas de GUI 1, 2 y 4. Las mismas capturas alimentan la wiki |
| **Rendimiento** | Presupuesto de tiempo por fotograma en el cliente |
| **Compat pack** (CI) | Lithium, FerriteCore y LuckPerms |

- Todo bug corregido añade un test que lo reproduce y una entrada en `docs/registro-de-errores.md`.
- La CI (`build.yml`) ejecuta las comprobaciones de estándar, el build, los gametests y el trabajo `docs`, en cada versión de Minecraft.

---

## 27. Hoja de ruta

| Fase | Contenido | Criterio de terminado |
|------|-----------|-----------------------|
| **0. Preparación y pruebas de viabilidad** | Generar el proyecto con `new-mod.sh`, confirmar nombres de API 26.x y **pruebas de concepto de GIF, WebP y Lottie**; comprobar `group.<nombre>` con LuckPerms y los mixins del grupo C | Una imagen de cada formato se dibuja en el HUD. Si Lottie no es viable, se acota o pasa a 1.1 (decisión explícita) |
| **1. Núcleo** | Registro, plantillas, protocolo con handshake, gestor de cola, canales, **colocación y modo compacto**, interruptor de esquina, comandos y **permisos con LuckPerms** | Notificaciones por los tres canales, en el centro y en esquinas, con las reglas de ocultación correctas |
| **2. Motor de líneas de tiempo** | Pistas, easings, texto, texturas, formas, sonido, fondos y reloj | El ejemplo `cinematic_purga` se reproduce según el JSON |
| **3. Tubería de medios** | Spritesheets, GIF, WebP, Lottie y modelos 3D, con presupuesto de memoria | Un pack con todos los formatos respeta los límites de memoria y FPS |
| **4. Disparadores** | Marco de disparadores, audiencias, condiciones, control de avalanchas, `triggers_state.json`, comandos `trigger`, catálogo A, luego B y C | "Un jugador muere" activado por el admin muestra la animación a un grupo de LuckPerms; desactivado, no hace nada y no cuesta nada |
| **5. Menú de administración** | Esqueleto del menú, permisos por pestaña, `AdminSnapshot` y `AdminEdit`, asistente **Enviar aviso**, pestaña **Disparadores**, selector visual de colocación y vista previa con el motor real; después el resto de pestañas, la ayuda y el tour | Un admin crea y activa un disparador y envía un aviso MOTD a un grupo **solo con el menú**, sin escribir un comando ni un JSON. Un jugador sin permiso no ve ni recibe nada del menú |
| **6. API y variables** | API pública, eventos, `registerTrigger`, widgets de ejemplo (ruleta, barra de progreso, cuenta atrás) y variables compartidas | Un addon de ejemplo registra un widget y un disparador y los usa en JSON |
| **7. Pulido** | Historial, programación, accesibilidad, matriz de compatibilidad e integración con LiveEvents | Todas las comprobaciones del estándar en verde |
| **8. Pack de referencia y wiki** | Completar el **Notify Example Pack** y la plantilla, la wiki en es/en, el tutorial "Cómo se hizo", la guía "Qué tomar en cuenta", las capturas automáticas y el trabajo `docs` de la CI | Una persona ajena al proyecto crea su propia presentación siguiendo solo la wiki, y la CI regenera las capturas sin diferencias |
| **9. Lanzamiento 1.0** | Publicación en Modrinth y CurseForge (mod, pack de referencia y datapack), API en Maven y wiki publicada | Versión 1.0 en las tres versiones de Minecraft |
| **10. Versión 1.1** | **Escenas** (§28) | Una escena "Esperando jugadores 0/10" con F1 y chat ocultos pasa a "¡Empieza!" sola y siempre se puede salir con Esc |

**Regla transversal:** durante todo el desarrollo, **ninguna fase se da por terminada sin su página de la wiki, un ejemplo en el pack de referencia, capturas y traducciones es/en**. La Fase 8 completa y pule lo que ya se fue documentando, no empieza desde cero.

---

## 28. Próximas mejoras (1.1): Escenas, pantallas de espera y de carga

### 28.1 Qué es

Una **pantalla completa personalizable** que el admin lanza **cuando quiera** a un grupo de jugadores:
- "Esperando jugadores 0/10".
- "Cargando el mapa".
- "Instrucciones del minijuego".
- "Cuenta atrás de inicio".

Reutiliza el motor de líneas de tiempo, las variables compartidas, la audiencia y los permisos.

**Ejemplos en el pack de referencia:** `espera_jugadores` (con barra de progreso y consejos que rotan), `instrucciones_minijuego` (varias páginas con título, reglas, controles y objetivo), `cuenta_atras_inicio` y `cargando_mapa`.

### 28.2 Cómo se muestra (punto técnico clave)

- **F1 (`hideGui`) oculta todo el HUD, incluidas las capas de Notify.** Por eso una escena **no puede ser una capa del HUD**: se dibuja como una **`Screen` propia**, que sigue viéndose con F1 activado.
- Mientras dura, el mod **activa `hideGui = true`** (equivale a pulsar F1: sin hotbar, sin vida, sin mira y **sin chat**) y **restaura el valor anterior** al terminar, al desconectar, al cambiar de mundo y si la escena falla. Los mensajes de chat que lleguen **no se pierden**: se guardan y se muestran al salir de la escena. ⚠ Comprobar en 26.x que `hideGui` no se persiste y cómo interactúa con las pantallas.
- **Fondo:** el mundo con desenfoque, un color, o una imagen o animación (los mismos formatos de la 1.0).
- **Entrada:** por defecto **bloquea el movimiento** (sala de espera). Una variante que deje moverse al jugador necesitaría una capa de dibujo especial sobre el HUD oculto. ⚠ La Fase 0 decide si es viable.
- **Siempre se puede salir:** `Esc` abre el menú de pausa (con la opción de desconectar) y al cerrarlo la escena vuelve. **Nunca se atrapa a un jugador.**

### 28.3 Contenido dinámico

- Variables de la 1.0 más **fuentes automáticas**, que el servidor calcula **solo mientras hay una escena que las use** y envía como deltas:
  `online_players`, `group_members:<grupo de LuckPerms>`, `team_members:<equipo>`, `tag_count:<etiqueta>` y `dimension_players`.
- Textos con formato `{online}/{target}` ("3/10"), claves de traducción y **las teclas del propio jugador**: `{key:key.attack}` muestra la tecla real de atacar que tiene configurada.
- **Páginas** para instrucciones: avanzan solas con temporizador o las controla el admin (`/notify scene page <n>`).
- **`end_when`:** cuando se cumple una condición (por ejemplo, `online >= target`) la escena lanza un Showcase "¡Empieza!" y termina sola.

### 28.4 Ciclo de vida

```
/notify scene start <id> [audiencia] [args]
/notify scene end <id|all>
/notify scene set <variable> <valor>
/notify scene page <n>
```

- El servidor **recuerda las escenas activas** y las envía a quien entra, reaparece o cambia de dimensión.
- `max_duration` (30 min por defecto) evita escenas olvidadas.
- Un disparador puede iniciar o terminar una escena.

### 28.5 Convivencia con lo demás

- Con una escena activa, LOW, NORMAL y HIGH **esperan en cola** (y caducan). **CRITICAL se dibuja encima.**
- Las escenas son de pantalla completa: **no se pueden ocultar**, igual que el centro, y se les aplica el mismo límite de destellos.
- LuckPerms: `notifymod.scene.start`, `.end`, `.receive` y `notifymod.bypass.scene` (el personal de moderación sigue jugando mientras los demás esperan).
- En el menú, la pestaña **Escenas** con el asistente "Crear pantalla de espera".

### 28.6 Qué se deja preparado en la 1.0

Variables compartidas y fuentes de datos, estado de las escenas activas al entrar y una capa de dibujo que pueda mostrarse sobre pantallas (`render_over_screens`).

**Idea posterior, sin fecha:** personalizar también las pantallas vanilla de conexión y de "Cargando terreno" desde el resource pack. Requiere mixins y se evaluará.

---

## 29. Riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| "Todo desde el principio" (sobre todo **Lottie y WebP**) retrasa la 1.0 | Alta | Alto | Fase 0 con pruebas de concepto y **puerta de decisión**; tubería única de fotogramas |
| El Showcase central no se puede ocultar: riesgo de fotosensibilidad | Baja | Muy alto | Límite de destellos fijo en el motor, `showcase_safe_mode`, validación de presentaciones y `notifymod.receive.showcase` para excluir a quien lo necesite |
| Muchos disparadores a la vez saturan la cola | Media | Medio | `cooldown`, `max_per_minute`, `coalesce` y tope global; los desactivados no cuestan nada |
| Los disparadores del grupo C necesitan mixins que cambian entre versiones | Alta | Bajo | Grupo C aislado, `require = 0` y estado "no disponible" explicado |
| Un grupo de LuckPerms no se detecta | Media | Medio | Prueba en el compat pack y alternativa por `permission`, `team` o `selector` |
| Revelar datos (coordenadas, causas de muerte) por accidente | Baja | Medio | `allow_coordinates: true` explícito y límite de longitud |
| El menú abre una superficie de ataque (paquetes con poder) | Media | Alto | Validación estricta, descarte con registro, auditoría, comandos reutilizados y pruebas de acceso |
| El menú lista presentaciones que no coinciden con las de los jugadores | Media | Bajo | Distribuir el resource pack desde el servidor y avisar de las presentaciones desconocidas |
| El alcance crece: el menú es casi otro producto | Alta | Medio | Fase propia, primero "Enviar aviso" y "Disparadores"; editor visual fuera de la 1.0 |
| La wiki y las capturas se quedan desactualizadas | Media | Medio | Capturas generadas por los gametests de cliente y regeneradas por la CI; referencia generada desde el esquema |
| El pack de referencia usa recursos con derechos de terceros | Baja | Alto | Solo recursos propios o CC0, anotados en `CREDITS.md` |
| Memoria de vídeo con GIF y Lottie grandes | Media | Medio | Presupuesto, límites de tamaño y de fotogramas, LRU y calidad por niveles |
| Cambios de HUD y de renderizado entre versiones 26.x | Alta | Medio | Capa de aislamiento del renderizado y pruebas en las tres versiones |
| El cliente obligatorio excluye a algunos jugadores | Media | Bajo | Mensaje claro con enlace, `require_client` configurable y documentación para modpacks |
| **Escenas (1.1):** HUD oculto que no se restaura o jugador atrapado | Baja | Alto | `hideGui` se restaura al terminar, desconectar, cambiar de mundo o fallar; `max_duration`; `Esc` siempre abre el menú de pausa; `/notify scene end all` |
| **Escenas (1.1):** F1 oculta también las capas de Notify | Alta | Medio | Las escenas son una `Screen` propia, no una capa del HUD (la Fase 0 lo comprueba) |

---

## 30. Decisiones abiertas

| # | Pregunta | Recomendación |
|---|----------|---------------|
| D1 | ¿Licencia del mod? | La plantilla de TakumiStudios trae **MIT**. LiveEvents Mod tiene la misma decisión pendiente, conviene unificarla |
| D2 | ¿Licencia del pack de referencia? | **CC0**, para que cualquiera lo use sin atribución |
| D3 | ¿Dónde se aloja la wiki? | **GitHub Pages** con un generador de documentación, versionada con el código (la wiki nativa de GitHub no se revisa con pull requests) |
| D4 | ¿Pueden las escenas dejar moverse al jugador? | Lo decide la prueba de la Fase 0. Si no es viable, solo con el cursor liberado |
| D5 | ¿Se personalizan también las pantallas vanilla de conexión y de carga? | Fuera de la 1.1; se evalúa después |
| D6 | ¿Qué librerías de WebP y Lottie? | ✅ **Resuelta en la Fase 0:** WebP con TwelveMonkeys (BSD-3, Java puro); Lottie con un rasterizador propio para un subconjunto documentado. Ver `docs/adr/0001-formatos-animados-fase-0.md` |
| D7 | ¿Caducidad de la cola de entrada (avisos para jugadores no conectados)? | 24 h por defecto |
| D8 | ¿Se extrae una librería común con LiveEvents (regla de destellos, validador de expresiones, resolución de audiencias)? | Primero se implementa en cada mod; se extrae cuando haya duplicación real |
| D9 | ¿Qué se graba del HUD en Replay Mod y Flashback? | Se documenta tras probarlo en la Fase 7 |
| D10 | ¿`CRITICAL` puede ir a una esquina si el admin lo pide? | **No.** Siempre va al centro para que ningún jugador lo oculte. Si se quisiera cambiar, sería una excepción explícita por plantilla |
| D11 | ¿Espacio de nombres? | ✅ **`notifymod:`** para todo el contenido propio (coincide con el modid). El comando sigue siendo `/notify` |

---

## 31. Casos de uso

| Caso | Cómo se resuelve |
|------|------------------|
| **Modpacks de aventura:** avisar que ha aparecido un jefe o que se descubrió una estructura | Disparadores "aparece un jefe" y "estructura descubierta" (grupos B y C), con una presentación cinemática |
| **Servidores SMP:** reinicios, bienvenida a nuevos VIP y cambios de temporada | Cuenta atrás de reinicio con MOTD y copia al chat; disparador "primera vez que entra" con audiencia por grupo; Showcase de inicio de temporada |
| **Eventos y series:** eliminaciones, ruletas de castigo, textos gigantes y barras de progreso globales | Disparador "un jugador muere" con la presentación de eliminación; ruleta con resultado decidido por el servidor; barras enlazadas a variables compartidas |
| **Muerte permanente:** avisar a todos | Plantilla con prioridad CRITICAL (siempre al centro) |
| **Directo con público amplio** | `showcase_safe_mode`, y un grupo de LuckPerms para quienes graban |
| **Minijuegos (1.1):** sala de espera "0/10" e instrucciones | Escenas con fuentes automáticas y páginas |
| **Creadores de packs** | Pack de referencia como plantilla y wiki con el paso a paso |
| **Desarrolladores** | API de elementos y disparadores, y eventos de cliente y de servidor |

---

*Fin del documento. Siguiente paso propuesto: cerrar las decisiones abiertas (D1–D9) y empezar la Fase 0 con las pruebas de viabilidad de GIF, WebP y Lottie.*

*Creado por TakumiStudios.*
