import rekords.gradle.RekordsTarget

plugins {
    id("rekords.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

rekords {
    // The samples run on the JVM, `./gradlew :sample:run`. The other targets build the same common
    // code with the engine each depends on - SQLite on Apple platforms, IndexedDB in the browser.
    targets(
        RekordsTarget.Jvm,
        RekordsTarget.Js,
        RekordsTarget.WasmJs,
        RekordsTarget.IosArm64,
        RekordsTarget.IosSimulatorArm64,
        RekordsTarget.MacosArm64,
    )
    // Not published: the module shows how the library is used.
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.rekordsCore)
            implementation(projects.rekordsMemory)
            implementation(projects.rekordsCrypto)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        jvmMain.dependencies {
            implementation(projects.rekordsSqlite)
        }
        appleMain.dependencies {
            implementation(projects.rekordsSqlite)
        }
        webMain.dependencies {
            implementation(projects.rekordsIndexeddb)
        }
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs every sample on the JVM."
    val main = kotlin.jvm().compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass = "io.github.denisshakinov.rekords.sample.MainKt"
    // The SQLite engine keeps a database on the JVM where its name points, relative to the working
    // directory: each run starts from empty storages of its own.
    val runDirectory = layout.buildDirectory.dir("run")
    workingDir(runDirectory)
    doFirst {
        runDirectory.get().asFile.run {
            deleteRecursively()
            mkdirs()
        }
    }
}
