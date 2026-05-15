plugins {
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
}

subprojects {
    // Android Studio may ask the app module for this Kotlin DSL model task during sync.
    // The root task is enough for CLI builds, but this alias keeps IDE import stable.
    tasks.register("prepareKotlinBuildScriptModel") {
        group = "help"
        description = "Compatibility task for Android Studio Kotlin DSL model sync."
    }
}
