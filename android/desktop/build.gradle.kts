// Windows desktop app. It compiles the same screens and data code as the Android app
// (app/src/main/java, minus the Android-only files) with Compose for Desktop.
//
//   ./gradlew :desktop:run                       run on this computer
//   tools/windows/build_windows.sh               build the Windows installer (.exe)

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

val appVersion = "1.4"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude("**/MainActivity.kt", "**/platform/**")
    }
}

sourceSets.main {
    resources.srcDir("../app/src/main/assets")
}

dependencies {
    implementation(compose.desktop.common)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // Skia natives: Windows for the installer, this machine otherwise.
    if (findProperty("target") == "windows") {
        runtimeOnly(compose.desktop.windows_x64)
    } else {
        runtimeOnly(compose.desktop.currentOs)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.appsc.prep.desktop.MainKt"
        // Shrinks the libraries for the Windows download (./gradlew :desktop:proguardReleaseJars).
        buildTypes.release.proguard {
            version.set("7.6.1")
            obfuscate.set(false)
            configurationFiles.from(project.file("rules.pro"))
        }
    }
}

/** The app jar and the ProGuard-shrunk library jars, ready to drop next to a Windows Java runtime. */
tasks.register<Sync>("windowsApp") {
    group = "distribution"
    dependsOn("proguardReleaseJars")
    from(layout.buildDirectory.dir("compose/tmp/main-release/proguard"))
    into(layout.buildDirectory.dir("windows/app"))
}

tasks.jar {
    archiveBaseName.set("rocket-prep")
    archiveVersion.set(appVersion)
    manifest { attributes("Implementation-Version" to appVersion) }
}
