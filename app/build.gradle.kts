import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Personal signing material lives outside the repository, in the user's Android directory.
val localSigningFile = file("${System.getProperty("user.home")}/.android/dark-coin-widget/signing.properties")
val localSigning = Properties().apply {
    if (localSigningFile.isFile) localSigningFile.inputStream().use { load(it) }
}
fun signingValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull ?: localSigning.getProperty(propertyName)

val releaseKeystore = signingValue("DARK_COIN_KEYSTORE", "storeFile")
val releaseStorePassword = signingValue("DARK_COIN_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("DARK_COIN_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("DARK_COIN_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
require(hasReleaseSigning || listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it == null }) {
    "Provide a complete release signing configuration"
}

android {
    namespace = "com.darkcoin.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darkcoin.widget"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.0.0"

        // Strip out unused language strings to keep APK footprint minimal
        resourceConfigurations += listOf("ru")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("production") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("production") else null
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
    // Already present transitively; explicitly used for retaining the scanner operation only.
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.6.2")
    implementation("com.google.android.material:material:1.14.0")

    // Google Code Scanner: Zero bundled ML model footprint!
    // Powered dynamically by Google Play Services already present on the device.
    // ScannerDelegateActivity adapts this SDK's result handling; recheck it on upgrades.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Lightweight HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(hasReleaseSigning) {
            "Release signing is required. Configure DARK_COIN_* signing variables or ~/.android/dark-coin-widget/signing.properties"
        }
    }
}
