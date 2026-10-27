plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub numbers each automatic build, so every new APK installs as an update.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.lss.fishreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lss.fishreader"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // A fixed signing key (in this folder) so new versions install over old ones.
    signingConfigs {
        create("release") {
            storeFile = file("fishreader.keystore")
            storePassword = "fishreader"
            keyAlias = "fishreader"
            keyPassword = "fishreader"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
