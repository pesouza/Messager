plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pesouza.messager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pesouza.messager"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Chave fixa (incluída no repositório) para que cada nova versão
        // possa ser instalada por cima da anterior sem desinstalar.
        create("shared") {
            storeFile = file("messager.keystore")
            storePassword = "messager"
            keyAlias = "messager"
            keyPassword = "messager"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
