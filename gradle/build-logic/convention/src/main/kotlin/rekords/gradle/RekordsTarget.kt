package rekords.gradle

/**
 * A target a rekords module can be built for.
 *
 * The enum lists every target the library as a whole can reach; each module declares the subset its
 * own dependencies allow via `rekords { targets(...) }`.
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
    MacosX64,
    TvosArm64,
    TvosSimulatorArm64,
    TvosX64,
    WatchosArm32,
    WatchosArm64,
    WatchosDeviceArm64,
    WatchosSimulatorArm64,
    WatchosX64,
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
