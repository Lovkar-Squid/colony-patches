# Building Colony Patches

Colony Patches is compiled with plain `javac` and packed with `jar`. There is no Gradle and no
mappings step, because NeoForge 1.21.1 mods run on the official Mojang names.

## What you need

- **JDK 21.** By default the scripts use `../../tools/jdk21`, relative to the repository. Set
  `COLONYPATCHES_JDK` to use another JDK.
- **A `libs/` folder** (default `../../libs`, or `COLONYPATCHES_LIBS`). It is not part of the
  repository, because the jars are not ours to redistribute. It holds Minecraft 1.21.1 as NeoForge
  installs it (`neoforge-21.1.x-client.jar`, the vanilla client jar, `neoforge-21.1.x-universal.jar`),
  the Mixin jar, MineColonies with Structurize, BlockUI and Domum Ornamentum, and the libraries
  Minecraft links against (DataFixerUpper, fastutil, Guava, Gson, SLF4J and so on).
- **A `compat-libs/` folder** (default `../../compat-libs`, or `COLONYPATCHES_COMPAT`) with the jars
  of the mods the fixes are written against: the Compatibility addon for MineColonies.
- **`stubsrc/`.** Two tiny stand-ins for `net.neoforged.api.distmarker.Dist` and `OnlyIn`, which none
  of those jars carry. They go on the compile classpath only, never into the mod jar.

The NeoForge-patched client jar must come before the vanilla client jar on the classpath; the
scripts do this.

## Compile and package

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build.ps1           # out/colony_patches-<version>.jar
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-harness.ps1   # out/corntest-<version>.jar
```

The version lives only in `resources/META-INF/neoforge.mods.toml`.

## The test harness

`tools/harness` is a headless test mod for a dedicated server that has MineColonies, the
Compatibility addon and Cultural Delights. Put its jar into the server's `mods/` (with or without
Colony Patches), write `crop=corn` or `crop=mixed` into `corntest.txt` in the server folder, use a
flat world and start the server. The harness builds a colony with a farmer and a field of 24 ripe
plants, asks the Compatibility addon about each crop the way its farmer mixin does, lets the farmer
harvest (and, for corn, replant), logs `[corntest] CHECK` and `RESULT` lines and stops the server.
Without the fix, a corn run ends in the crash instead.
