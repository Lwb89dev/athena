# Project Athena

**v0.1.1** — A free online library, built entirely on nostr. The name is
Athena, the goddess of knowledge.

Anyone can read without an account. Whoever logs in with their own key carries
along, from one device to another, where they left off, their highlights, and
their favorites — public or private, decided passage by passage.

**There is no Athena server.** Books are nostr events on public relays, your
annotations are events signed by you. The app is a reader.

## Status

Functional MVP, no stubs: NIP-44 and NIP-46 are implemented, not sketched
out. 32 tests pass, including the official NIP-44 vectors compared byte for
byte.

Compiles and has been built on both targets as a Kotlin Multiplatform
project:

- **Android** (`androidApp`) — login via **Amber** (NIP-55), release APK
  split per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) plus a universal one
- **Desktop** (`desktopApp`) — `.deb` / `.exe` via jpackage, login via **bunker** (NIP-46)

About 95% of the code — nostr, cryptography, database, repositories,
ViewModels, and the whole Compose Material 3 UI — lives in
`shared/commonMain` and is written once for both.

What's already standing and what isn't: see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Building

Coming from Flutter? Start with **[docs/BUILD.md](docs/BUILD.md)**: it's a
command-by-command translation.

```bash
# Android: install the debug APK on the connected device
./gradlew :androidApp:installDebug

# Android: release APK split per ABI (+ a universal one), in androidApp/build/outputs/apk/release/
./gradlew :androidApp:assembleRelease

# Desktop: run in development
./gradlew :desktopApp:run

# Desktop: produce the native package (.deb on Linux, .exe on Windows)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Tests (cryptography, NIP-19, bunker parsing)
./gradlew :shared:desktopTest
```

The wrapper is in the repo and downloads Gradle 8.11.1 on its own: you
**don't need** Gradle installed, and the system one is ignored. Always use
`./gradlew`, never `gradle`.

## Icons

All icons — Android adaptive, legacy bitmaps, Linux `.png`, Windows `.ico`,
512x512 for the store — are generated from a single source PNG:

```bash
python3 tools/make-icons.py artwork/icon-source.png
```

The script shrinks the artwork into the adaptive icon's *safe zone* (the
launcher can mask everything outside the central 66/108) and paints the
background layer the same navy sampled from the source, so the seam doesn't
show.

## What it's built on

Everything needed already existed as a spec. No invented formats:

| | |
|---|---|
| Books | NKBIP-01 (kind `30040` + `30041`), NIP-23 as fallback |
| Highlights | **NIP-84**, kind `9802` |
| Favorites | NIP-51 sets (`30003`), private ones NIP-44 encrypted |
| Reading sync | NIP-78 (`30078`), self-encrypted |
| Login | NIP-55 (Amber) and NIP-46 (bunker), both implemented |
| Encryption | NIP-44 v2, verified against the official vectors |
| Deletion | NIP-09 |
| Feed | NIP-02 (contacts) and NIP-51 (lists) to filter out spam |
| Relays | NIP-65 |

The only two non-standard things are the highlight color and the passage
offset, prefixed `project_athena_` so other clients ignore them. A highlight
made here remains a valid NIP-84 event everywhere.

## Notes

[Alexandria by GitCitadel](https://next-alexandria.gitcitadel.eu) already
exists on nostr — the reader that defined NKBIP-01. This app is called
Athena precisely so it isn't confused with that one. The kinds stay theirs:
that way it opens the books already published on relays right away, instead
of starting from an empty library.

## License

To be decided. For a public library, MIT or AGPL are the two sensible
directions, depending on how open you want forks to stay.
