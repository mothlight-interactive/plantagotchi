# Release (Produktion)

Ein Tag wie `v1.0.1` auf `master` veröffentlicht die App in beiden Stores:

1. **verify**: Der Tag muss auf einen Commit von `master` zeigen. Die Version kommt aus dem Tag
   (`1.0.1`), die Build-Nummer ist zeitbasiert (`.github/scripts/build-number.sh`).
2. **tests**: der `CI`-Workflow (Android-Debug- und Release-Build mit R8, iOS-Kompilierung, JVM- und UI-Tests).
3. **approve**: wartet im Environment `production`, bis du in Actions auf *Approve* klickst
   (eine Freigabe für beide Plattformen).
4. **ios** (`testflight.yml`): Build, Upload, dann legt fastlane die App-Store-Version `1.0.1` an,
   hängt den Build an, setzt *Neuerungen* (DE/EN) und **reicht sie zur Prüfung ein**. Nach der
   Freigabe durch Apple geht sie automatisch live.
5. **android** (`play-store.yml`): AAB mit `versionName 1.0.1`, hochgeladen in den
   **Produktions**-Track als **gestaffelter Rollout mit 20 %** inklusive *Neuerungen*
   (max. 500 Zeichen pro Sprache).

*Neuerungen* listet die seit dem letzten `v*`-Tag in `master` gemergten PRs (ohne
Dependency-Updates), siehe `.github/scripts/store-notes.sh`.

## Release auslösen

```bash
git switch master && git pull
git tag v1.0.1
git push origin v1.0.1
```

Dann in Actions → *Release (production)* → *Review deployments* → `production` freigeben.

Alternativ ein GitHub-Release mit neuem Tag `v1.0.1` auf `master` erstellen (Releases → *Draft a new
release*). Das pusht den Tag (startet den Release oben), und nach dem Veröffentlichen füllt der
Workflow *Release notes* die Beschreibung mit den Änderungen seit dem letzten Tag. Eigener Text
bleibt erhalten, der generierte Teil steht unter einer `<!-- changes:auto -->`-Markierung.

### Android-Rollout erhöhen

Actions → *Play rollout* → *Run workflow*: `version_code` (aus der Zusammenfassung des Release-Laufs
oder der Play Console) und `fraction` (`0.5` = 50 %, `1` = alle). Braucht dieselbe Freigabe in
`production`.

## Testbuilds

- **TestFlight**: Actions → *TestFlight* → Run workflow, oder einen Tag `ios-v1.0.1` pushen.
  Details: [testflight.md](testflight.md)
- **Google Play intern**: Actions → *Play Store* → Run workflow, oder einen Tag `android-v1.0.1`
  pushen. Details: [play-store.md](play-store.md)

**Nie einen Testbuild von Hand in App Store Connect oder der Play Console in die Produktion
befördern**, sondern immer über `release.yml` releasen.

Testbuilds laufen nie automatisch bei einem Push auf `master`, sie brechen also nicht, solange die
Secrets fehlen.

## Einmalige Einrichtung

1. **Environment mit Freigabe**: Settings → Environments → *New environment* `production` →
   *Required reviewers*: du selbst. Unter *Deployment branches and tags* *Selected* wählen und das
   Tag-Muster `v*` hinzufügen. **Ohne Required reviewers läuft der Release ohne Nachfrage.**
2. **Secrets**: die aus [testflight.md](testflight.md) und [play-store.md](play-store.md) (der
   Release-Workflow reicht sie weiter). Weitere Secrets gibt es nicht: die App hat keine Werbung
   und keine echten Käufe (der Shop nutzt nur Spielmünzen).
3. **App Store Connect, nur beim ersten Release**: Die App-Store-Seite braucht einmal ihre
   Metadaten (Name, Beschreibung, Keywords, Screenshots, App-Datenschutz, Altersfreigabe, Preis,
   Kontakt für die Prüfung). App-Datenschutz siehe [Datenschutz-Angaben](#datenschutz-angaben-in-den-stores).
   Der Workflow aktualisiert nur *Neuerungen*.
4. **Google Play, erster Produktions-Release**:
   - Store-Eintrag, Einstufung, Datensicherheit (siehe [Datenschutz-Angaben](#datenschutz-angaben-in-den-stores))
     und Zielgruppe müssen vollständig sein;
   - neue *private* Entwicklerkonten brauchen zuerst einen **geschlossenen Test mit mindestens
     12 Testern über 14 Tage**, bevor Google den Produktionszugang freigibt;
   - das allererste AAB muss von Hand hochgeladen werden (siehe [play-store.md](play-store.md)).

## Release-Build (Android)

`release` läuft mit R8 (`isMinifyEnabled`, `isShrinkResources`), Regeln in
`androidApp/proguard-rules.pro` (kotlinx.serialization, Ktor/OkHttp, Glance, WorkManager). Der
`CI`-Workflow baut bei jedem PR auch `:androidApp:assembleRelease`, fehlende Keep-Regeln fallen also
vor dem Release auf. Ohne `ANDROID_KEYSTORE_FILE` entsteht ein unsignierter Build
(`androidApp-release-unsigned.apk`), signiert wird nur in `play-store.yml`. Die R8-Mapping-Datei
steckt im AAB (`BUNDLE-METADATA`), Play deobfuskiert Absturzberichte damit selbst; lokal liegt sie
unter `androidApp/build/outputs/mapping/release/mapping.txt`.

## Datenschutz-Angaben in den Stores

Grundlage für App Store Connect (*App-Datenschutz*) und Play Console (*Datensicherheit*). Bei jeder
Änderung an Netzwerk, Berechtigungen oder SDKs prüfen und hier nachführen, ebenso
`iosApp/iosApp/PrivacyInfo.xcprivacy` und `iosApp/PlantagotchiWidget/PrivacyInfo.xcprivacy`.

### Was die App mit Daten macht

- **Spielstand und Einstellungen** liegen nur auf dem Gerät (iOS `NSUserDefaults`, Android
  `SharedPreferences`). Das Widget liest einen Auszug über die App Group
  `group.dev.mothlight.plantagotchi` (iOS) bzw. den Glance-Zustand (Android). Kein Konto, kein Server.
- **Wetter**: Die App fragt den *ungefähren* Standort ab (iOS `kCLLocationAccuracyKilometer`, Android
  nur `ACCESS_COARSE_LOCATION`) und schickt Breite/Länge per HTTPS an `api.open-meteo.com`, um das
  aktuelle Wetter zu laden. Ohne Erlaubnis nutzt sie einen festen Standardort. Die letzte Position
  liegt lokal im Spielstand, an uns geht nichts.
- **Kamera** (nur iOS, optional, standardmässig aus): Belichtungswerte der
  Rückkamera dienen als Lichtmesser. Keine Bilder werden gespeichert oder übertragen.
- **Lichtsensor** (Android): wird nur lokal gelesen.
- **Benachrichtigungen**: nur lokal geplant (WorkManager bzw. `UNUserNotificationCenter`), kein Push-Server.
- **Keine** Werbung, Analyse, Absturzberichte über Drittanbieter, Konten, Tracking oder Käufe mit
  echtem Geld (der Shop nutzt nur Spielmünzen). Kein Logging im Release.
- Android-Auto-Backup ist an (`allowBackup`): der Spielstand landet im Google-Backup des Nutzers. Das
  zählt laut Google nicht als Datenerhebung durch den Entwickler.

### App Store Connect → App-Datenschutz

- *Erfassen Sie oder Ihre Drittanbieter Daten aus dieser App?* **Nein, wir erfassen keine Daten**
  (Label "Keine Daten erfasst").
  - Begründung: Apple zählt Daten nur als erfasst, wenn sie das Gerät so verlassen, dass Entwickler
    oder Partner länger darauf zugreifen können, als für die Beantwortung der Anfrage in Echtzeit nötig.
    Die Wetteranfrage an Open-Meteo wird in Echtzeit beantwortet, ohne Konto und ohne Kennung.
  - **Entscheidung vor dem ersten Release**: die Datenschutzerklärung von Open-Meteo prüfen. Falls
    Open-Meteo Koordinaten zusammen mit der IP-Adresse länger speichert, stattdessen angeben:
    *Standort → Grober Standort*, Zweck *App-Funktionalität*, **nicht** mit der Identität verknüpft,
    **nicht** für Tracking verwendet, und `NSPrivacyCollectedDataTypes` im Manifest entsprechend ergänzen.
- Tracking: **Nein** (`NSPrivacyTracking = false`, keine Tracking-Domains).
- Datenschutzrichtlinie-URL (Pflicht): `https://mothlight.dev/privacy`, muss Plantagotchi abdecken
  (Standort an Open-Meteo, Kamera als Lichtmesser, lokale Speicherung).
- Privacy Manifest (`PrivacyInfo.xcprivacy`, App und Widget, im Xcode-Projekt unter *Copy Bundle Resources*):

  | API-Kategorie | Grund | Wofür |
  |---|---|---|
  | UserDefaults | `CA92.1` | Einstellungen und Spielstand (multiplatform-settings, Standard-Defaults), nur App |
  | UserDefaults | `1C8F.1` | Widget-Snapshot in der App Group, App und Widget |
  | File Timestamp | `C617.1` | Skia (Compose-Rendering, im `Shared`-Framework) ruft `stat`/`fstat` auf |
  | System Boot Time | `35F9.1` | Skia misst Zeitabstände mit `mach_absolute_time` |

  Disk Space und Active Keyboards werden nicht genutzt. Nach einem Update von Compose Multiplatform
  bzw. Skiko die Symbole erneut prüfen, z. B. `llvm-nm -u` auf die `.a`-Dateien im Skiko-iOS-Klib.

### Google Play → Datensicherheit

Konservativ beantwortet, weil der ungefähre Standort das Gerät verlässt (Anfrage an Open-Meteo):

- *Erhebt oder teilt Ihre App erforderliche Nutzerdatentypen?* **Ja**
- *Werden alle erhobenen Nutzerdaten bei der Übertragung verschlüsselt?* **Ja** (HTTPS)
- *Bieten Sie eine Möglichkeit, das Löschen der Daten anzufordern?* **Nein** (nichts wird auf einem
  Server gespeichert; Deinstallieren löscht alles)
- Konto-Erstellung: **Die App bietet keine Konten an**
- Datentypen: nur **Standort → Ungefährer Standort**
  - Erhoben: **Ja**, geteilt: **Nein** (Open-Meteo verarbeitet die Anfrage nur, um sie zu
    beantworten, als Dienstleister)
  - *Werden diese Daten sitzungsbezogen (ephemer) verarbeitet?* **Ja**
  - *Erforderlich oder optional?* **Optional** (ohne Erlaubnis gilt ein Standardort)
  - Zweck: **App-Funktionen** (Wetter in der Szene)
- Alle anderen Datentypen (persönliche Infos, Fotos/Videos, App-Aktivität, Gerätekennungen usw.):
  **nicht erhoben**. Den Kamera-Lichtmesser gibt es nur auf iOS, die Werte bleiben auf dem Gerät.
- Werbung: **Nein**, Werbe-ID: **Nein** (keine `AD_ID`-Berechtigung).

### Berechtigungen und Texte

iOS (`iosApp/iosApp/Info.plist`):

| Schlüssel | Text |
|---|---|
| `NSLocationWhenInUseUsageDescription` | Plantagotchi uses your location to let your plant feel the local weather. |
| `NSCameraUsageDescription` | Plantagotchi can use the camera as a light meter, so your plant knows how bright it is. No images are stored. |

Benachrichtigungen fragt iOS zur Laufzeit ab (kein Info.plist-Text nötig). Die Texte sind nur
englisch; für deutsche Texte fehlt noch ein `de.lproj/InfoPlist.strings`.

Android (`androidApp/src/main/AndroidManifest.xml` plus Bibliotheken, geprüft mit
`aapt2 dump permissions` auf dem Release-APK):

| Berechtigung | Wofür |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Wetter von Open-Meteo |
| `ACCESS_COARSE_LOCATION` | ungefährer Standort für das Wetter, zur Laufzeit abgefragt |
| `POST_NOTIFICATIONS` | Erinnerungen (ab Android 13 zur Laufzeit abgefragt) |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | von WorkManager (Erinnerungen überleben einen Neustart) |

`ACCESS_FINE_LOCATION` wurde entfernt: Für das Wetter reicht der ungefähre Standort
(`PRIORITY_BALANCED_POWER_ACCURACY`, Open-Meteo rechnet ohnehin auf einem Raster von einigen km).

## Version erhöhen

Für eine neue Version `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig` erhöhen. Der Wert gilt
für iOS und Android. Bei einem `v*`-Tag gewinnt die Version aus dem Tag.

## Hinweise

- Releases laufen pro Tag nacheinander. Ein fehlgeschlagener Job kann mit derselben Version und
  Build-Nummer neu gestartet werden.
- Abgelehnt von Apple: beheben, `v1.0.2` taggen, erneut releasen (`reject_if_possible` ersetzt eine
  Version, die noch auf die Prüfung wartet).

## Testmodus

Testbuilds (TestFlight, Play intern) sind Umgebung `test`: Badge oben in der Mitte, Tippen öffnet den
Testmodus (Zeit, Wetter, Ort, Pflanzen, Münzen, Freischaltungen, Animationen, Sounds). Store-Releases über
`release.yml` sind `prod` ohne Testmodus. **Nie einen Testbuild von Hand in die Produktion befördern.**
