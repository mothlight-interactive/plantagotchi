# TestFlight ohne Mac

`.github/workflows/testflight.yml` baut die iOS-App auf einem macOS-Runner von GitHub, signiert sie
und lädt sie zu TestFlight hoch. Start: Actions → TestFlight → Run workflow, oder einen Tag `ios-v*`
pushen. Die Build-Nummer ist zeitbasiert (`.github/scripts/build-number.sh`, gemeinsam mit Android),
die Version (`MARKETING_VERSION`) steht in `iosApp/Configuration/Config.xcconfig`. Produktions-Releases:
siehe [release.md](release.md).

## Einmalige Einrichtung

Alles geht im Browser plus einem Terminal mit `openssl` (Linux, macOS, WSL oder Git Bash).

### 1. Distribution-Zertifikat (developer.apple.com)

Schlüssel und Signing-Request lokal erzeugen:

```bash
openssl genrsa -out dist.key 2048
openssl req -new -key dist.key -out dist.csr -subj "/emailAddress=contact@mothlight.dev/CN=Mothlight Distribution/C=CH"
```

[Certificates](https://developer.apple.com/account/resources/certificates/list) → **+** → **Apple Distribution**
→ `dist.csr` hochladen → `distribution.cer` herunterladen. Dann die `.p12` bauen (Passwort wählen):

```bash
openssl x509 -inform DER -in distribution.cer -out dist.pem
openssl pkcs12 -export -inkey dist.key -in dist.pem -out dist.p12 \
  -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1 -passout pass:PASSWORT_WAEHLEN
```

(Die SHA1/3DES-Optionen sind nötig, weil der macOS-Schlüsselbund das Standardformat von OpenSSL 3
nicht lesen kann.) Hast du für Mothlight schon ein Apple-Distribution-Zertifikat (z. B. aus
mikrodb), kannst du dessen `.p12` wiederverwenden und direkt zu Schritt 2.

### 2. App Group, App-IDs und Provisioning-Profile (developer.apple.com)

Die App hat ein Widget (eigenes Extension-Target). App und Widget teilen sich die Daten über eine App Group.

- [Identifiers](https://developer.apple.com/account/resources/identifiers/list) → **+** → **App Groups** →
  `group.dev.mothlight.plantagotchi`.
- Identifiers → **+** → App IDs → App → Bundle ID explizit `dev.mothlight.plantagotchi`, Capability
  **App Groups** aktivieren und die Gruppe zuweisen (Push-Benachrichtigungen sind lokal und brauchen keine Capability).
- Dasselbe für das Widget: Bundle ID `dev.mothlight.plantagotchi.widget`, **App Groups** mit derselben Gruppe.
- [Profiles](https://developer.apple.com/account/resources/profiles/list) → **+** → **App Store Connect**
  (Distribution) → App ID `dev.mothlight.plantagotchi` → das Zertifikat aus Schritt 1 → Name z. B.
  `Plantagotchi AppStore` → `.mobileprovision` herunterladen.
- Ein zweites Profil genauso für `dev.mothlight.plantagotchi.widget`, z. B. `Plantagotchi Widget AppStore`.

Ändern sich die Capabilities einer App-ID, das Profil neu erzeugen (Edit → Save) und das Secret ersetzen.
Der Workflow prüft Bundle-ID und App Group beider Profile und bricht mit einer klaren Meldung ab.

### 3. App in App Store Connect

[App Store Connect](https://appstoreconnect.apple.com/apps) → **+** → Neue App → iOS, Name `Plantagotchi`,
Bundle-ID `dev.mothlight.plantagotchi`, beliebige SKU (z. B. `plantagotchi`). 

### 4. API-Key (App Store Connect)

Benutzer und Zugriff → **Integrationen** → App Store Connect API → Team-Schlüssel → **+**, Zugriff
**App-Manager**. Die `.p8` herunterladen (geht nur einmal) und **Key ID** sowie **Issuer ID** (über
der Liste) notieren. Ein bestehender Team-Key (z. B. von mikrodb) funktioniert auch.

### 5. GitHub-Secrets

Repo → Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Wert |
|---|---|
| `APPLE_TEAM_ID` | Team-ID aus developer.apple.com → Account → Membership details |
| `IOS_DIST_CERT_P12_BASE64` | `base64 -w0 dist.p12` (macOS: `base64 -i dist.p12`) |
| `IOS_DIST_CERT_PASSWORD` | das `.p12`-Passwort |
| `IOS_PROVISIONING_PROFILE_BASE64` | `base64 -w0 Plantagotchi_AppStore.mobileprovision` |
| `IOS_WIDGET_PROVISIONING_PROFILE_BASE64` | `base64 -w0 Plantagotchi_Widget_AppStore.mobileprovision` |
| `ASC_KEY_ID` | Key ID aus Schritt 4 |
| `ASC_ISSUER_ID` | Issuer ID aus Schritt 4 |
| `ASC_PRIVATE_KEY` | kompletter Inhalt der `.p8`-Datei, inklusive BEGIN/END-Zeilen |

`dist.key` / `dist.p12` sicher aufbewahren (Passwortmanager), dann die lokalen Kopien löschen.

## Testbuild hochladen

1. Actions → **TestFlight** → Run workflow (erster Lauf ca. 25 Min., weil Kotlin/Native
   heruntergeladen wird, danach ca. 10 bis 15 Min.).
2. Nach der Verarbeitung (5 bis 15 Min.) erscheint der Build in App Store Connect → TestFlight.
3. Dich einmal unter **Interne Tests** hinzufügen, dann über die TestFlight-App installieren.

## Hinweise

- **Einen TestFlight-Build nie von Hand in App Store Connect einer App-Store-Version zuordnen.**
  Store-Releases immer über `release.yml` ([release.md](release.md)).
- Privates Repo: macOS-Minuten zählen 10-fach gegen das Actions-Kontingent (Free: 2000 Min. ≈ 200
  macOS-Min. pro Monat).
- Das Zertifikat läuft nach einem Jahr ab: Schritte 1 bis 2 wiederholen und die vier
  Signing-Secrets ersetzen.
- Wird der Upload wegen eines veralteten SDKs abgelehnt, im Workflow einen neueren Runner festlegen
  (`runs-on: macos-26`).
