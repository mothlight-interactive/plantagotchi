import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

/** Classifier of the Compose desktop runtime for this machine, e.g. linux-x64 or macos-arm64. */
val skikoHost: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
    val name = when {
        "mac" in os -> "macos"
        "win" in os -> "windows"
        else -> "linux"
    }
    "$name-$arch"
}

kotlin {
    android {
        namespace = "dev.mothlight.plantagotchi.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}
    }

    // JVM desktop target, used only to run Compose UI tests headless (:shared:desktopTest).
    // Not shipped: there is no desktop app.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)


                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.noarg)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.location)
            implementation(libs.androidx.core.ktx)
            implementation(libs.work.runtime.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.compose.ui.test)
                implementation(libs.kotlinx.coroutines.swing)
                // Skiko native binaries for the machine running the tests.
                implementation("org.jetbrains.compose.desktop:desktop-jvm-$skikoHost:${libs.versions.compose.multiplatform.get()}")
            }
        }
    }
}

tasks.withType<Test>().matching { it.name == "desktopTest" }.configureEach {
    // Offscreen rendering with Skia, no display needed (CI runs on a plain Linux runner).
    systemProperty("java.awt.headless", "true")
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.mothlight.plantagotchi.resources"
    generateResClass = always
}
