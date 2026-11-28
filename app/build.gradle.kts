import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stormgbca.emulator"
    compileSdk = 36
    ndkVersion = "28.0.13004108"

    val stormSigningProps = Properties().apply {
        val centralProps = file("F:/STORM ALL PROJECTS/.storm-secrets/storm_signing.properties")
        val envProps = System.getenv("STORM_SIGNING_PROPS")?.let { file(it) }
        val activePropsFile = listOfNotNull(envProps, centralProps).firstOrNull { it.exists() }
        if (activePropsFile != null) {
            activePropsFile.inputStream().use { load(it) }
        }
    }
    val localProps = gradleLocalProperties(rootDir, providers)
    val unifiedCertFile = (
        System.getenv("STORM_CERT_FILE")
            ?: localProps.getProperty("STORM_CERT_FILE")
            ?: stormSigningProps.getProperty("STORM_CERT_FILE")
        )?.let { file(it) }?.takeIf { it.exists() }
    val unifiedCertPassword = System.getenv("STORM_CERT_PASSWORD")
        ?: localProps.getProperty("STORM_CERT_PASSWORD")
        ?: stormSigningProps.getProperty("STORM_CERT_PASSWORD")
        ?: ""
    val unifiedKeyAlias = System.getenv("STORM_KEY_ALIAS")
        ?: localProps.getProperty("STORM_KEY_ALIAS")
        ?: stormSigningProps.getProperty("STORM_KEY_ALIAS")
        ?: ""
    val unifiedKeyPassword = System.getenv("STORM_KEY_PASSWORD")
        ?: localProps.getProperty("STORM_KEY_PASSWORD")
        ?: stormSigningProps.getProperty("STORM_KEY_PASSWORD")
        ?: ""

    signingConfigs {
        create("release") {
            if (unifiedCertFile != null) {
                storeFile = unifiedCertFile
                storePassword = unifiedCertPassword
                keyAlias = unifiedKeyAlias
                keyPassword = unifiedKeyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        getByName("debug") {
            if (unifiedCertFile != null) {
                storeFile = unifiedCertFile
                storePassword = unifiedCertPassword
                keyAlias = unifiedKeyAlias
                keyPassword = unifiedKeyPassword
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.stormgbca.emulator"
        minSdk = 26
        targetSdk = 36
        versionCode = 10800
        versionName = "1.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DSTORM_GBCA_BUILD_TESTS=OFF",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DCMAKE_BUILD_PARALLEL_LEVEL=4",
                )
                targets("storm_gbca_core")
            }
        }
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
            externalNativeBuild {
                cmake {
                    cFlags("-O3", "-ffunction-sections", "-fdata-sections")
                    cppFlags("-O3", "-fvisibility-inlines-hidden", "-ffunction-sections", "-fdata-sections")
                    arguments("-DCMAKE_SHARED_LINKER_FLAGS_RELEASE=-Wl,-z,max-page-size=16384,-Wl,-O3,--gc-sections")
                }
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../core/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
        }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.splashscreen)
    implementation(libs.android.material)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil)
    implementation(libs.kotlin.coroutines)
    implementation(libs.kotlin.serialization)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.markwon)
}

tasks.register("copyNamedReleaseApk") {
    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val defaultApk = File(releaseDir, "app-release.apk")
        val targetApk = File(releaseDir, "STORM_GBCA_1.8.0.apk")
        val releasesDir = rootDir.resolve("releases").apply { mkdirs() }
        val persistentApk = File(releasesDir, "STORM_GBCA_1.8.0.apk")
        val filesDir = rootDir.resolve("Files")
        if (defaultApk.exists()) {
            defaultApk.copyTo(targetApk, overwrite = true)
            defaultApk.copyTo(persistentApk, overwrite = true)
            if (filesDir.isDirectory) {
                defaultApk.copyTo(File(filesDir, "STORM_GBCA_1.8.0.apk"), overwrite = true)
            }
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("copyNamedReleaseApk")
}
