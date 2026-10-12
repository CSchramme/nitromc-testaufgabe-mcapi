# NitroApiPlugin

Paper Plugin (1.21.4) für die NitroMC Testaufgabe. Holt Spielerinfos von [mc-api.io](https://mc-api.io/docs). Die Aufgabe steht in `AUFGABE.md`.

## Befehle

- `/mcinfo <name>` zeigt UUID, Name, Skin, Profil-Link und wann es abgerufen wurde
- `/mcinfo` ohne Namen öffnet eine GUI, da kann man den Namen eingeben und sieht den Skin
- `/namehistory <name>` zeigt die bekannten Namen

Permissions: `nitromc.command.mcinfo` und `nitromc.command.namehistory` (standardmäßig nur OP)

## Installieren

1. Java 21 installieren
2. `./mvnw clean package` ausführen (Windows: `mvnw.cmd clean package`)
3. `target/NitroApiPlugin-1.0.0.jar` in den `plugins` Ordner vom Server packen und starten

In der `config.yml` kann man Timeouts, Retries und den Cache einstellen.

## Wie die API benutzt wird

Die Links aus der Aufgabe (`/api/v1/...`) gehen nicht mehr (404). Laut Doku nimmt man jetzt
`GET https://mc-api.io/profile/<name>/JAVA`. Da ist alles in einer Antwort drin: UUID, Name, Skin und `names` für die Namenshistorie.

- Requests laufen async mit `HttpClient.sendAsync` und `CompletableFuture`, also nie im Main Thread
- Ausgabe kommt danach über den Scheduler zurück auf den Main Thread
- Fehler wie 404, 429 oder Timeout werden abgefangen und es gibt eine passende Nachricht
- Bei 429 und 5xx wird nochmal versucht, Profile werden ein paar Minuten gecached
- Alle Nachrichten gehen über `MessageUtil`. Da ist `sendClickable` dazugekommen, damit man die UUID kopieren und Links anklicken kann

Die Namenshistorie ist meistens leer, weil Mojang die 2022 abgeschaltet hat. Dann wird nur der aktuelle Name angezeigt.

## Beispiel

```
NITROMC » UUID: 069a79f4-44e9-4726-a5be-fca90e38aaf5
NITROMC » Name: Notch
NITROMC » Skin: https://textures.minecraft.net/texture/292009a4925b58f02c77dadc3ecef07ea4c7472f64e0fdc32ce5522489362680
NITROMC » Profil: https://namemc.com/profile/069a79f4-44e9-4726-a5be-fca90e38aaf5
NITROMC » Abgerufen: 11.10.2026 17:11
```

Wenn es den Spieler nicht gibt: `NITROMC » Es wurde kein Spieler mit dem Namen xyz gefunden.`

## Tests

`./mvnw test` führt die Unit Tests aus (Parser, Cache, Service gegen einen Fake-Server und die Skin-Anzeige).
