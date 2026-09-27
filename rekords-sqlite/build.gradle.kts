import rekords.gradle.RekordsTarget

plugins {
    id("rekords.multiplatform")
    // Declared here, not only applied by the convention plugin, so that the `android { }` accessor
    // is generated for this build script.
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

rekords {
    // What androidx.sqlite reaches: `sqlite-framework` covers Android and the native targets,
    // `sqlite-bundled` covers the JVM. The web targets are left out on purpose - there
    // `SQLiteDriver` is a suspending interface served by a web worker, so `sqlite-web` needs an
    // adapter of its own rather than the one in SQLiteSQLDriver.
    targets(
        RekordsTarget.Android,
        RekordsTarget.Jvm,
        RekordsTarget.IosArm64,
        RekordsTarget.IosSimulatorArm64,
        RekordsTarget.MacosArm64,
        RekordsTarget.TvosArm64,
        RekordsTarget.TvosSimulatorArm64,
        RekordsTarget.WatchosArm32,
        RekordsTarget.WatchosArm64,
        RekordsTarget.WatchosDeviceArm64,
        RekordsTarget.WatchosSimulatorArm64,
        RekordsTarget.LinuxArm64,
        RekordsTarget.LinuxX64,
    )
    // NativeSQLiteDriver binds the SQLite the operating system ships, so every native binary built
    // from this module states the flag - the test executables included.
    linkSystemSQLite()
    publish(
        description = "Rekords engine keeping rekords in SQLite on Android, the JVM, " +
            "Apple platforms and Linux, through androidx.sqlite.",
    )
}

kotlin {
    android {
        withHostTest {}
        withDeviceTest {}
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.rekordsCore)
            api(libs.androidx.sqlite)
            implementation(projects.rekordsSql)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        nativeMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(projects.rekordsTest)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest") {
            dependsOn(commonTest.get())
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

// The Android editor binds the SQLite the operating system ships, so the shared suite runs as a
// device test - `AndroidSQLiteRekordsEditorTest`. The host test task exists only because
// `commonTest` does, and all it compiles from there is the abstract base class, so it never
// discovers a runnable test of its own.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    failOnNoDiscoveredTests = false
}
