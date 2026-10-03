# Desarrollo, CI y publicación de la wiki

## Verificar el mod

Usa Java 25 y el wrapper de Gradle. En PowerShell, sustituye `./gradlew` por `.\gradlew.bat`.

```sh
./gradlew :26.1.2:build :26.2:build :26.3:build
./gradlew :26.3:runGameTest -PcompatPack
./gradlew :26.3:runClientGameTest
```

`build` incluye tests unitarios y gametests de servidor. La CI repite el compat pack y las pruebas de cliente en
26.1.2, 26.2 y 26.3. El compat pack actual contiene Lithium y FerriteCore, **no LuckPerms**.
Los gametests escriben en `versions/<v>/build/run/`; evita ejecutar simultáneamente servidor y cliente de la misma
versión porque comparten tareas que eliminan ese directorio. Conserva capturas antes de volver a ejecutar `build`.

El test de cliente comprueba render de todos los formatos, HUD, líneas de tiempo, modelos, disparadores activados y
desactivados y una muerte/reaparición real. Las capturas no equivalen a una matriz manual de accesibilidad o a una
prueba de LuckPerms. Los errores corregidos se describen en el [registro](../registro-de-errores.md).

## Editar esta wiki

La wiki es este directorio `docs/`, revisado mediante commits; no es una copia en la wiki nativa de GitHub.
MkDocs genera un sitio estático que se puede alojar en GitHub Pages. Cada nueva función necesita documentación es/en,
ejemplo y pruebas acordes con lo que se afirma. Conserva los nombres técnicos en los dos idiomas.

En Windows:

```powershell
py -m venv .venv-docs
.\.venv-docs\Scripts\python.exe -m pip install -r requirements-docs.txt
.\.venv-docs\Scripts\python.exe -m mkdocs build --strict
.\.venv-docs\Scripts\python.exe -m mkdocs serve
```

En Linux usa `python3 -m venv .venv-docs` y `.venv-docs/bin/python`.
La vista local se abre en `http://127.0.0.1:8000/`. El HTML exportable queda en `site/`.
`--strict` rechaza enlaces internos, imágenes y navegación incorrectos; no comprueba disponibilidad de enlaces externos.

Después de ejecutar el test de cliente, actualiza las capturas:

```powershell
.\.venv-docs\Scripts\python.exe scripts/docs_assets.py --screenshots versions/26.3/build/run/clientGameTest/screenshots
```

La CI toma las capturas de cliente de 26.3 y las incorpora al sitio generado. Falla si falta una captura obligatoria.
No modifica ni hace commits automáticos en el repositorio, y no compara píxeles entre máquinas.

## GitHub Actions

El workflow `Build and test` comprueba autoría, higiene, traducciones y wrapper; después prueba cada versión con y sin
compat pack, ejecuta el cliente y genera el sitio. El job `docs` entrega el artifact `github-pages`.
La build `dev` solo se publica desde `main` cuando normas, servidor, cliente y documentación pasan.

Para habilitar **la publicación de la wiki**, un administrador debe elegir Settings → Pages → Source → GitHub Actions
y definir la variable de repositorio `DOCS_PUBLISH=true`. Entonces el job `deploy-docs` publica solamente los pushes
a `main` o una ejecución manual del workflow en `main`. Las PR construyen documentación sin publicarla.
La URL prevista es `https://alonex15.github.io/Notify-Mod/`; configurar los archivos locales no crea por sí solo un sitio público.

## Releases del mod

Un tag `vX.Y.Z` debe coincidir con `mod.version` en `stonecutter.properties.toml` y tener una sección de changelog.
`release.yml` compila por versión y crea la release. Modrinth, CurseForge y Maven solo se activan con sus variables
y secretos, y requieren la implementación correspondiente: el proyecto todavía no declara una publicación Maven
de API. No configures `MAVEN_URL` hasta implementarla.

Para una release, verifica primero el commit de `main` en verde; después crea y sube el tag acordado.
Los cambios actuales son desarrollo de la fase 4, no un lanzamiento 1.0 ni la finalización del pack de referencia.
