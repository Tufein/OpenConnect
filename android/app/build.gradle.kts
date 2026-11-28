plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "nl.tufein.openconnect"
    // Compose 1.13 (BOM 2026.11.01) asks to be compiled against Android 37.1 or newer. Only the compile SDK moves:
    // targetSdk (runtime behaviour) and minSdk (which phones can install it) stay where they are.
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "nl.tufein.openconnect"
        minSdk = 28
        targetSdk = 36
        versionCode = 106
        versionName = "1.13.13"
    }

    // Two channels from one code base. The GitHub build keeps the in-app updater (the install permission and the
    // FileProvider live in src/github); the store build leaves both out and hides the updater in the app.
    flavorDimensions += "channel"
    productFlavors {
        create("github") {
            dimension = "channel"
            buildConfigField("boolean", "UPDATER_ENABLED", "true")
        }
        create("store") {
            dimension = "channel"
            buildConfigField("boolean", "UPDATER_ENABLED", "false")
        }
    }

    // The language picker switches the app's own strings, so every language has to stay in the one bundle.
    bundle {
        language {
            enableSplit = false
        }
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
            // Since v0.58.0 the release APK is shrunk by R8 (unused code and resources are removed: 30 MB became a
            // fraction of that). Not obfuscated and not optimised (proguard-rules.pro), so a stack trace still reads and
            // the only thing that can go wrong is a class or resource that was only reached by reflection. The emulator
            // smoke test (android/scripts/emulator_smoke.py, in CI and as a gate in release.yml) starts this very APK on
            // a real Android and walks the app. -Popenconnect.minify=false builds the plain APK for comparing.
            val shrink = providers.gradleProperty("openconnect.minify").orNull != "false"
            isMinifyEnabled = shrink
            isShrinkResources = shrink
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.health.connect:connect-client:1.2.0-alpha06")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AGP 9 builds Kotlin itself, so the kotlin("test") capability helper of the Kotlin plugin is not applied here;
    // name the JUnit 4 flavour so kotlin.test.Test resolves.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.work:work-testing:2.12.0")
    // The AI tests read the request bodies the app sends, to prove what leaves the phone.
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
tasks.withType<Test> {
    // Robolectric keeps its dependency cache under the user home; some environments have none set.
    System.getenv("HOME")?.takeIf { it.isNotBlank() }?.let { systemProperty("user.home", it) }
    // A failed test prints its whole exception (not just the top frame), so a flaky CI run can be explained afterwards.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
