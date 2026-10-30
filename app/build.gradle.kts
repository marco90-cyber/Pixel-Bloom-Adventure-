plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.marco.pixelbloom"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.marco.pixelbloom"
        minSdk = 24
        targetSdk = 34
        // Numero di build di GitHub Actions: ogni versione è più nuova della precedente (aggiornamenti in app)
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "1.0.$run"
    }

    // Firma: chiave privata dai segreti di GitHub (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).
    // Serve una firma sempre uguale perché gli aggiornamenti in app si installino sopra la versione precedente.
    // Se i segreti non ci sono, si usa la chiave di debug.
    val appKeystore = System.getenv("APP_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (appKeystore != null) {
            create("private") {
                storeFile = appKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (appKeystore != null) signingConfigs.getByName("private") else signingConfigs.getByName("debug")
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
}
