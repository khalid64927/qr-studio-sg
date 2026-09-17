import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

/**
 * The design system, reusable components, and screens for QR Studio SG — every
 * `@Composable` in the app lives here, split out from `:composeApp` so the platform
 * entry points (`MainActivity`, `main.kt`, `MainViewController`) stay a thin shell.
 *
 * A platform-specific look and feel (e.g. swapping Material 3 for a Cupertino-style
 * theme on iOS) is a change confined to this module — `:composeApp` only calls [App]
 * and never touches a design-system type directly.
 */
kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // Mirrors :composeApp's target list — see that module's build.gradle.kts for why
    // iosX64 is dropped (org.jetbrains.androidx.lifecycle has no iosX64 artifact past
    // 2.10.0-alpha07). No framework binary is declared here: this module is a library
    // consumed by :composeApp's iOS framework, not a framework in its own right.
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    js(IR) { browser() }

    sourceSets {
        // Mirrors :composeApp: the wasmJs and js(IR) actuals (image decoding, file
        // export, native file input) are identical DOM-based code, so they live here
        // once rather than duplicated per browser target.
        val webMain by creating { dependsOn(commonMain.get()) }
        wasmJsMain.get().dependsOn(webMain)
        val jsMain by getting { dependsOn(webMain) }

        val webTest by creating { dependsOn(commonTest.get()) }
        wasmJsTest.get().dependsOn(webTest)
        val jsTest by getting { dependsOn(webTest) }

        val iosMain by creating { dependsOn(commonMain.get()) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        commonMain.dependencies {
            implementation(projects.payload)
            implementation(projects.qr)

            // These leak into this module's public API (e.g. App(modifier: Modifier)),
            // so :composeApp needs them on its compile classpath transitively.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            api(compose.components.resources)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.filekit.core)
            implementation(libs.filekit.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        val desktopTest by getting
        desktopTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.zxing.core)
            implementation(libs.zxing.javase)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "sg.qrstudio.ui"
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
    buildFeatures { compose = true }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        htmlReport = true
        xmlReport = true
    }
}
