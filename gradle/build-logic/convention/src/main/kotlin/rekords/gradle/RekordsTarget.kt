package rekords.gradle

/**
 * A target a rekords module can be built for.
 *
 * The enum lists every target the library as a whole can reach; each module declares the subset its
 * own dependencies allow via `rekords { targets(...) }`.
 *
 * The targets Kotlin deprecates are left out - `macosX64`, `tvosX64`, `watchosX64` and
 * `watchosArm32`, see https://kotl.in/native-targets-tiers - as every target published costs a
 * publication per module, and Maven Central limits how many files a month an organization publishes.
 */
enum class RekordsTarget {
    Android,
    Jvm,
    Js,
    WasmJs,
    WasmWasi,
    IosArm64,
    IosSimulatorArm64,
    IosX64,
    MacosArm64,
    TvosArm64,
    TvosSimulatorArm64,
    WatchosArm64,
    WatchosDeviceArm64,
    WatchosSimulatorArm64,
    LinuxArm64,
    LinuxX64,
    MingwX64,
    AndroidNativeArm32,
    AndroidNativeArm64,
    AndroidNativeX64,
    AndroidNativeX86,
    ;

    companion object {

        /** Every target the rekords library can be built for. */
        val all: Set<RekordsTarget> get() = entries.toSet()
    }
}
