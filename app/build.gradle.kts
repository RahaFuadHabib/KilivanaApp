import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val canonicalBaseUrl = providers.gradleProperty("kilivana.apiBaseUrl").getOrElse("http://127.0.0.1:8081/api/v1/")
val apiUri = runCatching { URI(canonicalBaseUrl) }.getOrNull()
require(apiUri != null && apiUri.host != null && apiUri.rawPath == "/api/v1/" &&
    apiUri.rawUserInfo == null && apiUri.rawQuery == null && apiUri.rawFragment == null &&
    (apiUri.scheme == "https" || (apiUri.scheme == "http" && apiUri.host in setOf("127.0.0.1", "localhost", "10.0.2.2")))) {
    "API base URL must be HTTPS (or HTTP loopback for local development), ending in /api/v1/ without credentials, query or fragment"
}
val isolateDevelopmentBackend = providers.gradleProperty("kilivana.isolateDevelopmentBackend").orNull == "true"
val backendInstallKey = MessageDigest.getInstance("SHA-256")
    .digest(canonicalBaseUrl.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

android {
    namespace = "com.example.farmapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.farmapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$canonicalBaseUrl\""
        )

        buildConfigField("boolean", "USE_CANONICAL_BACKEND", (providers.gradleProperty("kilivana.canonicalBackend").orNull != "false").toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Explicit opt-in only. Demo builds have separate app storage from the real client.
            val mockAuth = providers.gradleProperty("kilivana.mockAuth").orNull == "true"
            buildConfigField("boolean", "USE_MOCK_AUTH", mockAuth.toString())
            if (mockAuth) applicationIdSuffix = ".demo"
            else if (isolateDevelopmentBackend) applicationIdSuffix = ".dev$backendInstallKey"
            else if (providers.gradleProperty("kilivana.demoInstall").orNull == "true") applicationIdSuffix = ".canonicaldemo"
        }
        release {
            buildConfigField("boolean", "USE_MOCK_AUTH", "false")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Preserve old demo sources on disk, but ship offline demo code only in the Review project.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude("**/com/example/farmapp/demo/**", "**/DemoFarmerShell.kt", "**/DemoModePresentationTest.kt")
}

dependencies {
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // 1. MVVM & ViewModel for Jetpack Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // 2. Retrofit & Gson (Communicating with Spring Boot REST API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 3. Kotlin Coroutines (Background async tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
