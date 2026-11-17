# Google Play (interner Test)

`.github/workflows/play-store.yml` baut ein signiertes Android App Bundle auf einem Linux-Runner von
GitHub und lädt es in den Track **Interner Test** hoch. Start: Actions → Play Store → Run workflow,
oder einen Tag `android-v*` pushen.

- `versionCode` = Minuten seit 14.11.2023 (`.github/scripts/build-number.sh`, gemeinsam für alle
  Release-Workflows, steigt also immer über alle Tracks), plus optional `version_code_offset`
- `versionName` = `MARKETING_VERSION` aus `iosApp/Configuration/Config.xcconfig` (gemeinsam mit iOS)

## Einmalige Einrichtung

Alles geht im Browser plus einem Terminal mit JDK (für `keytool`). Android Studio ist nicht nötig.

### 1. Upload-Keystore

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Plantagotchi, O=Mothlight Interactive, C=CH"
```

Passwort wählen. `upload.jks` und Passwort sicher aufbewahren (Passwortmanager). Mit Play App Signing
(Schritt 3) hält Google den eigentlichen Signaturschlüssel, das hier ist nur der *Upload*-Schlüssel
und lässt sich bei Verlust über den Play-Support zurücksetzen.

### 2. App in der Play Console

[Play Console](https://play.google.com/console) → **App erstellen** → Name `Plantagotchi`, App,
kostenlos. Der Paketname wird beim ersten Upload festgelegt: `dev.mothlight.plantagotchi`.

### 3. Erster Upload (einmal von Hand)

Google akzeptiert Uploads über die API erst, nachdem ein Bundle von Hand hochgeladen wurde.

1. Die Signing-Secrets aus Schritt 5 anlegen (alle ausser `PLAY_SERVICE_ACCOUNT_JSON`).
2. Actions → **Play Store** → Run workflow mit **upload ausgeschaltet**. Das AAB aus den Artefakten
   des Laufs herunterladen (`android-release-<n>`).
3. Play Console → Testen → **Interner Test** → Neuen Release erstellen → **Play App Signing**
   akzeptieren → AAB hochladen → speichern und veröffentlichen.

### 4. Dienstkonto für die API

1. [Google Cloud Console](https://console.cloud.google.com) → Projekt anlegen oder wählen →
   APIs & Dienste → **Google Play Android Developer API** aktivieren.
2. IAM & Verwaltung → Dienstkonten → **Dienstkonto erstellen** (keine Rollen nötig) → Schlüssel →
   Schlüssel hinzufügen → JSON → herunterladen. Ein bestehendes Dienstkonto (z. B. von mikrodb) geht
   auch, dann nur Schritt 3 hier.
3. Play Console → **Nutzer und Berechtigungen** → Neue Nutzer einladen → E-Mail des Dienstkontos →
   App-Berechtigungen: Plantagotchi → *Apps in Test-Tracks veröffentlichen* (und *App-Informationen
   ansehen*), für Produktions-Releases zusätzlich *Apps in der Produktion veröffentlichen*. Es kann
   einige Minuten dauern, bis die Berechtigung aktiv ist.

### 5. GitHub-Secrets

Repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Wert |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `ANDROID_KEY_ALIAS` | `upload` (der `-alias` aus Schritt 1) |
| `ANDROID_KEY_PASSWORD` | Schlüssel-Passwort (gleich wie Keystore-Passwort, wenn keins gesetzt) |
| `PLAY_SERVICE_ACCOUNT_JSON` | kompletter Inhalt der JSON-Datei des Dienstkontos |

### 6. Tester

Play Console → Testen → Interner Test → **Tester** → E-Mail-Liste anlegen (bis 100) → Opt-in-Link
kopieren, auf dem Handy mit diesem Google-Konto öffnen, dann aus Play installieren.

## Testbuild hochladen

1. Actions → **Play Store** → Run workflow (oder `git tag android-v1.0.1 && git push origin android-v1.0.1`).
2. Wenige Minuten später ist der Build im internen Track und Tester bekommen das Update über Play.

**Status:** Solange die App selbst in der Play Console noch ein Entwurf ist (Store-Eintrag,
Einstufung usw. unvollständig), akzeptiert Play nur Releases mit Status `draft`. Dann den Workflow
mit Status `draft` starten und den Release in der Play Console von Hand veröffentlichen. Sobald die
App eingerichtet ist, den Standard `completed` verwenden.

Meldet Play einen `versionCode` als bereits verwendet (z. B. nach manuellen Uploads), mit einem
höheren `version_code_offset` starten.
