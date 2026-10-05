import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

// Base URL is property-driven per Human decision Q3 (ARCHITECTURE §14.2):
//   -Pspendwise.api.base.url / gradle.properties  →  $SPENDWISE_API_BASE_URL  →  placeholder default
val apiBaseUrl: String = providers.gradleProperty("spendwise.api.base.url")
    .orElse(providers.environmentVariable("SPENDWISE_API_BASE_URL"))
    .orElse("https://example.invalid/")
    .get()
    .let { url -> if (url.endsWith("/")) url else "$url/" }

android {
    namespace = "com.spendwise.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spendwise.android"
        minSdk = 26
        targetSdk = 36
        // M7: V1 surface is complete (S01–S25 shipped M0–M6). Release
        // versioning/signing ownership stays with THE HUMAN per Q4;
        // this is the internal/direct-install V1 build.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SPENDWISE_API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release signing is Human-owned (Q4): no signingConfig here.
            // M0 only proves release compilation of an unsigned artifact.
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Kotlin jvmTarget follows compileOptions.targetCompatibility (AGP 9 built-in
// Kotlin — there is no separate kotlin-android plugin to configure).

// detekt is wired warn-only per ARCHITECTURE §36: the task runs and reports,
// but cannot fail the build until the team ratchets the baseline.
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    ignoreFailures = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
