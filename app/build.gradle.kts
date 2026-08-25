plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.seky443.librething"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.seky443.librething"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        // The go-librespot binaries are shipped per-ABI as jniLibs named
        // libgolibrespot.so purely so the packager extracts them next to the
        // real native libs with the execute bit set; they are plain ELF
        // executables, not shared objects, so they must not be compressed
        // or the runtime dlopen validation some OEMs apply would reject them.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // One APK per ABI (each a fraction of the universal APK's size, since it skips the
    // other three ABIs' copy of libgolibrespot.so) plus a universal fallback that bundles
    // all four -- the right pick when you don't know a target device's ABI up front, or
    // it's for a store/CI pipeline that only wants a single artifact. No per-ABI versionCode
    // remapping: that only matters for Play Store uploads, not direct APK distribution.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

// AGP's bundled Kotlin compiler in this environment can only read metadata up to Kotlin
// 2.3.0, but several transitive deps (coroutines/serialization/etc.) pull a
// newer kotlin-stdlib by default. Pin it so the compiler can actually parse it.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}