# Changelog

## [1.0.0]

### Fixed
- **Farmers and Cultural Delights corn**: with MineColonies Compatibility 3.57 and Cultural Delights
  0.18, a farmer working a field of corn crashed the server (`NoClassDefFoundError` for the removed
  `CornUpperBlock`). Farmers now harvest the one-block corn as an ordinary crop and replant it. The
  fix only acts while the addon's corn handler still needs the missing class.
