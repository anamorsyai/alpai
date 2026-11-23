import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Fetches a statically-linked `proot` build for each supported ABI at Gradle-configuration
// time and stages it under jniLibs (renamed to lib*.so — Android's installer extracts and
// marks files under nativeLibraryDir executable regardless of their actual content, which is
// the standard, documented way to run a binary you didn't link into the app; a file written to
// storage at runtime instead would be blocked by W^X on API 29+). See scripts/fetch_proot.py.
val generatedProotJni = layout.buildDirectory.dir("generated/proot-jni")

val fetchProot = tasks.register<Exec>("fetchProot") {
    inputs.file(rootProject.projectDir.resolve("scripts/fetch_proot.py"))
    outputs.dir(generatedProotJni)
    commandLine(
        "python3",
        rootProject.projectDir.resolve("scripts/fetch_proot.py").absolutePath,
        "--output-dir",
        generatedProotJni.get().asFile.absolutePath,
    )
}

android {
    namespace = "com.anamorsyai.alpai.terminal"
    compileSdk = 34
    ndkVersion = "26.3.11579264"

    defaultConfig {
        minSdk = 26

        ndk {
            // Keep in sync with fetch_proot.py's ANDROID_ABI_TO_TERMUX_ARCH — no point building
            // (or shipping) the native pty_bridge helper for an ABI proot isn't available for.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    sourceSets.getByName("main") {
        jniLibs.srcDir(generatedProotJni)
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // proot (+ the pty-bridge helper built via CMake below) are executables shipped under
    // jniLibs, not real shared libraries — legacy packaging is what extracts them as real,
    // executable files at install time instead of leaving them zipped inside the APK.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    afterEvaluate {
        tasks.named("preBuild") {
            dependsOn(fetchProot)
        }
    }

    testOptions {
        // The emulator logs through android.util.Log; plain JVM unit tests get no-op stubs.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
