import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Data shared with the app

/// Mirrors `WidgetPlant` written by the shared Kotlin code into the app group.
struct WidgetPlant: Codable, Hashable {
    let id: String
    let name: String
    let species: String
    let stage: Int
    let dead: Bool
    let lux: Int
    let water: Int
    let love: Int
    let fertilizer: Int
}

/// Mirrors `WidgetSnapshot`: all plants, the one active in the app and the coins.
struct WidgetSnapshot: Codable {
    let plants: [WidgetPlant]
    let activeId: String
    let coins: Int
    let updatedAt: Int64

    static let appGroup = "group.dev.mothlight.plantagotchi"
    static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }

    static func load() -> WidgetSnapshot? {
        guard let json = defaults?.string(forKey: "widget_snapshot"),
              let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    var active: WidgetPlant? { plant(activeId) ?? plants.first }

    func plant(_ id: String?) -> WidgetPlant? { plants.first { $0.id == id } }

    /// The plant `steps` positions after `id`, wrapping around.
    func step(from id: String, by steps: Int) -> WidgetPlant? {
        guard !plants.isEmpty else { return nil }
        let index = plants.firstIndex { $0.id == id } ?? 0
        let count = plants.count
        return plants[((index + steps) % count + count) % count]
    }

    static let preview = WidgetSnapshot(
        plants: [
            WidgetPlant(id: "a", name: "Sprout", species: "SPROUT", stage: 1, dead: false, lux: 80, water: 45, love: 60, fertilizer: 90),
            WidgetPlant(id: "b", name: "Cactus", species: "CACTUS", stage: 2, dead: false, lux: 60, water: 90, love: 30, fertilizer: 70),
            WidgetPlant(id: "c", name: "Tulip", species: "TULIP", stage: 0, dead: false, lux: 95, water: 20, love: 75, fertilizer: 50),
        ],
        activeId: "a", coins: 120, updatedAt: 0
    )
}

/// Which plant a widget shows after the arrows were used, per configuration.
enum BrowseState {
    static func key(_ config: String) -> String { "widget.browse.\(config)" }
    static func get(_ config: String) -> String? { WidgetSnapshot.defaults?.string(forKey: key(config)) }
    static func set(_ config: String, _ plantId: String) { WidgetSnapshot.defaults?.set(plantId, forKey: key(config)) }
}

private var languageCode: String { Locale.current.language.languageCode?.identifier ?? "en" }

/// Picks the text for the device language (English fallback).
private func tr(_ en: String, de: String, fr: String, es: String) -> String {
    switch languageCode {
    case "de": return de
    case "fr": return fr
    case "es": return es
    default: return en
    }
}

private enum Labels {
    static var water: String { tr("Water", de: "Wasser", fr: "Eau", es: "Agua") }
    static var light: String { tr("Light", de: "Licht", fr: "Lumière", es: "Luz") }
    static var love: String { tr("Love", de: "Liebe", fr: "Amour", es: "Amor") }
    static var fertilizer: String { tr("Fertilizer", de: "Dünger", fr: "Engrais", es: "Abono") }
    static var withered: String { tr("Withered", de: "Verwelkt", fr: "Fanée", es: "Marchita") }
    static var start: String {
        tr("Open Plantagotchi to start", de: "Öffne Plantagotchi zum Starten",
           fr: "Ouvre Plantagotchi pour commencer", es: "Abre Plantagotchi para empezar")
    }
    static var allTitle: String { tr("My plants", de: "Meine Pflanzen", fr: "Mes plantes", es: "Mis plantas") }
    static var description: String {
        tr("How your plant is doing", de: "Wie es deiner Pflanze geht",
           fr: "Comment va ta plante", es: "Cómo está tu planta")
    }
    static var allDescription: String {
        tr("All your plants at a glance", de: "Alle deine Pflanzen auf einen Blick",
           fr: "Toutes tes plantes en un coup d’œil", es: "Todas tus plantas de un vistazo")
    }
}

// MARK: - Intents

/// A plant the widget settings offer.
struct PlantEntity: AppEntity {
    let id: String
    let name: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Plant"
    static var defaultQuery = PlantQuery()
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

struct PlantQuery: EntityQuery {
    private func all() -> [PlantEntity] {
        (WidgetSnapshot.load()?.plants ?? []).map { PlantEntity(id: $0.id, name: $0.name) }
    }

    func entities(for identifiers: [String]) async throws -> [PlantEntity] {
        all().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [PlantEntity] { all() }
}

/// Widget settings: a fixed plant, or none to follow the plant selected in the app.
struct SelectPlantIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Plant"
    static var description = IntentDescription("Choose a plant, or leave it empty to show the one selected in the app.")

    @Parameter(title: "Plant")
    var plant: PlantEntity?
}

/// The arrows in the widget: shows another plant (only in the widget, the app keeps its plant).
struct ShowPlantIntent: AppIntent {
    static var title: LocalizedStringResource = "Show plant"
    static var isDiscoverable = false

    @Parameter(title: "Configuration")
    var config: String

    @Parameter(title: "Plant")
    var plantId: String

    init() {}

    init(config: String, plantId: String) {
        self.config = config
        self.plantId = plantId
    }

    func perform() async throws -> some IntentResult {
        BrowseState.set(config, plantId)
        return .result()
    }
}

// MARK: - Single plant widget

struct PlantEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
    let plant: WidgetPlant?
    let config: String
}

struct PlantProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> PlantEntry {
        PlantEntry(date: .now, snapshot: .preview, plant: WidgetSnapshot.preview.active, config: "app")
    }

    func snapshot(for configuration: SelectPlantIntent, in context: Context) async -> PlantEntry {
        entry(for: configuration, snapshot: WidgetSnapshot.load() ?? .preview)
    }

    func timeline(for configuration: SelectPlantIntent, in context: Context) async -> Timeline<PlantEntry> {
        // The app reloads the widgets when it goes to the background; refresh every 30 minutes anyway.
        let entry = entry(for: configuration, snapshot: WidgetSnapshot.load())
        return Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(30 * 60)))
    }

    private func entry(for configuration: SelectPlantIntent, snapshot: WidgetSnapshot?) -> PlantEntry {
        let config = configuration.plant?.id ?? "app"
        let plant = snapshot?.plant(BrowseState.get(config))
            ?? snapshot?.plant(configuration.plant?.id)
            ?? snapshot?.active
        return PlantEntry(date: .now, snapshot: snapshot, plant: plant, config: config)
    }
}

struct PlantWidgetView: View {
    let entry: PlantEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let snapshot = entry.snapshot, let plant = entry.plant {
                HStack(spacing: 4) {
                    if snapshot.plants.count > 1 { arrow("chevron.left", snapshot, plant, -1) }
                    Text(plant.name).font(.headline).foregroundStyle(Palette.title).lineLimit(1)
                    Spacer(minLength: 2)
                    if snapshot.plants.count > 1 { arrow("chevron.right", snapshot, plant, 1) }
                }
                if plant.dead {
                    Text(Labels.withered).font(.caption).foregroundStyle(Palette.ink)
                } else {
                    NeedBar(label: Labels.water, value: plant.water, color: Palette.water)
                    NeedBar(label: Labels.light, value: plant.lux, color: Palette.light)
                    NeedBar(label: Labels.love, value: plant.love, color: Palette.love)
                    NeedBar(label: Labels.fertilizer, value: plant.fertilizer, color: Palette.fertilizer)
                }
                Spacer(minLength: 0)
                Text("● \(snapshot.coins)").font(.caption2).foregroundStyle(Palette.coins)
            } else {
                Text(Labels.start).font(.caption).foregroundStyle(Palette.ink)
            }
        }
        .containerBackground(Palette.background, for: .widget)
    }

    private func arrow(_ symbol: String, _ snapshot: WidgetSnapshot, _ plant: WidgetPlant, _ steps: Int) -> some View {
        let target = snapshot.step(from: plant.id, by: steps)?.id ?? plant.id
        return Button(intent: ShowPlantIntent(config: entry.config, plantId: target)) {
            Image(systemName: symbol).font(.caption.bold()).foregroundStyle(Palette.title)
        }
        .buttonStyle(.plain)
    }
}

struct PlantagotchiWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "PlantagotchiWidget", intent: SelectPlantIntent.self, provider: PlantProvider()) { entry in
            PlantWidgetView(entry: entry)
        }
        .configurationDisplayName("Plantagotchi")
        .description(Text(Labels.description))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - All plants widget

struct AllPlantsEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot?
}

struct AllPlantsProvider: TimelineProvider {
    func placeholder(in context: Context) -> AllPlantsEntry { AllPlantsEntry(date: .now, snapshot: .preview) }

    func getSnapshot(in context: Context, completion: @escaping (AllPlantsEntry) -> Void) {
        completion(AllPlantsEntry(date: .now, snapshot: WidgetSnapshot.load() ?? .preview))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AllPlantsEntry>) -> Void) {
        let entry = AllPlantsEntry(date: .now, snapshot: WidgetSnapshot.load())
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(30 * 60))))
    }
}

struct AllPlantsView: View {
    let entry: AllPlantsEntry
    @Environment(\.widgetFamily) private var family

    private var limit: Int { family == .systemLarge ? 5 : 3 }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(Labels.allTitle).font(.headline).foregroundStyle(Palette.title)
                Spacer()
                if let coins = entry.snapshot?.coins {
                    Text("● \(coins)").font(.caption).foregroundStyle(Palette.coins)
                }
            }
            if let snapshot = entry.snapshot, !snapshot.plants.isEmpty {
                ForEach(snapshot.plants.prefix(limit), id: \.id) { plant in
                    row(plant, active: plant.id == snapshot.activeId)
                }
                if snapshot.plants.count > limit {
                    Text("+\(snapshot.plants.count - limit)").font(.caption2).foregroundStyle(Palette.muted)
                }
            } else {
                Text(Labels.start).font(.caption).foregroundStyle(Palette.ink)
            }
            Spacer(minLength: 0)
        }
        .containerBackground(Palette.background, for: .widget)
    }

    private func row(_ plant: WidgetPlant, active: Bool) -> some View {
        HStack(spacing: 6) {
            Text((active ? "• " : "") + plant.name)
                .font(.caption.weight(active ? .bold : .regular))
                .foregroundStyle(active ? Palette.title : Palette.ink)
                .lineLimit(1)
                .frame(width: 92, alignment: .leading)
            if plant.dead {
                Text(Labels.withered).font(.caption).foregroundStyle(Palette.muted)
            } else {
                MiniBar(value: plant.water, color: Palette.water)
                MiniBar(value: plant.lux, color: Palette.light)
                MiniBar(value: plant.love, color: Palette.love)
                MiniBar(value: plant.fertilizer, color: Palette.fertilizer)
            }
        }
        .frame(maxHeight: family == .systemLarge ? 34 : 22)
    }
}

struct AllPlantsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PlantagotchiAllPlants", provider: AllPlantsProvider()) { entry in
            AllPlantsView(entry: entry)
        }
        .configurationDisplayName(Text(Labels.allTitle))
        .description(Text(Labels.allDescription))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - Shared views

private enum Palette {
    static let background = Color(red: 0.17, green: 0.11, blue: 0.14)
    static let ink = Color(red: 0.93, green: 0.93, blue: 0.93)
    static let muted = ink.opacity(0.6)
    static let title = Color(red: 1.0, green: 0.92, blue: 0.34)
    static let coins = Color(red: 0.96, green: 0.74, blue: 0.11)
    static let water = Color(red: 0.37, green: 0.80, blue: 0.89)
    static let light = Color(red: 1.0, green: 0.92, blue: 0.34)
    static let love = Color(red: 0.84, green: 0.48, blue: 0.73)
    static let fertilizer = Color(red: 0.93, green: 0.76, blue: 0.60)
}

private struct NeedBar: View {
    let label: String
    let value: Int
    let color: Color

    var body: some View {
        HStack {
            Text(label).font(.caption2).foregroundStyle(Palette.ink).frame(width: 64, alignment: .leading)
            ProgressView(value: Double(value), total: 100).tint(color)
        }
    }
}

private struct MiniBar: View {
    let value: Int
    let color: Color

    var body: some View {
        ProgressView(value: Double(max(0, min(value, 100))), total: 100).tint(color)
    }
}

@main
struct PlantagotchiWidgets: WidgetBundle {
    var body: some Widget {
        PlantagotchiWidget()
        AllPlantsWidget()
    }
}
