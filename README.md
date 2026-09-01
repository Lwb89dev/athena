# Project Athena

**v0.1.0** — Una biblioteca online libera, costruita interamente su nostr. Il
nome è Athena, la dea della conoscenza.

Chiunque può leggere senza account. Chi fa login con la propria chiave si porta
dietro, da un dispositivo all'altro, il punto in cui è arrivato, le
sottolineature e i preferiti — pubblici o privati, deciso passaggio per
passaggio.

**Non c'è un server di Athena.** I libri sono eventi nostr su relay
pubblici, le tue annotazioni sono eventi firmati da te. L'app è un lettore.

## Stato

MVP funzionale, senza stub: NIP-44 e NIP-46 sono implementati, non abbozzati.
32 test passano, di cui i vettori ufficiali NIP-44 confrontati byte per byte.

Compila ed è stato buildato su entrambi i target come progetto Kotlin
Multiplatform:

- **Android** (`androidApp`) — login via **Amber** (NIP-55), APK release
  splittato per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) più un universale
- **Desktop** (`desktopApp`) — `.deb` / `.exe` via jpackage, login via **bunker** (NIP-46)

Circa il 95% del codice — nostr, crittografia, database, repository, ViewModel e
tutta la UI Compose Material 3 — vive in `shared/commonMain` ed è scritto una
volta sola per entrambi.

Cosa è già in piedi e cosa no: vedi [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Come si costruisce

Se arrivi da Flutter, parti da **[docs/BUILD.md](docs/BUILD.md)**: c'è la
traduzione comando per comando.

```bash
# Android: installa il debug APK sul dispositivo collegato
./gradlew :androidApp:installDebug

# Android: APK release splittato per ABI (+ un universale), in androidApp/build/outputs/apk/release/
./gradlew :androidApp:assembleRelease

# Desktop: avvia in sviluppo
./gradlew :desktopApp:run

# Desktop: produce il pacchetto nativo (.deb su Linux, .exe su Windows)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Test (crittografia, NIP-19, parsing bunker)
./gradlew :shared:desktopTest
```

Il wrapper è nel repo e scarica da sé Gradle 8.11.1: **non serve** avere Gradle
installato, e quello di sistema viene ignorato. Usa sempre `./gradlew`, mai
`gradle`.

## Icone

Tutte le icone — adaptive Android, bitmap legacy, `.png` Linux, `.ico` Windows,
512x512 per lo store — si generano da un unico PNG sorgente:

```bash
python3 tools/make-icons.py artwork/icon-source.png
```

Lo script rimpicciolisce l'artwork nella *safe zone* dell'adaptive icon (il
launcher può mascherare tutto fuori dal 66/108 centrale) e dipinge il layer di
sfondo dello stesso navy campionato dal sorgente, così la giunzione non si vede.

## Su cosa poggia

Tutto quello che serve esisteva già come specifica. Niente formati inventati:

| | |
|---|---|
| Libri | NKBIP-01 (kind `30040` + `30041`), NIP-23 come fallback |
| Sottolineature | **NIP-84**, kind `9802` |
| Preferiti | NIP-51 set (`30003`), privati cifrati NIP-44 |
| Sincronizzazione lettura | NIP-78 (`30078`), cifrato a sé stessi |
| Login | NIP-55 (Amber) e NIP-46 (bunker), entrambi implementati |
| Cifratura | NIP-44 v2, verificato sui vettori ufficiali |
| Cancellazione | NIP-09 |
| Feed | NIP-02 (contatti) e NIP-51 (liste) per filtrare lo spam |
| Relay | NIP-65 |

Le uniche due cose non standard sono il colore dell'evidenziatore e l'offset del
passaggio, prefissati `project_athena_` così che gli altri client li ignorino. Una
sottolineatura fatta qui resta un evento NIP-84 valido ovunque.

## Note

Su nostr esiste già [Alexandria di
GitCitadel](https://next-alexandria.gitcitadel.eu), il lettore che ha definito
NKBIP-01. Questa app si chiama Athena proprio per non confondersi con quella.
I kind restano i loro: così si aprono da subito i libri già pubblicati sui
relay, invece di partire da una biblioteca vuota.

## Licenza

Da decidere. Per una biblioteca pubblica, MIT o AGPL sono le due direzioni
sensate a seconda di quanto vuoi che i fork restino aperti.
