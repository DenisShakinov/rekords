plugins {
    id("rekords.multiplatform")
    // Declared here, not only applied by the convention plugin, so that the `android { }` accessor
    // is generated for this build script.
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

rekords {
    // Pure Kotlin, so every target kotlinx-coroutines, -datetime and -serialization publish for.
    allTargets()
    publish(
        description = "Kotlin Multiplatform storage for annotated classes: schemas with " +
            "migrations, filters, ordering and transactions, over a pluggable engine.",
    )
}

kotlin {
    android {
        withHostTest {}
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(projects.rekordsTest)
            implementation(libs.kotlin.test)
        }
    }
}
