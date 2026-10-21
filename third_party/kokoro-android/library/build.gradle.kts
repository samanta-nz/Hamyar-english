plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.ffmpegkit.kokoro"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-proguard-rules.pro")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DUSE_ASYNC=OFF",
                    "-DUSE_MBROLA=OFF",
                    "-DUSE_LIBSONIC=OFF",
                    "-DUSE_LIBPCAUDIO=OFF",
                    "-DUSE_KLATT=OFF",
                    "-DUSE_SPEECHPLAYER=OFF",
                    "-DESPEAK_BUILD_TESTS=OFF",
                    "-DBUILD_SHARED_LIBS=ON",
                )
                cppFlags += "-O3"
                targets += "kokoro_jni"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes { release { isMinifyEnabled = false } }
    packaging { jniLibs { useLegacyPackaging = false } }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    api(libs.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
}
