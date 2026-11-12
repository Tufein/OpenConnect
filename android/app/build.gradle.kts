plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "nl.tufein.openconnect"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.tufein.openconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    // Sideload signing. The key below is committed on purpose and is NOT a secret: it only gives every build the
    // same signature, so a new APK installs over the old one and keeps the data on the phone. Anyone could
    // build an APK with this key, so only install APKs you got from this repository's releases. A real
    // distribution key is supplied through the environment instead (see docs/ANDROID.md).
    signingConfigs {
        create("sideload") {
            val external = System.getenv("OPENCONNECT_KEYSTORE")
            storeFile = file(external ?: "openconnect-sideload.jks")
            storePassword = System.getenv("OPENCONNECT_KEYSTORE_PASSWORD") ?: "openconnect"
            keyAlias = System.getenv("OPENCONNECT_KEY_ALIAS") ?: "openconnect"
            keyPassword = System.getenv("OPENCONNECT_KEY_PASSWORD") ?: "openconnect"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            signingConfig = signingConfigs.getByName("sideload")
            // Kept unminified for the first releases: it is a debuggable-by-reading APK and R8 rules
            // for every library are one more thing that cannot be exercised without a device.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-ktx:1.15.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
