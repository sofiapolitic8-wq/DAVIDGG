plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Optional release keystore (see README). Empty / missing => the debug key is used so forks and CI
// always produce an installable APK.
val releaseKeystore: File? = System.getenv("RSHMAP_KEYSTORE")
    ?.takeIf { it.isNotBlank() }
    ?.let { File(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "app.rshmap"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.rshmap"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "hi")
    }

    signingConfigs {
        // Release signing is read from environment variables. Without them the debug
        // keystore is used so CI and forks always produce an installable APK.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RSHMAP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RSHMAP_KEY_ALIAS")
                keyPassword = System.getenv("RSHMAP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 off on purpose: guarantees a first-try, error-free build (no missing-class / keep-rule surprises).
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        viewBinding = true
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
