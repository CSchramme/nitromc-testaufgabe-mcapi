---

# NitroMC – Developer Bewerbungsaufgabe

## Aufgabe: Minecraft API Integration Plugin

Entwickle ein Plugin für Paper, das die API von [mc-api.io](https://www.mc-api.io/docs) verwendet, um Spielerinformationen abzurufen und im Spiel darzustellen.

---

# Ziel

Das Plugin soll externe REST-APIs sauber integrieren, HTTP-Requests asynchron verarbeiten und die erhaltenen JSON-Daten strukturiert im Spiel ausgeben.

---

# Technische Anforderungen

* Java 21
* Paper API 1.20+
* Maven oder Gradle
* HTTP-Requests ausschließlich asynchron
* Keine API-Abfragen im Main Thread
* Saubere Fehlerbehandlung
* Strukturierte Service- und Model-Klassen
* JSON-Verarbeitung mit Gson
* Alle Nachrichten müssen über `MessageUtil` ausgegeben werden

---

# Verwendete API

Dokumentation:

[mc-api.io Dokumentation](https://www.mc-api.io/)

Beispiel-Endpoints:

```text
https://mc-api.io/api/v1/profile/<spielername>
```

```text
https://mc-api.io/api/v1/name-history/<uuid>
```

---

# Befehle

## `/mcinfo <spielername>`

Zeigt folgende Informationen an:

* UUID
* aktueller Spielername
* Skin-URL
* Profil-URL
* Zeitpunkt der Abfrage
* Fehlernachricht bei ungültigem Spieler oder API-Fehler

### Beispielausgabe

```text
UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
Name: Notch
Skin: https://textures.minecraft.net/...
Profil: https://namemc.com/profile/...
Abgerufen: 16.05.2026 18:30
```

---

## `/namehistory <spielername>`

Zeigt bekannte Namenshistorien eines Spielers an.

### Beispielausgabe

```text
1. LukasDev
2. TheLukasDev
3. LukasDev_
```

---

# Permissions

```text
nitromc.command.mcinfo
nitromc.command.namehistory
```

---

# plugin.yml

```yaml
name: NitroApiPlugin
version: 1.0.0
main: net.nitromc.api.NitroApiPlugin
api-version: '1.20'

commands:
  mcinfo:
    description: Zeigt Spielerinformationen an
    usage: /mcinfo <spielername>
    permission: nitromc.command.mcinfo

  namehistory:
    description: Zeigt die Namenshistorie eines Spielers an
    usage: /namehistory <spielername>
    permission: nitromc.command.namehistory

permissions:
  nitromc.command.mcinfo:
    default: op

  nitromc.command.namehistory:
    default: op
```

---

# Erwartete Projektstruktur

```text
src/main/java/net/nitromc/api/
├── NitroApiPlugin.java
├── command/
│   ├── McInfoCommand.java
│   └── NameHistoryCommand.java
├── service/
│   └── McApiService.java
├── model/
│   ├── PlayerProfile.java
│   └── NameHistoryEntry.java
└── util/
    └── MessageUtil.java
```

---

# Anforderungen an die Service-Klasse

Die API-Kommunikation soll vollständig gekapselt werden.

Beispiele:

* Spielerprofil abrufen
* UUID ermitteln
* Namenshistorie laden
* HTTP-Fehler behandeln
* Timeouts setzen
* JSON-Daten in Models umwandeln
* `CompletableFuture` für Async-Verarbeitung verwenden

---

# Vorgaben für Async-Handling

Folgendes ist verpflichtend:

* Keine blockierenden HTTP-Requests im Server-Thread
* Verwendung von:

  * `CompletableFuture`
  * Bukkit Async Scheduler
  * oder Java HttpClient async APIs

Beispiel:

```java
HttpClient.newHttpClient()
    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
```

---

# MessageUtil

Die bereitgestellte Klasse `MessageUtil` muss für sämtliche Nachrichten verwendet werden.

Erlaubt:

```java
MessageUtil.send(sender, "&aErfolgreich geladen!");
```

Nicht erlaubt:

```java
sender.sendMessage(...);
```

---

# Empfohlene Dependencies

## Maven

```xml
<dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.20.4-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>

<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

---

# Optionale Erweiterungen

* Caching-System
* Config-Datei
* klickbare Chat-Komponenten
* Retry-System bei API-Fehlern
* Logging
* Unit-Tests
* Rate-Limit Handling

---

# Abgabe

Das Repository muss enthalten:

* vollständigen Source Code
* `README.md`
* `plugin.yml`
* `pom.xml` oder `build.gradle`
* Installationsanleitung
* kurze Erklärung der API-Nutzung
* Beispielausgaben oder Screenshots

---

# Bewertungskriterien

Bewertet werden:

* API-Verständnis
* Async-Handling
* Fehlerbehandlung
* Strukturierung
* Clean Code
* Lesbarkeit
* korrekte Nutzung von `MessageUtil`
* sinnvolle Objektmodellierung
* saubere Trennung von Command-, Service- und Model-Schicht
