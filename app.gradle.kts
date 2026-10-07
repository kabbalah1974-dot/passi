plugins {
    id("com.android.application")
}

android {
    namespace = "it.passi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "it.passi.app"
        minSdk = 26
        targetSdk = 34
        versionCode = providers.gradleProperty("verCode").orNull?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    // Stessa firma a ogni costruzione: senza, Android rifiuta gli aggiornamenti ("firma in conflitto").
    signingConfigs {
        create("digiuno") {
            storeFile = file("digiuno.keystore")
            storePassword = "digiuno"
            keyAlias = "digiuno"
            keyPassword = "digiuno"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("digiuno")
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
