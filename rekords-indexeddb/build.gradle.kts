import rekords.gradle.RekordsTarget

plugins {
    id("rekords.multiplatform")
}

rekords {
    // What com.juul.indexeddb is published for. IndexedDB is the browser's own - nothing is
    // bundled to reach it - so there is nothing else it could be built for.
    targets(RekordsTarget.Js, RekordsTarget.WasmJs)
    publish(
        description = "Rekords engine keeping rekords in the browser's IndexedDB on Kotlin/JS " +
            "and Kotlin/Wasm.",
    )
}

kotlin {
    sourceSets {
        webMain.dependencies {
            api(projects.rekordsCore)
            implementation(libs.indexeddb.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        webTest.dependencies {
            implementation(projects.rekordsTest)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // Node, which the tests run in, has no IndexedDB of its own.
            implementation(npm("fake-indexeddb", "6.2.5"))
        }
    }
}
