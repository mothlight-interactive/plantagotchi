package dev.mothlight.plantagotchi

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import dev.mothlight.plantagotchi.platform.iosPlatformServices
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(remember { iosPlatformServices() })
}
