plugins {
    id("rekords.multiplatform")
    // Declared here, not only applied by the convention plugin, so that the `android { }` accessor
    // is generated for this build script.
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

rekords {
    // Pure Kotlin, so the same targets as `core`.
    allTargets()
    publish(description = "Rekords engine keeping rekords in memory, for tests.")
}

kotlin {
    android {
        withHostTest {}
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.rekordsCore)
        }
        commonTest.dependencies {
            implementation(projects.rekordsTest)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
