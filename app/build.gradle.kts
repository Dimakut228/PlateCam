plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dimakut228.platecam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dimakut228.platecam"
        minSdk = 24
        targetSdk = 34
        versionCode = findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
        versionName = findProperty("versionName")?.toString() ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("stable") {
            storeFile = rootProject.file(
                System.getenv("PLATECAM_KEYSTORE_FILE")
                    ?: (findProperty("platecam.storeFile") as String?)
                    ?: "signing/platecam.jks"
            )
            storePassword = System.getenv("PLATECAM_STORE_PASSWORD")
                ?: (findProperty("platecam.storePassword") as String?)
                ?: ""
            keyAlias = System.getenv("PLATECAM_KEY_ALIAS")
                ?: (findProperty("platecam.keyAlias") as String?)
                ?: "znakcam"
            keyPassword = System.getenv("PLATECAM_KEY_PASSWORD")
                ?: (findProperty("platecam.keyPassword") as String?)
                ?: ""
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    androidResources {
        noCompress += listOf("onnx", "txt")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.preference:preference:1.2.1")
    testImplementation("junit:junit:4.13.2")
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.18.0")
}
