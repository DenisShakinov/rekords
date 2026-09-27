plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.maven.publish.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("rekordsMultiplatform") {
            id = "rekords.multiplatform"
            implementationClass = "rekords.gradle.RekordsMultiplatformPlugin"
        }
    }
}
