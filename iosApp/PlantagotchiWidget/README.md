# iOS widget

WidgetKit extension `PlantagotchiWidget` (iOS 17+), embedded in the app, with two widgets:

- **Plantagotchi** (small, medium): one plant with its needs. The arrows browse the plants inside the
  widget (the app keeps its plant). Edit Widget picks a fixed plant; empty = follow the app.
- **My plants** (medium: 3, large: 5): all plants as rows with mini bars, the active one highlighted.

- Bundle ID `dev.mothlight.plantagotchi.widget` (`PG_WIDGET_BUNDLE_IDENTIFIER` in `Config.xcconfig`)
- App and widget share `group.dev.mothlight.plantagotchi` (`*.entitlements`). The shared Kotlin code
  (`IosWidgetBridge`) writes the plant state there, the widget reads it. The app reloads the widget
  when it goes to the background.
- Signing: locally automatic; the TestFlight workflow uses `IOS_WIDGET_PROVISIONING_PROFILE_BASE64`
  (see `docs/testflight.md`).
