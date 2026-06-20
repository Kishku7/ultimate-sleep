# Ultimate Sleep

An advanced, configurable Minecraft sleep mod.

- **Loader:** Fabric only (until 1.0; other loaders + backports considered afterward).
- **Minecraft:** built against 26.1.2, declared compatible with `26.1.x` and `26.2.x`
  (`fabric.mod.json` minecraft range `>=26.1 <26.3`). A dedicated NeoForge 26.2 build is deferred.
- **Author:** Kishku7  ·  **License:** ARR  ·  Repo (planned): `github.com/Kishku7/ultimate-sleep` (private)

## Layout

```
ultimate-sleep/
  README.md            this file
  FUNCTIONAL_SPEC.md   authoritative design/spec
  research/            Modrinth sleep-mod feature survey (input for the feature set)
  fabric/26.1.2/       the Fabric loom project (build here)
```

## Build

```
cd fabric/26.1.2
./gradlew build      # Java 25, Fabric Loom 1.16, Gradle 9.4.1
```

Output jar: `fabric/26.1.2/build/libs/ultimate-sleep-<version>.jar`.

## Status

Scaffold. Commands, AFK tracking, and conditional `/afk` registration are stubbed and wired;
the in-game admin panel GUI and concrete sleep features are not implemented yet.
See `FUNCTIONAL_SPEC.md`.
