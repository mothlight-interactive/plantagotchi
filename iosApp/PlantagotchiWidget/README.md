# iOS widget

WidgetKit extension `PlantagotchiWidget` (iOS 17+, small and medium), embedded in the app.

- Bundle ID `dev.mothlight.plantagotchi.widget` (`PG_WIDGET_BUNDLE_IDENTIFIER` in `Config.xcconfig`)
- App and widget share `group.dev.mothlight.plantagotchi` (`*.entitlements`). The shared Kotlin code
  (`IosWidgetBridge`) writes the plant state there, the widget reads it. The app reloads the widget
  when it goes to the background.
- Signing: locally automatic; the TestFlight workflow uses `IOS_WIDGET_PROVISIONING_PROFILE_BASE64`
  (see `docs/testflight.md`).
