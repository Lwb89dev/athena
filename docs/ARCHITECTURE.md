# Athena — architettura

## L'idea in una riga

Una biblioteca pubblica dove i libri sono eventi nostr, la lettura è locale, e
tutto ciò che è "tuo" (posizione, sottolineature, preferiti) è un evento firmato
da te su relay che scegli tu. Nessun server di Athena: se il progetto muore,
la tua libreria no.

## Mappa dei NIP

Il punto di partenza è stato: **niente formati inventati**. Tutto quello che
serve esiste già come NIP o come convenzione già in uso.

| Funzione | Kind | Spec | Note |
|---|---|---|---|
| Indice del libro | `30040` | NKBIP-01 | I tag `a` elencano le sezioni, in ordine di lettura |
| Sezione / capitolo | `30041` | NKBIP-01 | Contenuto in AsciiDoc |
| Articolo singolo | `30023` | NIP-23 | Trattato come libro a una sezione |
| Login Android | — | **NIP-55** | Intent `nostrsigner:` verso Amber |
| Login desktop | `24133` | **NIP-46** | `bunker://`, JSON-RPC cifrato NIP-44 su relay |
| Sottolineature | `9802` | **NIP-84** | Il contenuto dell'evento *è* il passaggio evidenziato |
| Preferiti pubblici | `30003` | NIP-51 | Tag `a` leggibili: servono a essere trovati |
| Preferiti privati | `30078` | NIP-78 + NIP-44 | Slot blinded, solo ciphertext |
| Sottolineature private | `30078` | NIP-78 + NIP-44 | Slot blinded, solo ciphertext |
| Posizione di lettura | `30078` | NIP-78 | Uno slot **blinded** per libro, solo ciphertext |
| Segreto di blinding | `30078` | NIP-78 | Un evento di bootstrap, cifrato a sé stessi |
| Cifratura | — | **NIP-44 v2** | ChaCha20 + HMAC-SHA256 + HKDF, implementato in Kotlin puro |
| Cancellazione | `5` | NIP-09 | Ritira una sottolineatura pubblicata |
| Profili autore | `0` | NIP-01 | Risolve il vero autore, non il tag `author` |
| Lista relay | `10002` | NIP-65 | Unita ai relay di bootstrap |

### Perché 30040/30041 e non un formato nostro

Il progetto si chiama Athena (dea della conoscenza) per non confondersi con
il lettore [Alexandria di GitCitadel](https://next-alexandria.gitcitadel.eu),
che ha definito NKBIP-01. Usare i suoi kind significa che l'app apre da subito
i libri già pubblicati sui relay, invece di partire da una biblioteca vuota
che va riempita a mano.

### Le uniche cose non standard

Due tag che nessun NIP definisce, prefissati `project_athena_` perché gli altri
client li ignorino invece di rompersi:

- `project_athena_color` — colore dell'evidenziatore
- `project_athena_range` — offset carattere iniziale e finale nella sezione

Una sottolineatura fatta qui resta un evento NIP-84 perfettamente valido
ovunque; semplicemente altrove è gialla e senza posizione esatta.

## Struttura dei moduli

```
shared/
  commonMain/     nostr, data, domain, ui  ← ~95% del codice
  androidMain/    AmberSigner (NIP-55), Room/Context, engine OkHttp
  desktopMain/    DesktopSignerFactory (NIP-46), path XDG, engine Java
androidApp/       Application + MainActivity
desktopApp/       main() + jpackage → .deb / .exe
```

Il confine è disegnato su *cosa cambia davvero fra piattaforme*: come si firma,
dove stanno i file, quale engine HTTP. Tutto il resto — relay, crittografia,
repository, ViewModel e l'intera UI Compose — è scritto una volta sola.

## Perché il feed di default non è globale

Kind 30023 su nostr è aperto a chiunque, e una fetta consistente è spam, molto
del quale pornografico. Un feed globale come schermata iniziale renderebbe
l'app inutilizzabile come biblioteca al primo avvio.

Il filtro è il **grafo sociale dell'utente**, non una curatela nostra:

| Scope | Fonte |
|---|---|
| **Following** (default da loggati) | NIP-02 kind 3, la lista contatti che ogni client già mantiene |
| **Liste curate** | NIP-51 kind 30000 follow set — "classici", "filosofia" |
| **Globale** | Tutto, opt-in dal menu a tendina, con l'avvertenza scritta |

Nessuno si nomina censore: si mostra ciò che *tu* hai scelto di seguire. Chi non
è loggato non ha grafo sociale, quindi parte da Globale — con Following vuoto e
inspiegabile sarebbe peggio.

Il filtro vale anche **in lettura dalla cache**, non solo nel fetch: altrimenti
lo spam scaricato ieri in Globale ricomparirebbe oggi in Following.

## Il gateway: caricare libri

Trascini un file sulla finestra (o lo scegli), correggi i metadata, decidi chi
può leggerlo.

**Formati:** EPUB, Markdown, AsciiDoc, testo semplice. L'EPUB segue lo *spine*
dell'OPF, non l'ordine dei nomi file — è la differenza fra un libro e un mucchio
di capitoli, perché gli editori chiamano i file come vogliono.

L'editing dei metadata non è un vezzo: un file si chiama `pg1342.epub` e senza
titolo e autore corretti non è una voce di biblioteca, è un blob.

**Pubblico o cifrato — è una scelta legale, non estetica:**

- **Pubblico** → NKBIP-01 normale: un 30041 per capitolo più un 30040 di indice.
  Leggibile da qualsiasi client nostr. Per opere di pubblico dominio.
- **Privato** → capitoli e indice NIP-44 cifrati a indirizzi blinded. Solo i tuoi
  dispositivi. Per i libri che possiedi ma non puoi ripubblicare.

Quella seconda opzione esiste per un motivo concreto: la maggior parte dei libri
che una persona possiede non è ripubblicabile legalmente, e un'app che offrisse
solo "pubblica al mondo" starebbe invitando alla violazione di copyright senza
dirlo.

I capitoli vengono pubblicati **prima** dell'indice, sempre: l'indice li
referenzia per coordinata, quindi l'ordine inverso creerebbe un libro
temporaneamente rotto.

## Flussi che vale la pena conoscere

### Lettura (anonima)

`RelayPool.fetch()` → verifica firma di **ogni** evento → cache Room → la UI
osserva solo Room. I relay sono mirror non fidati: la verifica è in un solo
punto, `RelayPool.handle()`, e niente entra nella cache senza passarci.

La UI non legge mai direttamente dalla rete. Questo è il motivo per cui il
lettore funziona offline e non sfarfalla quando un relay risponde tardi.

### Sottolineatura

1. L'utente seleziona del testo (`HighlightableText`, vedi sotto)
2. Riga scritta in Room **subito**, `published = false` → il marker appare sotto il dito
3. In parallelo: evento 9802 → firma → publish → l'id locale viene sostituito dall'id evento

Se la firma fallisce (utente annulla, Amber assente), la sottolineatura resta
locale con `published = false` e può essere ripubblicata dopo. Non si perde nulla.

### Posizione di lettura

Scrittura locale a ogni scroll (debounce 1,5 s), pubblicazione su relay **solo
all'uscita dal lettore**. Un evento NIP-78 per pagina girata significherebbe un
round-trip col signer per pagina girata: inusabile.

### Perché Amber viene chiamato in due modi

NIP-55 ha due trasporti. L'intent mostra la schermata di approvazione; il
ContentProvider funziona in silenzio se l'utente ha già concesso il permesso.
`AmberSigner` prova **sempre prima** il provider e cade sull'intent solo se
questo rifiuta. Senza questo, salvare la posizione di lettura aprirebbe Amber
in faccia all'utente ogni volta.

## La scelta tecnica meno ovvia: il testo del lettore

`SelectionContainer` di Compose disegna la selezione ma **non dice cosa è stato
selezionato**. Un evidenziatore ha bisogno del range di caratteri.

Un `BasicTextField` in `readOnly = true` lo espone come
`TextFieldValue.selection`, continuando a comportarsi come un blocco di prosa:
niente cursore, handle di selezione nativi, toolbar di sistema. Per questo il
testo del libro vive in un campo di testo e non in un `Text`.

I marker esistenti sono `SpanStyle` sull'`AnnotatedString`, non overlay
posizionati: così seguono il testo quando riflowa al cambio di font o rotazione.

## La crittografia

NIP-44 v2 è scritto a mano in `commonMain`, senza dipendenze crypto di
piattaforma: `javax.crypto` espone ChaCha20 solo da Android API 28, sotto il
nostro minSdk 26. Sessanta righe di aritmetica ben specificata costano meno che
alzare il minSdk o impacchettare un provider.

È l'unica parte del progetto dove un errore è **silenzioso**: una cifratura
sbagliata ma auto-consistente supera qualunque test di round-trip e resta
illeggibile a ogni altro client nostr. Per questo `Nip44VectorsTest` è generato
dai vettori ufficiali `nip44.vectors.json` e confronta i payload byte per byte —
35 conversation key, 10 payload, 24 casi di padding.

## Threat model dei dati privati

Il relay è considerato ostile. Ogni cosa privata passa da `EncryptedSync`, e
niente altro scrive su relay in chiaro.

Un evento NIP-78 perde informazioni su **quattro canali** anche col contenuto
cifrato. Ognuno è chiuso deliberatamente:

| Canale | Difesa |
|---|---|
| **Indirizzo** | Il `d` tag è indicizzato: `REQ {"#d": [...]}` restituisce chi lo usa. Il tag è `HMAC-SHA256(segreto, "namespace:valore")` — hex opaco, non collegabile |
| **Contenuto** | NIP-44 self-encryption, solo ciphertext. Mai un tag, mai un campo, mai un fallback in chiaro |
| **Dimensione** | Il plaintext è imbottito a bucket da 4 KiB prima di cifrare. Il padding di NIP-44 è troppo fine per nascondere "un preferito" da "cinquanta" |
| **Tempo** | `created_at` arrotondato all'ora UTC, con bump monotono persistito perché l'ordinamento dei replaceable sopravviva |

In lettura c'è la quinta difesa: un **high-water mark** per slot, così un relay
non può rigiocare uno snapshot vecchio ma validamente firmato per resuscitare
dati cancellati.

C'è poi una sesta difesa che non è codice: `relay.nostr.band` è fuori dai relay
di default, benché sia ottimo per la scoperta dei libri. Alimenta un
indicizzatore pubblico, e un indirizzo blinded non serve a niente contro un
archivista che tiene tutto per sempre. Stessa scelta fatta in roadstr.

### Il buco che questo chiude

`roadstr/lib/services/favorites_sync_service.dart` documenta un limite onesto:

> *un segreto per-utente non può essere condiviso fra dispositivi sul percorso
> Amber, dove l'app non vede mai la nsec* — quindi il `d` tag deriva da
> `sha256(costante + pubkey)`, e un controllo mirato "questo npub usa l'app?"
> resta calcolabile.

Qui la via d'uscita è che **la decifratura NIP-44 è disponibile tramite il
signer anche se la chiave no**. Quindi:

1. il primo dispositivo tira 32 byte casuali,
2. li sigilla verso sé stesso e pubblica il ciphertext in **un** evento NIP-78,
   a uno slot derivato dalla pubkey,
3. ogni dispositivo successivo prende quell'evento e chiede al signer di aprirlo.

Da lì in poi entrambi derivano indirizzi identici e non indovinabili. Il
risultato: prima il `d` tag era
`athena:progress:30040:<autore>:<libro>` — **un relay vedeva in chiaro
quale libro stavi leggendo**, e poteva elencare tutti i lettori di un dato
libro. Ora vede 64 caratteri esadecimali senza struttura.

### Il limite, e come si elimina

Lo slot di bootstrap **deve** essere derivabile da informazione pubblica: è il
punto d'ingresso. Un relay può quindi rispondere a "questo npub usa Athena?"
— e qualcuno potrebbe agire su quella risposta.

Il problema è informazione-teorico: senza (a) la nsec, (b) un segreto fornito
dall'utente, o (c) un segreto già trasferito, non esiste modo di derivare un
indirizzo non indovinabile. Il bootstrap è la (c) fatta via relay, ed è l'unica
che lascia traccia. La (b) la elimina del tutto.

Quindi la modalità di sync è una **scelta esplicita dell'utente**, con il default
sulla più conservativa:

| Modalità | Cosa vede un relay |
|---|---|
| **Off** (default) | Niente. Sottolineature private e posizioni restano sul dispositivo |
| **Passphrase** (consigliata) | Niente di sondabile: il segreto è `PBKDF2(passphrase, salt = pubkey)`, 600k iterazioni, **non viene pubblicato nulla** |
| **Automatica** | Comodità in cambio del fatto che "questo npub usa Athena" diventa verificabile |

Il default è `Off` perché accendere una funzione che pubblica un marcatore
individuabile non è una decisione che un'app debba prendere al posto di
qualcuno.

Della passphrase viene salvato **il segreto derivato, mai la frase**: se lo
storage del dispositivo trapela, il danno resta circoscritto agli slot di
Athena invece di consegnare una passphrase che l'utente potrebbe riusare
altrove.

### Il residuo che resta anche con la passphrase

Un relay vede comunque che quella pubkey pubblica eventi kind 30078 con `d` tag
esadecimali. Non è "usa Athena": è "usa *un'app* attenta alla privacy" — un
insieme di anonimato molto più grande. È lo stesso residuo che roadstr chiama
"la *forma* del tag è essa stessa un indizio", e non si può chiudere senza
rinunciare all'indirizzo non indovinabile.

### Perché pubblico e privato sono due meccanismi, non un flag

Un preferito **pubblico** è un set NIP-51 con tag `a` leggibili, così ogni
client nostr può renderizzare lo scaffale — è esattamente il punto di renderlo
pubblico, e nasconderlo romperebbe l'interop senza guadagno. Un preferito
**privato** finisce in uno slot blinded. Lo stesso vale per le sottolineature:
pubbliche sono eventi NIP-84 citabili, private sono ciphertext.

Due dettagli minori chiusi durante l'audit: la richiesta di cancellazione
NIP-09 non porta più una motivazione testuale che nominava l'app su un evento
pubblico, e il set pubblico dei preferiti non viene pubblicato vuoto — per chi
tiene solo preferiti privati sarebbe stato un annuncio gratuito.

## Il markup e gli offset

Le sottolineature sono offset di carattere sul **sorgente**, non sul testo
renderizzato. Se il rendering mangiasse `== ` e `**` senza dire nulla, ogni
marker esistente finirebbe nel punto sbagliato al primo aggiornamento del
renderer. Per questo `MarkupRenderer` produce, insieme al testo, la traduzione
in entrambe le direzioni (`RenderedText`).

Il caso che va guardato due volte è l'estremo destro: è *esclusivo*, e mapparlo
direttamente cade sul carattere emesso successivo, inghiottendo il marker in
mezzo — una selezione di `emphasis` tornava come `emphasis*`. Lo risolve
`toSourceRange`, ed è coperto da un test.

## Cosa resta aperto

Nessuno stub: non ci sono più `TODO` né `NotImplementedError` nel codice. Restano
scelte consapevoli, non lavori a metà:

| Cosa | Stato |
|---|---|
| Chiave di sessione NIP-46 | Salvata in chiaro nello storage privato dell'app. È una credenziale revocabile, non la chiave d'identità — ma il passo successivo è il keystore di piattaforma |
| Slot di bootstrap | Derivabile da informazione pubblica per necessità: consente un controllo mirato "questo npub usa Athena?" |
| Scelta dei relay per il sync | Gli eventi cifrati vanno a *tutti* i relay del pool. Un pool separato per il sync privato sarebbe più preciso della sola scelta dei default |
| AsciiDoc | Coperti titoli, grassetto, corsivo, codice, citazioni, elenchi. Tabelle, note e include restano testo semplice invece di essere maciullati |
| Target Android | Compila e produce APK release (splittati per ABI), firmato con una keystore di release dedicata tenuta fuori dal repo (`keystore.properties`) |
| `auth_url` del bunker | Esposto come `SignerManager.authUrl`; la schermata impostazioni lo mostra, ma non apre il browser da sola |
