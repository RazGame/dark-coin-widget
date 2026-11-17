plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.darkside.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darkside.widget"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Strip out unused language strings to keep APK footprint minimal
        resourceConfigurations += listOf("ru")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // Allows direct testing of release builds
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Google Code Scanner: Zero bundled ML model footprint!
    // Powered dynamically by Google Play Services already present on the device.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Lightweight HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.register("copyReleaseApkToZ") {
    doLast {
        val zDrive = file("Z:/")
        if (zDrive.exists() && zDrive.isDirectory) {
            val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
            if (releaseApk.exists()) {
                releaseApk.copyTo(file("Z:/Darkside-release.apk"), overwrite = true)
                releaseApk.copyTo(file("Z:/app-release.apk"), overwrite = true)
                println("Copied APK to Z:/ successfully")
            }
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleRelease")?.finalizedBy("copyReleaseApkToZ")
}
