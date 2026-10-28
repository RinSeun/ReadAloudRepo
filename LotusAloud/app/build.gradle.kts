plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub numbers each automatic build, so every new APK installs as an update.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.lss.lotusaloud"
    compileSdk = 35

    defaultConfig {
        // Keep this ID forever: it is how Android knows Lotus Aloud updates the app you already have.
        applicationId = "com.lss.fishreader"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // A fixed signing key (in this folder) so new versions install over old ones.
    // Its password/alias are baked into the key file - leave them as they are.
    signingConfigs {
        create("release") {
            storeFile = file("lotusaloud.keystore")
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

dependencies {
    // Finds text in screenshots and photos, on the phone (offline, free)
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
