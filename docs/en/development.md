# Development, CI and wiki publishing

## Verify the mod

Use Java 25 and the Gradle wrapper. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
./gradlew :26.1.2:build :26.2:build :26.3:build
./gradlew :26.3:runGameTest -PcompatPack
./gradlew :26.3:runClientGameTest
```

`build` includes unit tests and server gametests. CI checks the compat pack and client per supported version.
The compat pack contains Lithium and FerriteCore, **not LuckPerms**. Server/client tests of the same version share
cleanup tasks under `versions/<v>/build/run/`; do not run them concurrently. Save screenshots before another build.

Client tests check media formats, HUD, timelines, models, enabled/disabled triggers and an actual death/respawn cycle.
Screenshots do not establish a manual accessibility matrix or LuckPerms compatibility. Resolved bugs are recorded
in the [error log](../registro-de-errores.md).

## Build and edit the wiki

The wiki is the version-controlled `docs/` directory, rather than GitHub's native wiki. MkDocs builds static HTML
for GitHub Pages. New features need matching Spanish/English documentation, examples and appropriate evidence.

On Linux:

```sh
python3 -m venv .venv-docs
.venv-docs/bin/python -m pip install -r requirements-docs.txt
.venv-docs/bin/python -m mkdocs build --strict
.venv-docs/bin/python -m mkdocs serve
```

On Windows use `py -m venv .venv-docs` and `.\.venv-docs\Scripts\python.exe`.
Preview at `http://127.0.0.1:8000/`; generated HTML is in `site/`. Strict mode checks internal links, assets and
navigation; it does not check availability of external websites.

After client tests, refresh documentation images:

```sh
.venv-docs/bin/python scripts/docs_assets.py --screenshots versions/26.3/build/run/clientGameTest/screenshots
```

CI uses screenshots from the 26.3 client job in the generated site and fails if required screenshots are missing.
It neither commits generated changes nor compares pixels between machines.

## GitHub Actions and publishing

`Build and test` checks metadata, code hygiene, translation keys and the wrapper; it then tests every version with
and without the compat pack, runs client tests, and builds the wiki. The `docs` job uploads the `github-pages` artifact.
The development mod release requires successful standards, server, client and documentation jobs on `main`.

To publish the wiki, a repository administrator must select Settings → Pages → Source → GitHub Actions and define
the repository variable `DOCS_PUBLISH=true`. The `deploy-docs` job then publishes on pushes to `main` or manual workflow
runs on `main`. Pull requests build documentation without publishing. The expected URL is
`https://alonex15.github.io/Notify-Mod/`; local configuration does not itself create a public website.

## Mod releases

A `vX.Y.Z` tag must match `mod.version` in `stonecutter.properties.toml` and have a changelog section.
`release.yml` builds each version and creates the GitHub release. Modrinth, CurseForge and Maven require configured
variables/secrets and matching implementation. The project does not yet declare an API Maven publication; do not
set `MAVEN_URL` until that exists.

Verify the `main` commit's CI before creating and pushing an agreed release tag. Current changes are phase 4
development, not the 1.0 release or a completed reference pack.
