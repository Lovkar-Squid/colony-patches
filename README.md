# Colony Patches

*Small compatibility fixes for MineColonies modpacks.*

Big MineColonies packs put many addons next to each other, and now and then an update on one side
breaks something on the other. Colony Patches carries small, targeted fixes for those cases until
the mods involved fix them themselves. Each fix only acts when the broken combination is actually
loaded; with anything else the mod does nothing.

Made by **Lovkar & Claude** for NeoForge 1.21.1. It needs no other mod and adds no blocks or items,
so it is safe to add to a world and to remove again. Its one setting, several colonies per player, is
off until a server owner turns it on.

## The fixes

### Farmers and Cultural Delights corn

**Mods:** [Compatibility addon for MineColonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies-compatibility)
3.57 with [Cultural Delights](https://www.curseforge.com/minecraft/mc-mods/cultural-delights) 0.18 or later.

**What went wrong:** a farmer working a field with Cultural Delights corn crashed the server
("Ticking entity", `NoClassDefFoundError: com/baisylia/culturaldelights/block/custom/CornUpperBlock`).
The Compatibility addon harvests corn as the two-block plant it was up to Cultural Delights 0.17: a
`CornBlock` with a `CornUpperBlock` on top. Cultural Delights 0.18 rebuilt corn as one `CornBlock`
with a `height` property, up to three blocks tall, and removed `CornUpperBlock`. MineColonies' worker
AI catches exceptions but not errors, so the missing class takes the whole server down.

**The fix:** the new corn is an ordinary crop for MineColonies: the bottom block at full age is the
harvest and drops the corn cobs. Colony Patches stops the addon's corn handler from asking for a
special harvest position when that handler still needs `CornUpperBlock` and the class is gone, and
MineColonies' own crop handling takes over. Farmers harvest ripe corn, the upper blocks go with the
bottom one, and they plant corn kernels again as usual. Eggplants, cucumbers and beans never went
through the broken code.

The fix checks the actual classes rather than version numbers: with the old two-block corn, or with
a future Compatibility addon whose corn handler no longer needs `CornUpperBlock`, it stays out of the
way. The log says which case applies the first time a farmer looks at corn:

```
[Colony Patches]: MineColonies Compatibility's corn handler needs CornUpperBlock, which this Cultural
Delights no longer has: farmers harvest its one-block corn as an ordinary crop
```

Reported upstream:
[MineColonies_Compatibility#203](https://github.com/gisellevonbingen-Minecraft/MineColonies_Compatibility/issues/203).

### The same building-module numbers on the server and its players

**Mods:** MineColonies with addons that bring their own buildings, played on a dedicated server.

**What went wrong:** MineColonies numbers its building modules with a counter, in the order the
mods create them, and the server and its players talk about modules by those numbers: the building
windows are synced with them, and settings, hiring and item lists are sent back with them. That
order follows the order NeoForge loads the mods in, and NeoForge fixes it only where mods depend on
each other; for the rest it follows a hash map whose order changes with the set of installed mods.
A dedicated server has no client-only mods (no Sodium, no Iris...), so it can number the modules
differently from its players. In *Lovkar's MineColonies Ultimate* 1.6.16 the server loaded Runesmith
before Voyager and Colonies at War, the players after them: 22 modules had other numbers, the windows
of those buildings never synced, settings could reach the wrong module, and opening the town hall's
statistics page crashed the game (`NullPointerException` on `WorkerBuildingModuleView.getJobDisplayName`,
with `Problem during sync: Client side does not have matching module views` in the log before it).

**The fix:** once every mod has loaded, Colony Patches numbers all building modules again by their
names, which are the same everywhere. The numbers are never saved (a building stores its modules by
name), so worlds are not touched. Both sides have to do it: Colony Patches registers a required
network payload, so a player without it cannot join a server with it (and the other way round), with
NeoForge naming the missing mod instead of colony windows breaking silently. The log shows the
module set's fingerprint, which must be the same on the server and its players:

```
[Colony Patches]: Numbered 244 MineColonies building modules by name (244 changed their number);
module set b5973defa99b - the same on the server and its players
```

## Option: several colonies per player

MineColonies lets a player own one colony. Placing a second Town Hall only offers to delete or
abandon the first, and its own `allowinfinitecolonies` setting does not change that (it only adds the
abandon button). With Colony Patches, a server owner can allow it in
`config/colony_patches-server.toml`:

```toml
[colonies]
	severalColoniesPerPlayer = true
```

Then a new Town Hall founds another colony, owned by the same player, next to the ones they have.
Every colony keeps its own citizens, buildings and permissions. Where MineColonies asks for *the*
colony of a player - `/minecolonies home`, colony commands without a colony id, a flag placed
outside any colony - it takes one of them. Off by default.

Tested headless: one player confirms the founding of three Town Halls 400 blocks apart. With the
setting off (or with Colony Patches 1.1.0) only the first becomes a colony; with it on, all three do,
each owned by that player.

## How it was tested

On a dedicated server with the server-side mods of *Lovkar's MineColonies Ultimate*: a colony with
a farmer and a field of 24 ripe plants (`tools/harness`).

| Field | Without Colony Patches | With Colony Patches |
| --- | --- | --- |
| Ripe corn | the server crashes as soon as the farmer looks at the field | all 24 harvested (44-51 cobs), replanted, no corn blocks left hanging |
| Ripe eggplants, cucumbers, beans | all harvested | all harvested |

## Install

Drop the jar into `mods/` **on the server and on every client** (single player runs a server too).
The corn fix does nothing in a game without the mods it patches; the module numbering does nothing
without MineColonies.

## Building

See [BUILDING.md](BUILDING.md).

## License

GPL-3.0-or-later; see [LICENSE](LICENSE).
