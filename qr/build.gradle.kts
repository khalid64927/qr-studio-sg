import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

// FR-201: one encoder for all four targets. Like :payload this module is pure Kotlin
// with no UI dependency, so the module matrix can be tested without a renderer. See
// config/publishing.gradle.kts (Android AAR + JVM jar), the XCFramework block below
// (iOS/SPM) and the js(IR) block (npm) — this ships everywhere :payload does, so a
// native-UI app can render its own QR view from the same module matrix.
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

    val xcf = XCFramework("QrStudioQrKit")
    iosArm64 {
        binaries.framework {
            baseName = "QrStudioQrKit"
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "QrStudioQrKit"
            xcf.add(this)
        }
    }
    iosX64 {
        binaries.framework {
            baseName = "QrStudioQrKit"
            xcf.add(this)
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // Library (not browser-executable) output — see :payload's build.gradle.kts for why.
    js(IR) {
        binaries.library()
        generateTypeScriptDefinitions()
        useCommonJs()
        nodejs()
        // GitHub Packages' npm registry requires the package name to be scoped under
        // the repository owner.
        compilations["main"].packageJson { name = "@khalid64927/qr-studio-sg-qr" }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.qrcode.kotlin)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // ZXing is a test-only decoder: it reads back what we render and proves the
        // matrix survives a real scan. JVM only; never shipped in the app.
        jvmTest.dependencies {
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
        }
    }
}

android {
    namespace = "sg.qrstudio.qr"
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
