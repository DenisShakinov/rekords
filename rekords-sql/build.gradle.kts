plugins {
    id("rekords.multiplatform")
}

rekords {
    // Pure Kotlin, so the same targets as `core`.
    allTargets()
    publish(description = "The SQL editor the SQL-based Rekords engines are built on.")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.rekordsCore)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
