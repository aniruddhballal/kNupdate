import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.knupdate.build.AppVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val versionFile = rootProject.file("version.properties").toPath()
val buildsAppArtifact = gradle.startParameter.taskNames.any(AppVersion::buildsAppArtifact) && !gradle.startParameter.isDryRun
// Always reserve before Android config captures metadata. Sync/lint/tests only read.
// This build script intentionally runs without configuration-cache reuse: every APK needs a reservation.
if (buildsAppArtifact && gradle.startParameter.isConfigurationCacheRequested) {
    throw GradleException("Automatic APK versioning requires --no-configuration-cache for artifact builds.")
}
val appVersion = if (buildsAppArtifact) {
    AppVersion.reserve(versionFile, providers.gradleProperty("versionBump").getOrElse("patch"))
} else {
    AppVersion.read(versionFile)
}
if (buildsAppArtifact) logger.lifecycle("kNupdate build version: ${appVersion.name} (code ${appVersion.code})")

android {
    namespace = "com.knupdate.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.knupdate.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.code
        versionName = appVersion.name
        val relayUrl = providers.gradleProperty("relayUrl").orElse(providers.environmentVariable("KNUPDATE_RELAY_URL"))
            .orElse("https://knupdate-relay.aniruddhballaldeeksha.workers.dev").get()
        buildConfigField("String", "RELAY_URL", "\"${relayUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Optimized installable build for personal testing. Same local signing key as debug
        // so upgrades preserve data; store distribution needs a private production key.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.osmdroid)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.14.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation("androidx.tracing:tracing:1.0.0")
}
