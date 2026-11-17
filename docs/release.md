# Release (Produktion)

Ein Tag wie `v1.0.1` auf `master` veröffentlicht die App in beiden Stores:

1. **verify**: Der Tag muss auf einen Commit von `master` zeigen. Die Version kommt aus dem Tag
   (`1.0.1`), die Build-Nummer ist zeitbasiert (`.github/scripts/build-number.sh`).
2. **tests**: der `CI`-Workflow (Android-Build, iOS-Kompilierung und JVM-Tests).
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
3. **Cloud-Speicher (Variablen, keine Secrets)**: Repo → Settings → Secrets and variables → Actions →
   Tab *Variables* → `SUPABASE_URL` und `SUPABASE_PUBLISHABLE_KEY` (Werte in `local.properties`).
   Der Publishable Key ist dafür gedacht, in Apps ausgeliefert zu werden. Ohne die Variablen
   speichern Store-Builds nur lokal, Freunde sind dann ausgeschaltet.
4. **App Store Connect, nur beim ersten Release**: Die App-Store-Seite braucht einmal ihre
   Metadaten (Name, Beschreibung, Keywords, Screenshots, App-Datenschutz, Altersfreigabe, Preis,
   Kontakt für die Prüfung), Standort, Kamera (optional) und anonyme Anmeldung für den Cloud-Speicher im App-Datenschutz angeben. Der Workflow
   aktualisiert nur *Neuerungen*.
5. **Google Play, erster Produktions-Release**:
   - Store-Eintrag, Einstufung, Datensicherheit und Zielgruppe müssen vollständig sein;
   - neue *private* Entwicklerkonten brauchen zuerst einen **geschlossenen Test mit mindestens
     12 Testern über 14 Tage**, bevor Google den Produktionszugang freigibt;
   - das allererste AAB muss von Hand hochgeladen werden (siehe [play-store.md](play-store.md)).

## Version erhöhen

Für eine neue Version `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig` erhöhen. Der Wert gilt
für iOS und Android. Bei einem `v*`-Tag gewinnt die Version aus dem Tag.

## Hinweise

- Releases laufen pro Tag nacheinander. Ein fehlgeschlagener Job kann mit derselben Version und
  Build-Nummer neu gestartet werden.
- Abgelehnt von Apple: beheben, `v1.0.2` taggen, erneut releasen (`reject_if_possible` ersetzt eine
  Version, die noch auf die Prüfung wartet).
