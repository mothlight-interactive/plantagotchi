plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/** Gradle property (-Pname=...) or environment variable, used by the release workflows. */
fun releaseProp(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

// One version for both platforms: MARKETING_VERSION in iosApp/Configuration/Config.xcconfig.
val marketingVersion: String = rootProject.file("iosApp/Configuration/Config.xcconfig").readLines()
    .firstNotNullOfOrNull { Regex("""^\s*MARKETING_VERSION\s*=\s*(\S+)""").find(it)?.groupValues?.get(1) }
    ?: "1.0"

// Build environment: dev for local builds, test for Play internal (and TestFlight), prod for store
// releases (release.yml). The test mode exists in dev and test only.
val appEnv: String = releaseProp("appEnv")?.trim()?.lowercase().orEmpty().ifEmpty { "dev" }
require(appEnv in setOf("dev", "test", "prod")) { "appEnv must be dev, test or prod, got '$appEnv'" }

val releaseKeystore = releaseProp("ANDROID_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "dev.mothlight.plantagotchi"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.mothlight.plantagotchi"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI passes a time based versionCode (.github/scripts/build-number.sh).
        versionCode = releaseProp("versionCode")?.toInt() ?: 1
        versionName = releaseProp("versionName") ?: marketingVersion

        buildConfigField("String", "APP_ENV", "\"$appEnv\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseProp("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = releaseProp("ANDROID_KEY_ALIAS")
                keyPassword = releaseProp("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
