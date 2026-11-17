# iOS widget

The widget code is ready but the Widget Extension target is not part of the Xcode project yet,
because adding it requires your Apple team and an App Group.

1. In Xcode: File > New > Target > Widget Extension, name it `PlantagotchiWidget`,
   uncheck "Include Configuration App Intent". Delete the generated Swift files and add
   `PlantagotchiWidget.swift` from this folder to the new target.
2. Add the capability "App Groups" to both the `iosApp` target and the widget target and
   enable `group.dev.mothlight.plantagotchi`.
3. Run the app once: it writes the plant state into the app group, the widget reads it.

The shared Kotlin code (`IosWidgetBridge`) already writes to that app group.
