plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

val releaseKeystore = rootProject.file(
    ".signing/appopsnext-release.keystore",
)
val releaseStorePassword =
    providers.environmentVariable("APPOPSNEXT_STORE_PASSWORD")
val releaseKeyPassword =
    providers.environmentVariable("APPOPSNEXT_KEY_PASSWORD")
val releaseSigningConfigured =
    releaseKeystore.isFile &&
        releaseStorePassword.isPresent &&
        releaseKeyPassword.isPresent

android {
    namespace = "dev.izumi.appopsnext"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.izumi.appopsnext"
        minSdk = 35
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = releaseStorePassword.orNull
            keyAlias = "appopsnext"
            keyPassword = releaseKeyPassword.orNull
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        aidl = true
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.configureEach {
    if (name == "validateSigningRelease") {
        doFirst {
            if (!releaseSigningConfigured) {
                throw GradleException(
                    "Release signing requires " +
                        ".signing/appopsnext-release.keystore, " +
                        "APPOPSNEXT_STORE_PASSWORD, and " +
                        "APPOPSNEXT_KEY_PASSWORD.",
                )
            }
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit4)

    debugImplementation(libs.compose.ui.tooling)
}
