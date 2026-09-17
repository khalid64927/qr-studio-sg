/**
 * Shared Maven publishing config for the standalone library modules (:payload, :qr).
 * Applied via `apply(from = "$rootDir/config/publishing.gradle.kts")` — :ui and
 * :composeApp never apply this; they are not published.
 *
 * Every Kotlin Multiplatform target declared in a module (Android, JVM, iOS) gets its
 * own Maven publication automatically once the `maven-publish` plugin is applied —
 * this script only needs to say *where* those publications go and *what* they're
 * called.
 *
 * Publishes to GitHub Packages. Consuming it — even for a public repository — requires
 * a GitHub personal access token with `read:packages`, because GitHub Packages does not
 * support unauthenticated Maven reads. See README's Publishing section for the consumer
 * setup and for how to switch this to Maven Central instead.
 */

apply(plugin = "maven-publish")

group = "sg.qrstudio"
version = (findProperty("libraryVersion") as String?) ?: "0.1.0"

configure<PublishingExtension> {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/khalid64927/qr-studio-sg")
            credentials {
                // CI sets GITHUB_ACTOR/GITHUB_TOKEN automatically. Locally, put
                // gpr.user=<username> and gpr.token=<a PAT with write:packages> in
                // ~/.gradle/gradle.properties — never in this repo.
                username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as String?
                password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.token") as String?
            }
        }
    }
}
