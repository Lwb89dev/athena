# Compilare e provare — guida per chi viene da Flutter

Kotlin Multiplatform assomiglia più a Flutter di quanto sembri, ma i nomi sono
tutti diversi. Questa è la traduzione.

## Il dizionario Flutter → Kotlin

| Flutter / Dart | Qui | Note |
|---|---|---|
| `pubspec.yaml` | `gradle/libs.versions.toml` + `*/build.gradle.kts` | Le versioni stanno nel primo, le dipendenze nei secondi |
| `flutter pub get` | *niente* | Gradle scarica da sé al primo comando |
| `flutter run` | `./gradlew :desktopApp:run` | |
| `flutter run -d android` | `./gradlew :androidApp:installDebug` | Installa e basta: l'app poi la apri tu |
| `flutter build apk` | `./gradlew :androidApp:assembleDebug` | L'APK finisce in `androidApp/build/outputs/apk/` |
| `flutter build linux` | `./gradlew :desktopApp:packageDeb` | |
| `flutter test` | `./gradlew :shared:desktopTest` | |
| `flutter analyze` | `./gradlew compileKotlinDesktop` | Il compilatore Kotlin *è* l'analyzer |
| `flutter clean` | `./gradlew clean` | |
| `.dart_tool/` | `build/`, `.gradle/` | Entrambi già in `.gitignore` |
| Hot reload | *non c'è* | Su desktop c'è il "hot reload" di Compose solo in IntelliJ/Studio |

**La differenza che sorprende di più:** in Flutter `flutter run` fa tutto. Qui
`./gradlew <modulo>:<task>` è sempre `modulo` **due punti** `task`. I moduli di
questo progetto sono tre: `:shared`, `:androidApp`, `:desktopApp`.

## Usa sempre `./gradlew`, mai `gradle`

Il `gradle` di sistema su Pop!_OS è la 4.4.1 del 2017 e non funziona con questo
progetto. Il *wrapper* `./gradlew` scarica da sé la versione giusta (8.11.1) e
ignora quello di sistema. È l'equivalente di avere la versione di Flutter
inchiodata nel repo.

## I tre comandi che userai davvero

```bash
# 1. Gira sul desktop — il ciclo più veloce, non serve nessun SDK Android
./gradlew :desktopApp:run

# 2. Controlla che tutto compili e i test passino
./gradlew :shared:desktopTest

# 3. Installa su un telefono collegato (serve l'SDK Android)
./gradlew :androidApp:installDebug
```

Il primo apre una finestra vera con l'app dentro. È il modo migliore per provare
lettura, evidenziatore e impostazioni senza toccare Android.

## Desktop: provare adesso

```bash
./gradlew :desktopApp:run
```

Alla prima esecuzione scarica ~300 MB di dipendenze, poi parte in una ventina di
secondi. Le volte dopo, pochi secondi.

Cosa dovresti vedere: la finestra Athena, la libreria che si popola con
libri veri presi dai relay, e in fondo la barra con Library / Highlights /
Settings.

Per chiudere: chiudi la finestra, oppure `Ctrl+C` nel terminale.

### Vedere i log

L'app scrive su stdout, quindi li vedi già nel terminale dove hai lanciato
`run`. Le righe interessanti hanno il prefisso `[DEBUG]`, `[WARN]`, `[ERROR]`:

```
[DEBUG] SessionBootstrap: connecting to 4 relays
[DEBUG] RelayConnection: flushed 1 queued events to wss://nos.lol
```

### Pacchetto nativo `.deb`

```bash
./gradlew :desktopApp:packageDeb        # solo il .deb
./gradlew :desktopApp:packageDistributionForCurrentOS   # il formato di questo OS
```

Esce in `desktopApp/build/compose/binaries/main/deb/` (~97 MB: contiene un JVM
runtime ritagliato con `jlink`, quindi non serve Java sulla macchina di chi lo
installa).

Su Pop!_OS/Ubuntu i prerequisiti di `jpackage` — `binutils`, `fakeroot`,
`dpkg-deb` — sono già presenti di serie. Le icone sono opzionali: senza,
`jpackage` usa la sua di default.

## Android: cosa manca ancora

Serve l'SDK. `sdkmanager` c'è già sul sistema, ma l'SDK vero no:

```bash
sdkmanager --sdk_root=$HOME/Android/Sdk \
    "platforms;android-35" "build-tools;35.0.0" "platform-tools"
sdkmanager --sdk_root=$HOME/Android/Sdk --licenses
```

Poi va detto a Gradle dove sta, creando `local.properties` nella radice:

```properties
sdk.dir=/home/antona89/Android/Sdk
```

Se apri il progetto con Android Studio, quel file lo scrive lui.

Da lì:

```bash
./gradlew :androidApp:installDebug   # installa sul telefono collegato via adb
adb logcat | grep -E "Athena|RelayPool|Nip46|SyncSecret"
```

Per provare il login serve **Amber** installato sul telefono
(F-Droid / zapstore). Senza, la schermata Impostazioni mostra solo il campo
`bunker://`.

## Aprire il progetto in Android Studio

`File > Open` e scegli la **cartella del progetto**, non un file. Studio capisce
Gradle da solo. Alla prima apertura fa il "Gradle sync" — l'equivalente di
`flutter pub get`, ma lo fa da sé.

Due cose che disorientano arrivando da VS Code + Flutter:

- Il pannello **Build Variants** in basso a sinistra sceglie debug/release.
- Il menu a tendina in alto sceglie quale *run configuration* lanciare:
  `androidApp` per il telefono, `desktopApp` per la finestra.

## Quando qualcosa non va

```bash
# Errore incomprensibile? Vedi lo stack trace vero
./gradlew :desktopApp:run --stacktrace

# Stato sporco dopo un cambio di dipendenze
./gradlew clean

# Cache di Gradle corrotta (raro, ma succede)
./gradlew --stop && rm -rf .gradle build */build
```

Il daemon di Gradle resta vivo fra un comando e l'altro e rende i build
successivi molto più veloci. `./gradlew --stop` lo uccide.

## Dove mettere le mani

| Vuoi cambiare… | File |
|---|---|
| Colori e tema | `shared/src/commonMain/…/ui/theme/Color.kt` |
| Come appare la libreria | `…/ui/library/LibraryScreen.kt` |
| Il lettore e l'evidenziatore | `…/ui/reader/ReaderScreen.kt`, `HighlightableText.kt` |
| Relay di default | `…/data/session/SessionStore.kt` |
| Cosa viene pubblicato su nostr | `…/data/repository/`, `…/data/sync/` |

Tutto ciò che sta in `commonMain` vale per Android **e** desktop insieme.
