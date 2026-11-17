import SwiftUI
import WidgetKit

/// Mirrors `WidgetSnapshot` written by the shared Kotlin code into the app group.
struct PlantSnapshot: Codable {
    let species: String
    let stage: Int
    let dead: Bool
    let lux: Int
    let water: Int
    let love: Int
    let fertilizer: Int
    let coins: Int
    let updatedAt: Int64

    static let appGroup = "group.dev.mothlight.plantagotchi"

    static func load() -> PlantSnapshot? {
        guard let json = UserDefaults(suiteName: appGroup)?.string(forKey: "widget_snapshot"),
              let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(PlantSnapshot.self, from: data)
    }

    static let preview = PlantSnapshot(species: "SPROUT", stage: 1, dead: false, lux: 80, water: 45, love: 60, fertilizer: 90, coins: 120, updatedAt: 0)
}

struct PlantEntry: TimelineEntry {
    let date: Date
    let snapshot: PlantSnapshot?
}

struct PlantProvider: TimelineProvider {
    func placeholder(in context: Context) -> PlantEntry { PlantEntry(date: .now, snapshot: .preview) }

    func getSnapshot(in context: Context, completion: @escaping (PlantEntry) -> Void) {
        completion(PlantEntry(date: .now, snapshot: PlantSnapshot.load() ?? .preview))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PlantEntry>) -> Void) {
        // The app writes a new snapshot whenever it saves; refresh every 30 minutes.
        let entry = PlantEntry(date: .now, snapshot: PlantSnapshot.load())
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(30 * 60))))
    }
}

struct PlantWidgetView: View {
    let entry: PlantEntry

    private let background = Color(red: 0.17, green: 0.11, blue: 0.14)
    private let ink = Color(red: 0.93, green: 0.93, blue: 0.93)

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let s = entry.snapshot {
                HStack {
                    Text(s.species.capitalized).font(.headline).foregroundStyle(Color.yellow)
                    Spacer()
                    Text("● \(s.coins)").font(.caption).foregroundStyle(Color.orange)
                }
                if s.dead {
                    Text("Withered").font(.caption).foregroundStyle(ink)
                } else {
                    need("Water", s.water, .cyan)
                    need("Light", s.lux, .yellow)
                    need("Love", s.love, .pink)
                    need("Fertilizer", s.fertilizer, .orange)
                }
            } else {
                Text("Open Plantagotchi to start").font(.caption).foregroundStyle(ink)
            }
        }
        .containerBackground(background, for: .widget)
    }

    private func need(_ label: String, _ value: Int, _ color: Color) -> some View {
        HStack {
            Text(label).font(.caption2).foregroundStyle(ink).frame(width: 64, alignment: .leading)
            ProgressView(value: Double(value), total: 100).tint(color)
        }
    }
}

@main
struct PlantagotchiWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "PlantagotchiWidget", provider: PlantProvider()) { entry in
            PlantWidgetView(entry: entry)
        }
        .configurationDisplayName("Plantagotchi")
        .description("How your plant is doing")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
