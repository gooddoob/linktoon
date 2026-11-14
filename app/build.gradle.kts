plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.linktoon.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linktoon.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "edition"
    productFlavors {
        create("personal") {
            dimension = "edition"
            buildConfigField("String", "START_URL", "\"https://gooddoob.github.io/linktoon/\"")
            buildConfigField("String", "OFFLINE_URL", "\"file:///android_asset/index.html\"")
        }
        create("clean") {
            dimension = "edition"
            buildConfigField("String", "START_URL", "\"https://gooddoob.github.io/linktoon/?clean=1\"")
            buildConfigField("String", "OFFLINE_URL", "\"file:///android_asset/index.html?clean=1\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}
