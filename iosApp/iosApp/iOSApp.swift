import SwiftUI
import WidgetKit

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { phase in
            // The shared code writes the plant state into the app group; show it right away.
            if phase == .background { WidgetCenter.shared.reloadAllTimelines() }
        }
    }
}
