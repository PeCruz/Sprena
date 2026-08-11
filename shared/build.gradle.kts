import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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
            // Coroutines & Serialization
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Koin DI (módulos shared exportam definições de módulo)
            implementation(libs.koin.core)

            // Logging (KMP)
            implementation(libs.napier)
        }

        androidMain.dependencies {
            // Firebase (project.dependencies.platform() is required for BOM in KMP)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.crashlytics)
            implementation(libs.firebase.auth)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.tink.android)
            // Koin Android — para androidContext() nos modules Android
            implementation(libs.koin.android)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        // MockK é JVM-only — não usar em commonTest (quebra KMP)
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.mockk)
            }
        }
    }
}

android {
    namespace = "br.com.sprena.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // As exceções do Firebase têm inicializador estático que toca o framework
            // (android.util.SparseArray). Sem isto, só instanciá-las num unit test já
            // estoura "Method ... not mocked". Ver AuthErrorMapperTest.
            isReturnDefaultValues = true
        }
    }
}
