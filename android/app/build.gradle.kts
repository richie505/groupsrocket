plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.appsc.prep"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.groupsrocket.rocketprep"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    signingConfigs {
        // Fixed key kept in the repo so every build can update the installed app in place.
        create("app") {
            storeFile = rootProject.file("keystore/rocket-prep.jks")
            storePassword = "rocketprep"
            keyAlias = "rocket-prep"
            keyPassword = "rocketprep"
        }
    }

    // Two apps from one code base: the ROCKET notes, and the revision points built from the MCQs
    // (scripts/build_revision.py).
    // They install side by side, each with its own progress.
    flavorDimensions += "edition"
    productFlavors {
        create("notes") {
            dimension = "edition"
            resValue("string", "app_name", "Rocket Prep")
        }
        create("revise") {
            dimension = "edition"
            applicationIdSuffix = ".revise"
            resValue("string", "app_name", "Rocket Revision")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("app")
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
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // ./gradlew testNotesDebugUnitTest --tests '*SpeechAuditTest' -Pspeech.audit=/path/out.tsv dumps read-aloud text
        unitTests.all { test ->
            project.findProperty("speech.audit")?.let { test.systemProperty("speech.audit", it) }
            test.systemProperty("prep.strict", "true") // a failing read-aloud rule fails the tests (skipped on phones)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media:media:1.7.0") // MediaStyle notification for read-aloud
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.browser:browser:1.8.0") // Google in a Chrome tab (signed in)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Screenshot tests (JVM, no device needed): ./gradlew recordRoborazziNotesDebug
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
