import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

/**
 * The app shell: per-platform entry points only (`MainActivity`, desktop/web `main()`,
 * `MainViewController`). Every screen, component and design-system type lives in
 * `:ui` — this module wires a platform's window/activity/viewport to [sg.qrstudio.app.App]
 * and nothing else, so a platform-specific UI swap never touches this module.
 */
kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // §9.1 lists iosX64 as a target. It is dropped *here* because
    // org.jetbrains.androidx.lifecycle stopped publishing iosX64 artifacts after
    // 2.10.0-alpha07, and a payment app should not ship on an alpha dependency.
    //
    // iosX64 is the Intel iOS simulator: devices use iosArm64 and modern simulators use
    // iosSimulatorArm64, so this costs nothing except building on an Intel Mac. v1 does
    // not distribute iOS at all (§12.3), which makes the trade cheaper still. The
    // :payload and :qr modules keep iosX64 — they have no such dependency.
    //
    // To restore it, replace the lifecycle ViewModel with a hand-rolled state holder.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        // CD-05: GitHub Pages serves project pages from /<repo-name>/, so the bundle
        // must not assume it is at the domain root.
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }

    // Kotlin/JS (canvas) target, alongside wasmJs. The wasmJs backend is newer and some
    // browsers/automation setups have had trouble routing input to its canvas; js(IR)
    // uses the older, more battle-tested Compose-for-Web canvas renderer as a fallback
    // that can be deployed if wasmJs interactivity proves unreliable.
    js(IR) {
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }

    sourceSets {
        // Shared between the wasmJs and js(IR) browser targets — both platform entry
        // points (main.kt) talk to the DOM the same way.
        val webMain by creating { dependsOn(commonMain.get()) }
        wasmJsMain.get().dependsOn(webMain)
        val jsMain by getting { dependsOn(webMain) }

        commonMain.dependencies {
            implementation(projects.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "sg.qrstudio.app"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "sg.qrstudio.app"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint {
        // A payment app fails its build on a real lint error rather than shipping with
        // one merely reported; warnings are still visible in the HTML report without
        // blocking CI.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        htmlReport = true
        xmlReport = true
    }
}

compose.desktop {
    application {
        mainClass = "sg.qrstudio.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "QR Studio SG"
            packageVersion = "1.0.0"
        }
    }
}
