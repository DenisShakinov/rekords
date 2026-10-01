import rekords.gradle.RekordsTarget

plugins {
    id("rekords.multiplatform")
    // Declared here, not only applied by the convention plugin, so that the `android { }` accessor
    // is generated for this build script.
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

rekords {
    // What cryptography-kotlin's optimal provider covers - JCA on the JVM and Android, CryptoKit on
    // Apple platforms, OpenSSL elsewhere - and the browser, where @noble stands in for WebCrypto,
    // which is asynchronous only. Wasm WASI and Android Native have no provider of the optimal one.
    targets(
        RekordsTarget.Android,
        RekordsTarget.Jvm,
        RekordsTarget.Js,
        RekordsTarget.WasmJs,
        RekordsTarget.IosArm64,
        RekordsTarget.IosSimulatorArm64,
        RekordsTarget.IosX64,
        RekordsTarget.MacosArm64,
        RekordsTarget.TvosArm64,
        RekordsTarget.TvosSimulatorArm64,
        RekordsTarget.WatchosArm64,
        RekordsTarget.WatchosDeviceArm64,
        RekordsTarget.WatchosSimulatorArm64,
        RekordsTarget.LinuxArm64,
        RekordsTarget.LinuxX64,
        RekordsTarget.MingwX64,
    )
    publish(
        description = "AES-GCM cipher encrypting the fields of rekords, deterministically or not, " +
            "on every Rekords engine.",
    )
}

kotlin {
    android {
        withHostTest {}
    }
    sourceSets {
        commonMain.dependencies {
            api(projects.rekordsCore)
        }
        // The targets cryptography-kotlin's providers serve, which every one but the web is.
        val cryptographyMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.cryptography.provider.optimal)
            }
        }
        jvmMain.get().dependsOn(cryptographyMain)
        androidMain.get().dependsOn(cryptographyMain)
        nativeMain.get().dependsOn(cryptographyMain)
        webMain.dependencies {
            implementation(npm("@noble/ciphers", libs.versions.noble.ciphers.get()))
            implementation(npm("@noble/hashes", libs.versions.noble.hashes.get()))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(projects.rekordsMemory)
            implementation(projects.rekordsTest)
            implementation(libs.kotlinx.coroutines.test)
        }
        webTest.dependencies {
            implementation(projects.rekordsIndexeddb)
            implementation(projects.rekordsTest)
            implementation(libs.kotlinx.coroutines.test)
            // Node, which the tests run in, has no IndexedDB of its own.
            implementation(npm("fake-indexeddb", "6.2.5"))
        }
    }
}
