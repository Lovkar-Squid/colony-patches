# Changelog

## [1.2.0]

### Added
- **Several colonies per player**, off by default. With `severalColoniesPerPlayer = true` in
  `config/colony_patches-server.toml`, placing a new Town Hall founds another colony instead of
  offering to delete or abandon the one the player has. MineColonies' own `allowinfinitecolonies`
  does not do this. Where MineColonies asks for *the* colony of a player (`/minecolonies home`,
  commands without a colony id, a flag outside any colony) it takes one of them.

### Notes
- The Compatibility addon for MineColonies 3.58 no longer has its own Cultural Delights corn
  handler, so the corn fix has nothing to do there; Mixin logs that its target class is missing,
  which is harmless.

## [1.1.0]

### Fixed
- **Building modules numbered the same on the server and its players.** MineColonies numbers its
  building modules in the order the mods load, and a dedicated server (no client-only mods) could
  load them in another order than its players. Then the windows of some addon buildings did not
  sync, settings could reach the wrong module, and the town hall's statistics page crashed. All
  building modules are now numbered by name once loading is complete, the same on both sides.

### Changed
- Colony Patches is now needed on the server **and** on every client. It registers a required
  network payload, so a half-installed setup is refused at login with the missing mod named,
  instead of breaking colony windows.

## [1.0.0]

### Fixed
- **Farmers and Cultural Delights corn**: with MineColonies Compatibility 3.57 and Cultural Delights
  0.18, a farmer working a field of corn crashed the server (`NoClassDefFoundError` for the removed
  `CornUpperBlock`). Farmers now harvest the one-block corn as an ordinary crop and replant it. The
  fix only acts while the addon's corn handler still needs the missing class.
