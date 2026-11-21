# Store listing (en, de, fr, es)

Assets:
- **Icons**
  - `assets/brand/app-icon-1024.png` (App Store)
  - `assets/brand/play-icon-512.png` (Play)
- **Screenshots:** `assets/store/screenshots/<format>/<lang>/01..05.png`
  - `iphone-6.9`: 1320×2868
  - `android-phone`: 1080×1920
- **Play feature graphic:** `assets/store/feature/feature-graphic-1024x500-<lang>.png`
- **Texts as files:** `assets/store/metadata/appstore/<locale>/` and `assets/store/metadata/play/<locale>/`, one `.txt` per field, generated from the texts below
- **Upload folders:** `assets/store/upload/appstore/<locale>/` and `assets/store/upload/play/<locale>/` hold everything for one store and language: texts, screenshots, and for Play the feature graphic and icon

## Regenerate

1. Render raw frames from the real Compose UI:

```bash
STORE_SHOTS=/tmp/raw LANGS=en,de,fr,es ./gradlew :shared:desktopTest --tests '*StoreShots*'
```

2. Build the screenshots and feature graphics. Captions are in `CAPTIONS` in `tools/store/store_assets.py`.

```bash
python3 tools/store/store_assets.py /tmp/raw assets/store assets/brand/icon-plant-art.png
```

3. Text files and upload folders. The script checks the store length limits and fails if a text is too long.

```bash
python3 tools/store/store_metadata.py
```

4. App icon (iOS and Android). The input is a plant rendered at 1 px per art pixel.

```bash
python3 tools/generate_icon.py assets/brand/icon-plant-art.png /tmp/icons
```

Copy `AppIcon.png` and `res/` from `/tmp/icons` into the app.

## Texts

### English
- **Name:** Plantagotchi
- **Subtitle (App Store, 30):** Your cozy pixel plant pal
- **Short description (Play, 80):** Grow, water and cuddle pixel plants that react to the real weather.
- **Promotional text (App Store):** New: string of hearts, a redrawn flytrap, grow lamps that swing, and every species now grows at its own pace.
- **What's new:** A second hanging plant, the string of hearts. Every species grows at its own pace, from a sprout in a day to a bonsai in a week. Light now also fills while the app is closed. Friendly greetings from your plants, at most one a day.
- **Keywords (App Store):** plant,tamagotchi,pixel,cozy,garden,virtual pet,houseplant,idle,cute,weather
- **Description:**

Plantagotchi is a small, cozy plant to look after, drawn in pixel art.

Water it, feed it and give it some love, and it grows from a tiny sprout into a full plant. Each species has its own needs. Cacti hate frost, ferns like shade, and sunflowers follow the sun. Every plant grows a little differently: no two bonsai are alike, and tulips bloom in random colors.

- Ten species: sprout, cactus, fern, sunflower, tulips, monstera, bonsai, flytrap, pothos and string of hearts
- Real weather where you live: rain, snow, heat and frost affect your plants
- Cozy scenes: a room with a cat, a fireside, a plant shop, beach, mountains and more
- Move your plants around the room, treat them to a grow lamp, and pick pretty pots
- A daily chest with coins, and reminders when a plant gets thirsty
- Home screen widgets
- No ads, no in-app purchases, no account

### Deutsch
- **Name:** Plantagotchi
- **Untertitel (30):** Deine gemütliche Pixelpflanze
- **Kurzbeschreibung (80):** Zieh Pixelpflanzen gross, giess und streichle sie. Sie spüren das echte Wetter.
- **Werbetext:** Neu: Herzkette, neu gezeichnete Fliegenfalle, schwingende Pflanzenlampen, und jede Art wächst jetzt in ihrem eigenen Tempo.
- **Neuigkeiten:** Eine zweite Hängepflanze, die Herzkette. Jede Art wächst in ihrem eigenen Tempo, vom Spross in einem Tag bis zum Bonsai in einer Woche. Licht füllt sich jetzt auch bei geschlossener App. Freundliche Grüsse von deinen Pflanzen, höchstens einer pro Tag.
- **Keywords:** pflanze,tamagotchi,pixel,gemütlich,garten,haustier,zimmerpflanze,süss,wetter
- **Beschreibung:**

Plantagotchi ist eine kleine, gemütliche Pflanze in Pixel-Art, um die du dich kümmerst.

Giess sie, dünge sie und schenk ihr etwas Liebe. So wächst sie vom winzigen Spross zur prächtigen Pflanze. Jede Art hat ihre eigenen Bedürfnisse: Kakteen hassen Frost, Farne mögen Schatten, Sonnenblumen folgen der Sonne. Keine Pflanze wächst wie die andere. Jeder Bonsai ist einzigartig, Tulpen blühen in zufälligen Farben.

- Zehn Arten: Spross, Kaktus, Farn, Sonnenblume, Tulpen, Monstera, Bonsai, Venusfliegenfalle, Efeutute und Herzkette
- Echtes Wetter bei dir: Regen, Schnee, Hitze und Frost wirken auf deine Pflanzen
- Gemütliche Szenen: Zimmer mit Katze, Kamin, Pflanzenladen, Strand, Berge und mehr
- Pflanzen im Zimmer verschieben, Pflanzenlampe und schöne Töpfe
- Tägliche Truhe mit Münzen, Erinnerung wenn eine Pflanze Durst hat
- Widgets für den Homescreen
- Keine Werbung, keine In-App-Käufe, kein Konto

### Français
- **Nom :** Plantagotchi
- **Sous-titre (30) :** Ta plante en pixels douillette
- **Description courte (80) :** Fais pousser, arrose et câline des plantes en pixels qui vivent la vraie météo.
- **Texte promotionnel :** Nouveau : chaîne des cœurs, dionée redessinée, lampes horticoles qui se balancent, et chaque espèce pousse à son rythme.
- **Nouveautés :** Une deuxième plante suspendue, la chaîne des cœurs. Chaque espèce pousse à son rythme, d’une pousse en un jour à un bonsaï en une semaine. La lumière se remplit aussi quand l’app est fermée. De petits bonjours de tes plantes, un par jour au maximum.
- **Mots-clés :** plante,tamagotchi,pixel,cosy,jardin,animal virtuel,plante verte,mignon,météo
- **Description :**

Plantagotchi est une petite plante douillette en pixel art dont tu prends soin.

Arrose-la, nourris-la, donne-lui de l'amour, et elle passe d'une minuscule pousse à une belle plante. Chaque espèce a ses besoins : les cactus détestent le gel, les fougères aiment l'ombre, les tournesols suivent le soleil. Aucune plante ne pousse pareil : chaque bonsaï est unique, les tulipes fleurissent en couleurs aléatoires.

- Dix espèces : pousse, cactus, fougère, tournesol, tulipes, monstera, bonsaï, dionée, pothos et chaîne des cœurs
- La vraie météo de chez toi : pluie, neige, chaleur et gel agissent sur tes plantes
- Scènes douillettes : chambre avec un chat, coin du feu, boutique de plantes, plage, montagnes et plus
- Déplace tes plantes dans la pièce, offre-leur une lampe horticole et de jolis pots
- Coffre quotidien avec des pièces, rappels quand une plante a soif
- Widgets pour l'écran d'accueil
- Sans pub, sans achats intégrés, sans compte

### Español
- **Nombre:** Plantagotchi
- **Subtítulo (30):** Tu acogedora planta pixelada
- **Descripción breve (80):** Cultiva, riega y mima plantas pixeladas que sienten el tiempo real.
- **Texto promocional:** Novedad: collar de corazones, atrapamoscas redibujada, lámparas de cultivo que se balancean, y cada especie crece a su ritmo.
- **Novedades:** Una segunda planta colgante, el collar de corazones. Cada especie crece a su ritmo, de un brote en un día a un bonsái en una semana. La luz también se llena con la app cerrada. Saludos de tus plantas, uno al día como máximo.
- **Palabras clave:** planta,tamagotchi,pixel,acogedor,jardín,mascota virtual,planta de interior,tierno,tiempo
- **Descripción:**

Plantagotchi es una pequeña y acogedora planta en pixel art que tú cuidas.

Riégala, abónala y dale cariño, y crecerá de un brote diminuto a una planta preciosa. Cada especie tiene sus necesidades: los cactus odian las heladas, los helechos prefieren la sombra y los girasoles siguen al sol. Ninguna planta crece igual: cada bonsái es único y los tulipanes florecen en colores al azar.

- Diez especies: brote, cactus, helecho, girasol, tulipanes, monstera, bonsái, atrapamoscas, pothos y collar de corazones
- El tiempo real de tu zona: la lluvia, la nieve, el calor y las heladas afectan a tus plantas
- Escenas acogedoras: habitación con gato, junto al fuego, tienda de plantas, playa, montañas y más
- Mueve tus plantas por la habitación, dales una lámpara de cultivo y macetas bonitas
- Cofre diario con monedas y avisos cuando una planta tiene sed
- Widgets para la pantalla de inicio
- Sin anuncios, sin compras integradas, sin cuenta
