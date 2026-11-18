# R8 rules for the release build (isMinifyEnabled + isShrinkResources).
# Most libraries ship consumer rules (kotlinx.serialization, Ktor, coroutines, Compose, WorkManager,
# Glance). The rules below make the app's own reflective entry points explicit, so a library update
# that drops a consumer rule does not silently break a release build.

# Keep source file and line numbers for readable crash stack traces (deobfuscate with mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization -------------------------------------------------------------------------
# Game state, settings snapshots and the widget snapshot are stored as JSON, the weather comes as JSON.
# Field names come from the generated serializers, so obfuscating the classes is safe; keep the
# serializer lookups (Companion.serializer(), $$serializer, enum and object serializers).
-keepattributes *Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class dev.mothlight.plantagotchi.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class dev.mothlight.plantagotchi.** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class dev.mothlight.plantagotchi.** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.mothlight.plantagotchi.**$$serializer { *; }

# --- Ktor (OkHttp engine) --------------------------------------------------------------------------
# HttpClient {} without an explicit engine finds OkHttp through META-INF/services (ServiceLoader).
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { <init>(); }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { <init>(); }

# --- Compose resources ---------------------------------------------------------------------------
# Strings, images, fonts and sounds live in assets/composeResources and are read by path, so R8 and
# the resource shrinker do not touch them. Nothing to keep here.

# --- Glance widgets ------------------------------------------------------------------------------
# Receivers and activities from the manifest are kept by AAPT. Action callbacks are created by class
# name when a widget button is tapped.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }

# --- WorkManager ---------------------------------------------------------------------------------
# Reminder workers are instantiated by class name (also after an app update, from the work database),
# so their names and constructors must stay stable.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Logging -------------------------------------------------------------------------------------
# The app has no logging; strip verbose/debug/info logs in case a dependency or future code adds some.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
