package dev.mothlight.plantagotchi

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import dev.mothlight.plantagotchi.platform.iosPlatformServices
import platform.UIKit.UIViewController
import platform.Foundation.NSBundle
import dev.mothlight.plantagotchi.dev.AppEnv

@Suppress("FunctionName", "unused") // called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController {
    // PG_APP_ENV in Config.xcconfig, via Info.plist; CI rewrites it for test and prod builds.
    val env = remember { AppEnv.parse(NSBundle.mainBundle.objectForInfoDictionaryKey("AppEnv") as? String) }
    App(remember { iosPlatformServices() }, env)
}
