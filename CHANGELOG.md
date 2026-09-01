# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.1] — 2026-09-01

### Fixed

- Android: the "load a file" tap on the import screen did nothing. No
  picker was ever installed on Android — `FilePickers.install(...)` was
  only wired up on desktop — so `FilePickers.pick()` returned `null`
  unconditionally and the tap silently no-opped. Added `AndroidFilePicker`,
  a suspend bridge over `ActivityResultContracts.GetContent()` (SAF),
  registered from `MainActivity`.

## [0.1.0] — 2026-09-01

Initial public release. See the
[v0.1.0 release notes](https://github.com/Lwb89dev/athena/releases/tag/v0.1.0)
and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for what the app does and
what it's built on.

### Added

- Android release APKs split per ABI (`arm64-v8a`, `armeabi-v7a`,
  `x86_64`) plus a universal one, signed with a dedicated release keystore
  kept out of the repo (`keystore.properties`) — a release build fails
  outright if it's missing instead of falling back to the debug key.

### Fixed

- `com.tom-roush:pdfbox-android` was pinned to a version (`10.0.0.2`) that
  doesn't exist on Maven Central, which broke Android builds entirely.
  Pinned to the real latest, `2.0.27.0`.
- R8 failed on Android release builds over a missing optional class
  (`com.gemalto.jp2.JP2Decoder`, pdfbox-android's unbundled JPEG2000
  decoder). Added the corresponding `-dontwarn` rule.
- macOS packaging (`packageDmg`) rejected `packageVersion = "0.1.0"` —
  Apple's pkg/dmg versioning requires MAJOR > 0. Linux and Windows keep the
  real app version; macOS gets a separate `dmgPackageVersion`.
