package rekords.gradle

import org.gradle.api.Project
import javax.inject.Inject

/**
 * Configures which targets a rekords module is built for, how its native binaries are linked and
 * whether it is published.
 *
 * Targets are created as soon as they are declared, so the block has to be evaluated before the
 * `kotlin { sourceSets { ... } }` block that refers to them.
 */
abstract class RekordsExtension @Inject constructor(private val project: Project) {

    /** Builds the module for every target in [RekordsTarget]. */
    fun allTargets() = targets(RekordsTarget.all)

    /** Builds the module for [targets] only. */
    fun targets(vararg targets: RekordsTarget) = targets(targets.toSet())

    /** Builds the module for [targets] only. */
    fun targets(targets: Iterable<RekordsTarget>) = project.configureTargets(targets.toSet())

    /**
     * Links the SQLite the operating system ships into every native binary this module builds.
     *
     * A klib carries no linker options, so this covers only what Kotlin links here - the test
     * executables. A consumer still states the flag for its own binary, and an application shipping
     * a static framework states it in Xcode's `OTHER_LDFLAGS`, which no `linkerOpts` reaches.
     *
     * Declared per module rather than for the whole library: the targets that ship no SQLite of
     * their own, Linux and MinGW among them, fail to link a binary asking for it.
     */
    fun linkSystemSQLite() = project.configureSystemSQLiteLinking()

    /**
     * Publishes the module to Maven Central as `group:name:version` of the project, described in
     * its POM by [description]. A module that does not call it is not published.
     */
    fun publish(description: String) = project.configurePublishing(description)
}
