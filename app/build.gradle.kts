plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "gt.toca.social.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "gt.toca.social.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // Room (SQLite ORM for all-time records persistence)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // OkHttp (HTTP client for polling corporate API)
    implementation(libs.okhttp)

    // Gson (JSON serialization for JS bridge)
    implementation(libs.gson)

    // Kotlin Coroutines (async polling worker)
    implementation(libs.coroutines.android)

    // Lifecycle (coroutine scope management)
    implementation(libs.lifecycle.runtime)
}
