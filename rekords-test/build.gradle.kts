plugins {
    id("rekords.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

rekords {
    // Pure Kotlin, so the same targets as `core`.
    allTargets()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.rekordsCore)
            implementation(libs.kotlin.test)
            // The test rekords carry dates in their constructors, so whoever builds one needs it.
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
