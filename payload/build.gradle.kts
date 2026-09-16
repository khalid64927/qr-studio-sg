import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

// FR-101 / §9.2: this module is pure Kotlin with no platform or UI dependencies,
// so it can be extracted as a standalone library without touching anything else.
// See config/publishing.gradle.kts (Android AAR + JVM jar to GitHub Packages), the
// XCFramework block below (iOS/SPM) and the js(IR) block (npm) — this module ships to
// all three so a native-UI app can consume the PayNow logic without Compose at all.
kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        // KMP's own publishing hook for the Android target — distinct from (and in
        // addition to) the AGP `android.publishing.singleVariant` below, which controls
        // which variant that publication is built from.
        publishLibraryVariants("release")
    }
    jvm()

    // One XCFramework bundling all three Apple slices, for SPM's binaryTarget. Each
    // module gets its own framework name/product so a consumer picks only what it
    // needs — see Package.swift at the repo root.
    val xcf = XCFramework("PayNowPayloadKit")
    iosArm64 {
        binaries.framework {
            baseName = "PayNowPayloadKit"
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "PayNowPayloadKit"
            xcf.add(this)
        }
    }
    iosX64 {
        binaries.framework {
            baseName = "PayNowPayloadKit"
            xcf.add(this)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // Library (not browser-executable) output: a plain npm package with a .d.ts file,
    // consumable from Node or a bundler by a native-JS/TS UI — not just from this repo's
    // own Compose-for-Web build. GitHub Packages publishing is a separate `npm publish`
    // step (see README) since Kotlin/JS has no Gradle-native npm-registry publish task.
    js(IR) {
        binaries.library()
        generateTypeScriptDefinitions()
        useCommonJs()
        nodejs()
        // GitHub Packages' npm registry requires the package name to be scoped under
        // the repository owner.
        compilations["main"].packageJson { name = "@khalid64927/qr-studio-sg-payload" }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // TC-04: independent EMVCo parser, JVM test source set only. Never shipped.
        jvmTest.dependencies {
            implementation(libs.emv.qrcode)
        }
    }
}

android {
    namespace = "sg.qrstudio.payload"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // The AAR variant maven-publish picks up. Only "release" is published — a debug
    // AAR is never a library artifact worth shipping.
    publishing {
        singleVariant("release")
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        htmlReport = true
        xmlReport = true
    }
}

apply(from = "$rootDir/config/publishing.gradle.kts")
