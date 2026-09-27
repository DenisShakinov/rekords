package rekords.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon

/**
 * Configures a rekords library module as a Kotlin Multiplatform library.
 *
 * Configures only what a library needs: no Apple frameworks, no web executables and no
 * application dependencies.
 *
 * Targets are declared per module, see [RekordsExtension].
 */
class RekordsMultiplatformPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        extensions.create("rekords", RekordsExtension::class.java, this)
        kotlinMultiplatform {
            applyDefaultHierarchyTemplate()
            jvmToolchain(jdkVersion = libs.version("java").toInt())
            targets.configureEach {
                compilations.configureEach {
                    compileTaskProvider.configure {
                        compilerOptions {
                            freeCompilerArgs.add("-Xexpect-actual-classes")
                        }
                    }
                }
            }
            metadata {
                compilations.configureEach {
                    if (name == KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME) {
                        compileTaskProvider.configure {
                            // Replaces the default library name with the project path to avoid
                            // `duplicate library name: foo_commonMain`
                            // https://youtrack.jetbrains.com/issue/KT-57914
                            this as KotlinCompileCommon
                            moduleName.set("${projectPath()}_commonMain")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalWasmDsl::class)
internal fun Project.configureTargets(targets: Set<RekordsTarget>) {
    if (RekordsTarget.Android in targets) {
        pluginManager.apply("com.android.kotlin.multiplatform.library")
    }
    kotlinMultiplatform {
        for (target: RekordsTarget in targets) {
            when (target) {
                RekordsTarget.Android -> configure<KotlinMultiplatformAndroidLibraryTarget> {
                    namespace = androidNamespace()
                    compileSdk = libs.version("android.compile.sdk").toInt()
                    minSdk = libs.version("android.min.sdk").toInt()
                }
                // Libraries only ship klibs, so the web targets declare a Node.js environment for
                // their tests and nothing else. Consumers stay free to run them in a browser.
                RekordsTarget.Jvm -> jvm()
                RekordsTarget.Js -> js { nodejs() }
                RekordsTarget.WasmJs -> wasmJs { nodejs() }
                RekordsTarget.WasmWasi -> wasmWasi { nodejs() }
                RekordsTarget.IosArm64 -> iosArm64()
                RekordsTarget.IosSimulatorArm64 -> iosSimulatorArm64()
                RekordsTarget.IosX64 -> iosX64()
                RekordsTarget.MacosArm64 -> macosArm64()
                RekordsTarget.MacosX64 -> macosX64()
                RekordsTarget.TvosArm64 -> tvosArm64()
                RekordsTarget.TvosSimulatorArm64 -> tvosSimulatorArm64()
                RekordsTarget.TvosX64 -> tvosX64()
                RekordsTarget.WatchosArm32 -> watchosArm32()
                RekordsTarget.WatchosArm64 -> watchosArm64()
                RekordsTarget.WatchosDeviceArm64 -> watchosDeviceArm64()
                RekordsTarget.WatchosSimulatorArm64 -> watchosSimulatorArm64()
                RekordsTarget.WatchosX64 -> watchosX64()
                RekordsTarget.LinuxArm64 -> linuxArm64()
                RekordsTarget.LinuxX64 -> linuxX64()
                RekordsTarget.MingwX64 -> mingwX64()
                RekordsTarget.AndroidNativeArm32 -> androidNativeArm32()
                RekordsTarget.AndroidNativeArm64 -> androidNativeArm64()
                RekordsTarget.AndroidNativeX64 -> androidNativeX64()
                RekordsTarget.AndroidNativeX86 -> androidNativeX86()
            }
        }
    }
}

/** See [RekordsExtension.linkSystemSQLite]. */
internal fun Project.configureSystemSQLiteLinking() {
    kotlinMultiplatform {
        targets.withType<KotlinNativeTarget>().configureEach {
            binaries.configureEach {
                linkerOpts.add("-lsqlite3")
            }
        }
    }
}

private fun Project.kotlinMultiplatform(block: KotlinMultiplatformExtension.() -> Unit) =
    extensions.configure<KotlinMultiplatformExtension>(block)

private val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun VersionCatalog.version(name: String): String = findVersion(name).get().displayName

private fun Project.projectPath(): String = path.substring(1).replace(":", "_")

/** `:rekords-sqlite` in group `io.github.example` -> `io.github.example.rekords.sqlite`. */
private fun Project.androidNamespace(): String = "$group.${name.replace('-', '.')}"
