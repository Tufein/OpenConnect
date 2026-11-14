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
        versionCode = 30
        versionName = "0.30.0"
    }

    // Release signing. The key is never stored in this repository: a release build reads it from
    // OPENCONNECT_KEYSTORE (path), OPENCONNECT_KEYSTORE_PASSWORD and OPENCONNECT_KEY_ALIAS (see
    // scripts/signing-key.sh and docs/ANDROID.md). Without it (local builds, pull-request CI) the APK is signed
    // with the throw-away Android debug key, which is fine for trying it out but not for handing to anyone.
    val releaseKeystore = System.getenv("OPENCONNECT_KEYSTORE")?.takeIf { it.isNotBlank() }
    val releaseSigning = releaseKeystore?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = System.getenv("OPENCONNECT_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("OPENCONNECT_KEY_ALIAS") ?: "openconnect"
            keyPassword = System.getenv("OPENCONNECT_KEYSTORE_PASSWORD")
        }
    }
    if (releaseSigning == null && System.getenv("OPENCONNECT_REQUIRE_RELEASE_KEY") == "true") {
        throw GradleException("OPENCONNECT_REQUIRE_RELEASE_KEY is set but OPENCONNECT_KEYSTORE is not")
    }

    buildTypes {
        release {
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
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

    // The watch test files (and the activity fixtures) live with the core tests; the app tests read the same ones.
    sourceSets.getByName("test").resources.srcDir("../core/src/test/resources")

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
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    // The AI tests read the request bodies the app sends, to prove what leaves the phone.
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
