plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Allow OpenCV path from local.properties (property `opencv.dir`) or environment.
val opencvDir: String? = (project.findProperty("opencv.dir") as? String)
    ?: System.getenv("OPENCV_SDK")
    ?: System.getenv("OpenCV_DIR")

android {
    namespace = "com.vintagecam.imageprocessor"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }

        externalNativeBuild {
            cmake {
                val cmakeArgs = mutableListOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
                if (!opencvDir.isNullOrBlank()) {
                    cmakeArgs += "-DOpenCV_DIR=$opencvDir"
                }
                arguments += cmakeArgs
                cppFlags += listOf("-std=c++17", "-O3", "-fexceptions", "-frtti")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    if (!opencvDir.isNullOrBlank()) {
        externalNativeBuild {
            cmake {
                path = file("CMakeLists.txt")
                version = "3.22.1"
            }
        }
    } else {
        println("OpenCV not configured: native image-processor build disabled. Set project property 'opencv.dir' or environment OPENCV_SDK/OpenCV_DIR to enable native build.")
    }

    // OpenCV path is injected into CMake arguments earlier via `opencvDir` variable.

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(
                // OpenCV prebuilt .so libraries go here
                "src/main/jniLibs",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
