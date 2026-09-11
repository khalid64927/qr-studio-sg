import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

// FR-201: one encoder for all four targets. Like :payload this module is pure Kotlin
// with no UI dependency, so the module matrix can be tested without a renderer.
kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    js(IR) { browser() }

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
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
