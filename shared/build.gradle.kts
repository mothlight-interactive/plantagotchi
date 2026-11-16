import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

/**
 * Supabase credentials are read from local.properties (or env vars) and never committed.
 *   SUPABASE_URL=https://<project>.supabase.co
 *   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
 * Without them the app runs in local-only mode.
 */
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String = localProps.getProperty(name) ?: System.getenv(name) ?: ""

val generateAppConfig = tasks.register("generateAppConfig") {
    val url = secret("SUPABASE_URL")
    val key = secret("SUPABASE_PUBLISHABLE_KEY")
    val outDir = layout.buildDirectory.dir("generated/appConfig/commonMain/kotlin")
    inputs.property("url", url)
    inputs.property("key", key)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("dev/mothlight/plantagotchi/AppConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package dev.mothlight.plantagotchi
            |
            |internal object AppConfig {
            |    const val SUPABASE_URL: String = "$url"
            |    const val SUPABASE_PUBLISHABLE_KEY: String = "$key"
            |}
            |""".trimMargin()
        )
    }
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
            kotlin.srcDir(generateAppConfig)
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

                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.auth)
                implementation(libs.supabase.postgrest)

                implementation(libs.multiplatform.settings)
                implementation(libs.multiplatform.settings.noarg)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.location)
            implementation(libs.androidx.core.ktx)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.mothlight.plantagotchi.resources"
    generateResClass = always
}
