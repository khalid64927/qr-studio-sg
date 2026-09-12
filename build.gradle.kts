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
    analyzers {
        // This is a Kotlin/Compose project with no .NET, Node, or native-assembly
        // dependencies anywhere in the tree; leaving these on just burns scan time
        // (and the assembly analyzer needs a local install of dotnet besides).
        assemblyEnabled = false
        nodeEnabled = false
        nuspecEnabled = false
    }
}
