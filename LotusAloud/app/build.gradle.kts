plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.lss.lotusaloud"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lss.fishreader"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = file("lotusaloud.keystore")
            storePassword = "fishreader"
            keyAlias = "fishreader"
            keyPassword = "fishreader"
        }
        create("play") {
            storeFile = file("lotusaloud-play.keystore")
            storePassword = "lotusaloud-lss"
            keyAlias = "lotusaloud"
            keyPassword = "lotusaloud-lss"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "edition"
    productFlavors {
        create("personal") {
            dimension = "edition"
            applicationId = "com.lss.fishreader"
            targetSdk = 34
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "PLAY", "false")
            buildConfigField("String", "DEFAULT_VOICE", "\"phone:\"")
            buildConfigField("String", "SUPPORT_EMAIL", "\"\"")
            buildConfigField("String", "PRIVACY_URL", "\"\"")
            buildConfigField("String", "REPORT_WEBHOOK", "\"\"")
        }
        create("play") {
            dimension = "edition"
            applicationId = "com.lotusshield.lotusaloud"
            targetSdk = 35
            signingConfig = signingConfigs.getByName("play")
            buildConfigField("boolean", "PLAY", "true")
            buildConfigField("String", "DEFAULT_VOICE", "\"phone:\"")
            buildConfigField("String", "SUPPORT_EMAIL", "\"${project.findProperty("lotusSupportEmail") ?: ""}\"")
            buildConfigField("String", "PRIVACY_URL", "\"${project.findProperty("lotusPrivacyUrl") ?: ""}\"")
            buildConfigField("String", "REPORT_WEBHOOK", "\"${project.findProperty("lotusReportWebhook") ?: ""}\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")

    implementation("org.jsoup:jsoup:1.17.2")
}
