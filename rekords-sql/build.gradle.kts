import rekords.gradle.RekordsTarget

plugins {
    id("rekords.multiplatform")
}

rekords {
    // Pure Kotlin, but built on by the SQLite engine alone, so the targets it reaches - publishing
    // the rest would cost Maven Central files for nobody to use.
    targets(RekordsTarget.sqlite)
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
