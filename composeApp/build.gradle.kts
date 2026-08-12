import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Future: iOS targets
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Lifecycle & ViewModel
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            // Navigation
            implementation(libs.navigation.compose)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Shared module
            implementation(projects.shared)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)

            // Firebase (project.dependencies.platform() is required for BOM in KMP)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.auth)

            // F1.4b: App Check. O provider de debug entra só na variante debug
            // (ver bloco `dependencies` no fim do arquivo) para que a classe do
            // provider inseguro nem exista no APK de release.
            implementation(libs.firebase.appcheck.playintegrity)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

// F1.5: a política de privacidade é um arquivo em composeResources/files.
// Pacote fixado explicitamente para o import não depender de heurística do plugin.
compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.sprena.resources"
    generateResClass = always
}

android {
    namespace = "br.com.sprena"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.sprena"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // F1.1: assina com a chave debug para `assembleRelease` rodar local.
            // Substituir por signingConfig real ao publicar (F6/Pós-MVP).
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {
    // F1.4b: DebugAppCheckProviderFactory só na variante debug. Isso é o que
    // torna `src/androidDebug` vs `src/androidRelease` uma garantia de compilação:
    // o release não tem como referenciar o provider de debug.
    debugImplementation(libs.firebase.appcheck.debug)
}
