plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.owasp.dependencycheck)
}

// Formatting/style gate: same ktlint ruleset on every module's Kotlin sources plus this
// file and every module's own build.gradle.kts. `spotlessCheck` runs in CI; `spotlessApply`
// is what a human runs locally to fix violations rather than hand-editing whitespace.
subprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get())
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }
}

// FR-604-adjacent for the build itself: a payment app should not ship a known-vulnerable
// dependency silently. CVSS 7+ (High/Critical) fails the build rather than just warning;
// anything genuinely a false positive for this project goes in the suppression file below
// with a dated justification, not by lowering the threshold.
dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "$projectDir/config/dependency-check-suppressions.xml"
    formats = listOf("HTML", "JSON")
    // A fixed, project-relative path (not the default under GRADLE_USER_HOME) so CI can
    // cache it directly — see ci.yml's dependency-check-data cache step. Without this,
    // every CI run re-downloads the entire NVD CVE database from scratch, which can take
    // 30-90+ minutes even with an API key (NVD's own rate limits apply regardless).
    data {
        directory = "$rootDir/.dependency-check-data"
    }
    // The NVD_API_KEY env var (set in ci.yml from the repo's NVD_API_KEY secret) isn't
    // read automatically by dependency-check-core or this plugin — grepping both jars
    // turns up no reference to that name anywhere; it only exists once wired explicitly
    // into this nvd.apiKey property. Without this block, dependency-check silently falls
    // back to unauthenticated (heavily rate-limited) NVD access regardless of whether the
    // secret is set, which is what was producing "An NVD API Key was not provided" even
    // with a real secret configured.
    nvd {
        apiKey = System.getenv("NVD_API_KEY")
    }
    analyzers {
        // This is a Kotlin/Compose project with no .NET, Node, or native-assembly
        // dependencies anywhere in the tree; leaving these on just burns scan time
        // (and the assembly analyzer needs a local install of dotnet besides).
        assemblyEnabled = false
        nodeEnabled = false
        nuspecEnabled = false
    }
}
