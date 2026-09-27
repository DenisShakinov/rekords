package rekords.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val REPOSITORY = "github.com/DenisShakinov/rekords"

/**
 * See [RekordsExtension.publish].
 *
 * The Maven Central credentials and the signing key are read from Gradle properties the plugin
 * knows, which are kept out of the repository - `~/.gradle/gradle.properties` or
 * `ORG_GRADLE_PROJECT_*` environment variables.
 */
internal fun Project.configurePublishing(description: String) {
    pluginManager.apply("com.vanniktech.maven.publish")
    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral()
        // Central takes no unsigned release, while a snapshot published to Maven Local for a try
        // should not need the key.
        if (!version.toString().endsWith("-SNAPSHOT")) {
            signAllPublications()
        }
        pom {
            name.set(project.name)
            this.description.set(description)
            inceptionYear.set("2026")
            // Also what klibs.io finds the repository by.
            url.set("https://$REPOSITORY")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("DenisShakinov")
                    name.set("Dzianis Shakinau")
                    url.set("https://github.com/DenisShakinov")
                }
            }
            scm {
                url.set("https://$REPOSITORY")
                connection.set("scm:git:git://$REPOSITORY.git")
                developerConnection.set("scm:git:ssh://git@$REPOSITORY.git")
            }
        }
    }
}
