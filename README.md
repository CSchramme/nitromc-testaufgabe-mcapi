# NitroApiPlugin

Paper-Plugin für NitroMC, das Spielerinformationen über die REST-API von [mc-api.io](https://mc-api.io/docs) abruft und im Spiel ausgibt.
Die ursprüngliche Aufgabenstellung liegt in [`AUFGABE.md`](AUFGABE.md).

## Features

| Befehl | Permission | Beschreibung |
|---|---|---|
| `/mcinfo <spielername>` | `nitromc.command.mcinfo` | UUID, aktueller Name, Skin-URL, Profil-URL und Abrufzeitpunkt im Chat |
| `/mcinfo` (als Spieler, ohne Namen) | `nitromc.command.mcinfo` | Öffnet die Profil-GUI mit Namenseingabe |
| `/namehistory <spielername>` | `nitromc.command.namehistory` | Bekannte Namen des Spielers in chronologischer Reihenfolge |

Außerdem:

* alle HTTP-Requests laufen asynchron über `HttpClient#sendAsync` (`CompletableFuture`), nie im Server-Thread
* Ausgabe zurück auf dem Main-Thread über den Bukkit-Scheduler
* klare Fehlermeldungen für ungültige Namen, unbekannte Spieler, Rate-Limit, Timeout, API-Ausfall
* Retry mit exponentiellem Backoff bei temporären Fehlern (Netzwerk, Timeout, `429`, `5xx`)
* Rate-Limit-Handling: `X-RateLimit-Reset`/`Retry-After` wird beachtet
* Cache mit TTL; gleichzeitige Anfragen für denselben Spieler teilen sich einen Request
* Profil-GUI: Namenseingabe im Amboss, Anzeige mit Spielerkopf im echten Skin und dem ganzen Skin als Pixel-Bild im Tooltip, Lade- und Fehlerzustand
* Tab-Completion für Online-Spieler
* konfigurierbar über `config.yml`
* 26 Unit-Tests (Parser, Cache, Service gegen einen lokalen HTTP-Stub-Server, Skin-Vorschau)
* klickbare Chat-Komponenten: UUID kopieren, Skin- und Profil-Link öffnen
* alle Chat-Ausgaben laufen über `MessageUtil`

## Installation

Voraussetzungen: **Java 21**, ein **Paper-Server 1.21.4**.

```bash
./mvnw clean package
```

Unter Windows `mvnw.cmd clean package`. Eine lokale Maven-Installation ist nicht nötig, der Maven Wrapper lädt Maven selbst.

1. `target/NitroApiPlugin-1.0.0.jar` in den `plugins/`-Ordner des Servers kopieren.
2. Server starten. Die `plugins/NitroApiPlugin/config.yml` wird beim ersten Start erzeugt.
3. Die Permissions sind standardmäßig nur für OPs freigegeben (`default: op`).

## Konfiguration

```yaml
api:
  base-url: "https://mc-api.io"
  connect-timeout-ms: 5000    # Timeout für den Verbindungsaufbau
  request-timeout-ms: 10000   # Timeout für die komplette Anfrage
  max-retries: 2              # Wiederholungen bei temporären Fehlern
  retry-base-delay-ms: 500    # Backoff: 500 ms, 1000 ms, 2000 ms, ...

cache:
  ttl-seconds: 300            # 0 deaktiviert den Cache
  max-entries: 500

date-format: "dd.MM.yyyy HH:mm"
```

## API-Nutzung

Die in der Aufgabenstellung genannten Endpoints (`/api/v1/profile/<name>`, `/api/v1/name-history/<uuid>`) existieren nicht mehr, sie liefern `404`.
Laut der [aktuellen Dokumentation](https://mc-api.io/docs) nutzt das Plugin daher:

```text
GET https://mc-api.io/profile/{name}/JAVA
```

Ein einziger Request liefert alles, was beide Befehle brauchen:

```json
{
  "name": "Notch",
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "decodedTexture": {
    "textures": { "SKIN": { "url": "http://textures.minecraft.net/texture/2920..." } }
  },
  "names": { "Notch": 1704063600000 },
  "cachedAt": 1791730208415
}
```

* **UUID und Name** kommen direkt aus `uuid` und `name`. Ein separater Request an `/uuid/{name}` ist dadurch unnötig.
* **Skin-URL** kommt aus `decodedTexture.textures.SKIN.url` (auf `https` umgestellt). Fehlt sie, nutzt der Spieler den Standard-Skin.
* **Profil-URL** ist `https://namemc.com/profile/<uuid>`.
* **Namenshistorie** kommt aus `names` (Name → Zeitstempel in ms), chronologisch sortiert.

**Hinweis zur Namenshistorie:** Mojang hat die öffentliche Name-History-API im September 2022 abgeschaltet. mc-api.io kann daher nur die Namen liefern, die es selbst beobachtet hat, und lässt das Feld `names` meistens weg. Das Plugin zeigt dann den aktuellen Namen und einen entsprechenden Hinweis. Liefert die API mehr Einträge, werden alle angezeigt.

Alternativen wie NameMC, laby.net oder crafty.gg bieten keine offene API für die Historie. Sie blocken automatisierte Anfragen per Bot-Schutz (Cloudflare, Challenge-Token). Diesen Schutz zu umgehen, würde gegen deren Nutzungsbedingungen verstoßen. Das Plugin nutzt deshalb bewusst nur mc-api.io.

### Fehlerbehandlung

Fehlerantworten der API haben die Form `{"error": true, "message": "...", "cause": "..."}`. Der Service bildet sie auf `ApiException.Reason` ab:

| Situation | Reason | Retry | Meldung im Spiel |
|---|---|---|---|
| Name verletzt `[A-Za-z0-9_]{1,16}` (vorab geprüft) oder `400` | `INVALID_NAME` | nein | Ungültiger Spielername |
| `404` | `PLAYER_NOT_FOUND` | nein | Kein Spieler mit diesem Namen gefunden |
| `429` | `RATE_LIMITED` | ja, wenn Reset ≤ 10 s | Zu viele Anfragen |
| `500`, `502`, `503`, `504` | `SERVICE_UNAVAILABLE` | ja | API momentan nicht verfügbar |
| Timeout | `TIMEOUT` | ja | API hat nicht rechtzeitig geantwortet |
| Verbindungsfehler | `NETWORK_ERROR` | ja | Verbindung fehlgeschlagen |
| Kaputtes JSON / fehlende Felder | `INVALID_RESPONSE` | nein | Unerwartete Antwort (wird geloggt) |

## Architektur

```text
src/main/java/net/nitromc/api/
├── NitroApiPlugin.java              Lädt die Config, erstellt den Service, registriert Befehle
├── command/
│   ├── PlayerLookupCommand.java     Gemeinsamer Ablauf: Eingabe prüfen → async laden → sync ausgeben
│   ├── McInfoCommand.java           Ausgabe von /mcinfo
│   └── NameHistoryCommand.java      Ausgabe von /namehistory
├── gui/
│   ├── ProfileGui.java              Steuert den Ablauf (Eingabe → async Laden → Anzeige), Inventar-Events
│   ├── PlayerSearchMenu.java        Amboss-Fenster zur Namenseingabe
│   ├── ProfileMenu.java             Profilanzeige (eigener InventoryHolder): Laden, Profil, Fehler
│   ├── SkinFrontView.java           Setzt aus der Skin-Datei die Vorderansicht (16×32 Pixel) zusammen
│   └── MenuItems.java               Item-Erzeugung, Farbcodes über MessageUtil.colorize
├── service/
│   ├── McApiService.java            HTTP-Kommunikation, Retry, Rate-Limit, Cache
│   ├── McApiResponseParser.java     JSON (Gson) → Models
│   ├── AsyncCache.java              Thread-sicherer TTL-Cache für CompletableFutures
│   ├── ApiSettings.java             Konfiguration des Services
│   └── ApiException.java            Fehler mit Ursache (Reason)
├── model/
│   ├── PlayerProfile.java
│   ├── NameHistoryEntry.java
│   ├── SkinModel.java               Normale (Steve) oder schmale (Alex) Arme
│   └── SkinTexture.java             Signierte Textur-Daten für den Spielerkopf
└── util/
    ├── MessageUtil.java             vorgegeben, um sendClickable(...) für klickbare Nachrichten ergänzt
    └── ApiErrorMessages.java        Fehlertexte für Chat und GUI
```

**Trennung der Schichten**

* **Command** kennt nur den Service und die Models. Die Commands prüfen die Eingabe, starten den Abruf und formatieren die Ausgabe. Fehlertexte für den Spieler entstehen nur hier.
* **Service** hat keine Bukkit-Abhängigkeit und wird deshalb ohne laufenden Server getestet. Er liefert ausschließlich `CompletableFuture`s.
* **Model** sind unveränderliche `record`s ohne Logik zur API.

**Async-Ablauf** am Beispiel `/mcinfo Notch`:

```text
Server-Thread   onCommand → Name validieren → "Lade Daten..." → service.fetchProfile()  (kehrt sofort zurück)
HTTP-Thread     sendAsync → ggf. Retry (delayedExecutor) → JSON parsen → PlayerProfile
Server-Thread   Bukkit.getScheduler().runTask(...) → MessageUtil.send(...)
```

Vor der Ausgabe wird geprüft, ob das Plugin noch aktiv und der Spieler noch online ist. Beim Deaktivieren werden `HttpClient` und Thread-Pool heruntergefahren.

## Profil-GUI

Führt ein Spieler `/mcinfo` **ohne Namen** aus, öffnet sich ein Amboss mit Eingabefeld. Ist der eingegebene Name gültig, erscheint rechts ein Namensschild. Ein Klick darauf startet die Suche. Danach öffnet sich das Profil-Menü:

```text
[ ][ ][ ][ ][☻][ ][ ][ ][ ]   ☻ Spielerkopf mit echtem Skin
[ ][N][ ][U][ ][H][ ][A][ ]   N Name · U UUID · H Namenshistorie · A Abgerufen
[ ][ ][ ][⚒][ ][✕][ ][ ][ ]   ⚒ neue Suche · ✕ schließen

Titel: „PROFIL • <name>“ im NitroMC-Farbverlauf
```

* **Ablauf**: Das Menü öffnet sofort mit einem Lade-Zustand. Der Request läuft asynchron, das Ergebnis wird auf dem Main-Thread eingesetzt. Bei Fehlern (Spieler nicht gefunden, API down, …) zeigt das Menü die Fehlermeldung an. Über den Amboss-Button lässt sich direkt der nächste Name suchen.
* **Klicks**: Ein Klick auf die UUID oder den Kopf schließt das Menü und schickt eine anklickbare Nachricht. Bei der UUID kopiert der Klick in die Zwischenablage, beim Kopf öffnet er den Skin-Link. Aus einem Inventar heraus kann Minecraft nicht direkt kopieren, das geht nur über eine Chat-Komponente.
* **Spielerkopf**: Die API liefert im Profil das von Mojang signierte `textures`-Property mit. Es wird direkt auf den Kopf gesetzt (`ProfileProperty`), ohne weiteren Request an Mojang.
* **Ganzer Skin**: Beim Hovern über den Kopf zeigt der Tooltip den kompletten Spieler von vorne als Pixel-Bild aus farbigen `█`-Zeichen, ohne Resource Pack.
  * Der Service lädt dazu die Skin-Datei asynchron von der Skin-URL aus der API, mit Retry und Cache. Erlaubt ist nur `https://textures.minecraft.net`.
  * `SkinFrontView` setzt daraus die Vorderansicht zusammen: Kopf, Körper, Arme und Beine, jeweils mit zweiter Ebene (Hut, Jacke, Ärmel, Hose). Schmale Arme (`metadata.model = slim`) und das alte 64×32-Format werden berücksichtigt.
  * Die Vorschau wird nachgeladen, das restliche Profil erscheint sofort.
* **Schutz**: Beide Menüs brechen alle Klicks und Drags ab. Der Amboss wird beim Schließen geleert, damit Vanilla dem Spieler kein Papier zurückgibt. Beim Deaktivieren des Plugins werden offene Menüs geschlossen.
* `/mcinfo <name>` liefert unverändert die geforderte Chat-Ausgabe. Die GUI ist eine Ergänzung für den Aufruf ohne Namen. In der Konsole zeigt `/mcinfo` ohne Namen weiterhin die Verwendung an.

## Erweiterung von `MessageUtil`

`MessageUtil` sendet nur reinen Text. Für klickbare Nachrichten ist eine Methode dazugekommen, die bestehenden sind unverändert:

```java
MessageUtil.sendClickable(sender, "&7UUID: &f" + uuid, "&eKlicken zum Kopieren", ClickEvent.copyToClipboard(uuid));
```

Sie nutzt denselben Prefix und dieselben Farbcodes (`colorize`) und baut daraus eine Adventure-Component mit Hover-Text und Klick-Aktion. In `/mcinfo <name>` ist die UUID damit kopierbar, Skin- und Profil-URL lassen sich anklicken. Der Text der Ausgabe bleibt gleich, in der Konsole erscheint er wie bisher.

## Beispielausgaben

Getestet auf **Paper 1.21.4 (Build 232)** mit Java 21. Die Befehle wurden über die Server-Konsole ausgeführt, gegen die echte mc-api.io.
Unten ein unveränderter Auszug aus `logs/latest.log`. `[Server thread]` zeigt, dass die Ausgabe nach dem async Request wieder auf dem Main-Thread erfolgt. Der Prefix `NITROMC »` kommt aus `MessageUtil`, im Spiel erscheint er im Farbverlauf.

`/mcinfo Notch`

```text
[17:11:10] [Server thread/INFO]: NITROMC » Lade Daten für Notch...
[17:11:10] [Server thread/INFO]: NITROMC » UUID: 069a79f4-44e9-4726-a5be-fca90e38aaf5
[17:11:10] [Server thread/INFO]: NITROMC » Name: Notch
[17:11:10] [Server thread/INFO]: NITROMC » Skin: https://textures.minecraft.net/texture/292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680
[17:11:10] [Server thread/INFO]: NITROMC » Profil: https://namemc.com/profile/069a79f4-44e9-4726-a5be-fca90e38aaf5
[17:11:10] [Server thread/INFO]: NITROMC » Abgerufen: 11.10.2026 17:11
```

`/mcinfo notch` drei Sekunden später. Die Antwort kommt aus dem Cache, daher ist sie sofort da und der Abrufzeitpunkt unverändert:

```text
[17:11:13] [Server thread/INFO]: NITROMC » Lade Daten für notch...
[17:11:13] [Server thread/INFO]: NITROMC » UUID: 069a79f4-44e9-4726-a5be-fca90e38aaf5
[17:11:13] [Server thread/INFO]: NITROMC » Name: Notch
[17:11:13] [Server thread/INFO]: NITROMC » Skin: https://textures.minecraft.net/texture/292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680
[17:11:13] [Server thread/INFO]: NITROMC » Profil: https://namemc.com/profile/069a79f4-44e9-4726-a5be-fca90e38aaf5
[17:11:13] [Server thread/INFO]: NITROMC » Abgerufen: 11.10.2026 17:11
```

`/namehistory jeb_`

```text
[17:11:16] [Server thread/INFO]: NITROMC » Lade Daten für jeb_...
[17:11:16] [Server thread/INFO]: NITROMC » Namenshistorie von jeb_:
[17:11:16] [Server thread/INFO]: NITROMC » 1. jeb_
[17:11:16] [Server thread/INFO]: NITROMC » Keine früheren Namen bekannt. Mojang stellt die Namenshistorie seit 2022 nicht mehr bereit, daher liefert die API nur die ihr bekannten Namen.
```

Fehlerfälle: `/mcinfo zzqqxxnotexist12`, `/mcinfo bad!name`, `/mcinfo`, `/namehistory a b`

```text
[17:11:19] [Server thread/INFO]: NITROMC » Lade Daten für zzqqxxnotexist12...
[17:11:19] [Server thread/INFO]: NITROMC » Es wurde kein Spieler mit dem Namen zzqqxxnotexist12 gefunden.
[17:11:22] [Server thread/INFO]: NITROMC » Ungültiger Spielername. Erlaubt sind 1–16 Zeichen (a–z, 0–9, _).
[17:11:25] [Server thread/INFO]: NITROMC » Verwendung: /mcinfo <spielername>
[17:11:28] [Server thread/INFO]: NITROMC » Verwendung: /namehistory <spielername>
```

Liefert die API mehrere Namen (Fall aus den Unit-Tests, da mc-api.io aktuell für keinen getesteten Spieler eine Historie liefert), sieht die Ausgabe so aus:

```text
NITROMC » Namenshistorie von LukasDev_:
NITROMC » 1. LukasDev
NITROMC » 2. TheLukasDev (13.09.2020 14:26)
NITROMC » 3. LukasDev_ (01.01.2024 00:00)
```

## Tests

```bash
./mvnw test
```

* `SkinFrontViewTest`: Position aller Körperteile, zweite Ebene, schmale Arme, gespiegelte Gliedmaßen und Hut-Regel bei 64×32-Skins
* `McApiResponseParserTest`: Skin-Modell (slim/classic), Profil-Parsing, UUID ohne Bindestriche, fehlender Skin, Sortierung der Namenshistorie, kaputtes JSON, Fehlermeldungen
* `AsyncCacheTest`: TTL, geteilte laufende Requests, keine gecachten Fehler, Verdrängung bei vollem Cache
* `McApiServiceTest`: Service gegen einen lokalen `HttpServer`. Abgedeckt sind Erfolg, Cache, ungültige Namen (kein Request), `404` ohne Retry, Retry bei `503`/`429`, Abbruch nach `max-retries`, lange Rate-Limits, Timeout und ungültige Antworten.
"# nitromc-testaufgabe-mcapi" 
