# Wireless Belt

Mindustry v8 Build 160.x Java mod. This repository is a complete Gradle desktop-build project.

## Requirements

- JDK 17 or newer. JDK 21 is supported; Gradle compiles the mod with Java `--release 17`.
- Internet access on the first build so Gradle can download Gradle itself and Mindustry `v160.6`'s `dependencies.jar`.

## Build on Windows

Open a terminal in this folder and run:

```bat
gradlew.bat clean jar
```

The first run downloads Gradle 8.10.2 into `.gradle-dist/`. Gradle then downloads the official Mindustry v160.6 compile-time API. The resulting desktop-testing mod is:

```text
build\libs\wirelessbelt-1.0.jar
```

You can also run:

```bat
gradlew.bat buildMod
```

## Build on Linux/macOS

```bash
./gradlew clean jar
```

## Important

This is a **desktop-testing JAR**. Mindustry's official Java mod template uses `compileOnly` for the Mindustry API, so the game classes are not bundled into the mod JAR. The mod itself contains `mod.json` and the `assets/` directory.

## Mindustry version

The build is pinned to **v160.6**. Change `mindustryVersion` in `build.gradle` only if you intentionally target another Mindustry release.

## Mod usage

- Place one sender (orange) and one receiver (cyan), within 20 tiles.
- Both blocks require power.
- Configure the sender, then select the receiver to link them.
- Select an item on the sender to filter what is transmitted; clear the selection to transmit all items.
- Items that do not match the filter are passed to other adjacent item-accepting blocks.
