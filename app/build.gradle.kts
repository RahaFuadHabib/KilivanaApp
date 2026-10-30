import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release has no implicit deployed endpoint. CI/developers must supply it explicitly.
val configuredBaseUrl = providers.gradleProperty("kilivana.apiBaseUrl")
    .orElse(providers.environmentVariable("KILIVANA_API_BASE_URL")).orNull
// Used only to configure non-release tasks; validateReleaseBackend blocks release without HTTPS configuration.
val canonicalBaseUrl = configuredBaseUrl ?: "http://127.0.0.1:8081/api/v1/"
// Machine-specific overrides apply only to debug builds.
val localDevelopmentProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.inputStream().use { load(it) }
}
val debugBaseUrl = providers.gradleProperty("kilivana.debug.apiBaseUrl")
    .orElse(providers.environmentVariable("KILIVANA_DEBUG_API_BASE_URL")).orNull
    ?: localDevelopmentProperties.getProperty("kilivana.debug.apiBaseUrl")
    ?: configuredBaseUrl ?: "http://10.0.2.2:8081/api/v1/"
val debugInstallSuffix = providers.gradleProperty("kilivana.debug.applicationIdSuffix").orNull
    ?: localDevelopmentProperties.getProperty("kilivana.debug.applicationIdSuffix")
require(debugInstallSuffix == null || Regex("\\.dev[0-9a-f]{16}").matches(debugInstallSuffix)) {
    "Debug install suffix must be .dev followed by sixteen lowercase hex digits"
}
listOf(canonicalBaseUrl, debugBaseUrl).forEach { baseUrl ->
    val apiUri = runCatching { URI(baseUrl) }.getOrNull()
    require(apiUri != null && apiUri.host != null && apiUri.rawPath == "/api/v1/" &&
        apiUri.rawUserInfo == null && apiUri.rawQuery == null && apiUri.rawFragment == null &&
        (apiUri.scheme == "https" || (apiUri.scheme == "http" && apiUri.host in setOf("127.0.0.1", "localhost", "10.0.2.2")))) {
        "API base URL must be HTTPS (or HTTP loopback for local development), ending in /api/v1/ without credentials, query or fragment"
    }
}
val isolateDevelopmentBackend = providers.gradleProperty("kilivana.isolateDevelopmentBackend").orNull == "true"
val backendInstallKey = MessageDigest.getInstance("SHA-256")
    .digest(debugBaseUrl.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

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
        // Set only after Daniel confirms the environment for this exact deployed backend. No secrets.
        val mpesaEnvironment = providers.gradleProperty("kilivana.mpesa.environment").orNull ?: "UNKNOWN"
        val mpesaConfirmedUrl = providers.gradleProperty("kilivana.mpesa.confirmedApiBaseUrl").orNull ?: ""
        require(mpesaEnvironment in setOf("UNKNOWN", "SANDBOX", "PRODUCTION"))
        require(mpesaConfirmedUrl.isEmpty() || mpesaConfirmedUrl in setOf(canonicalBaseUrl, debugBaseUrl))
        buildConfigField("String", "BUYER_MPESA_ENVIRONMENT", "\"$mpesaEnvironment\"")
        buildConfigField("String", "BUYER_MPESA_CONFIRMED_API_BASE_URL", "\"$mpesaConfirmedUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val safeAuthDiagnostics = providers.gradleProperty("kilivana.authDiagnostics").orNull == "true"
            buildConfigField("boolean", "ENABLE_AUTH_DIAGNOSTICS", safeAuthDiagnostics.toString())
            manifestPlaceholders["safeAuthDiagnosticsEnabled"] = safeAuthDiagnostics.toString()
            val cartStructureDiagnostic = providers.gradleProperty("kilivana.cartStructureDiagnostic").orNull == "true"
            buildConfigField("boolean", "ENABLE_CART_STRUCTURE_DIAGNOSTIC", cartStructureDiagnostic.toString())
            manifestPlaceholders["cartStructureDiagnosticEnabled"] = cartStructureDiagnostic.toString()
            buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
            // Explicit opt-in only. Demo builds have separate app storage from the real client.
            val mockAuth = providers.gradleProperty("kilivana.mockAuth").orNull == "true"
            buildConfigField("boolean", "USE_MOCK_AUTH", mockAuth.toString())
            if (mockAuth) applicationIdSuffix = ".demo"
            else if (debugInstallSuffix != null) applicationIdSuffix = debugInstallSuffix
            else if (isolateDevelopmentBackend) applicationIdSuffix = ".dev$backendInstallKey"
            else if (providers.gradleProperty("kilivana.demoInstall").orNull == "true") applicationIdSuffix = ".canonicaldemo"
            else applicationIdSuffix = ".debug"
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

// Fail before release build/test/package work instead of shipping a loopback or temporary default.
val validateReleaseBackend = tasks.register("validateReleaseBackend") {
    doLast {
        val endpoint = configuredBaseUrl?.let { URI(it) }
        require(endpoint != null && endpoint.scheme == "https" &&
            endpoint.host !in setOf("localhost", "127.0.0.1", "10.0.2.2", "::1", "[::1]")) {
            "Release requires an explicit HTTPS backend: -Pkilivana.apiBaseUrl=<url> or KILIVANA_API_BASE_URL. No production endpoint is assumed."
        }
    }
}
tasks.configureEach {
    if (name.contains("Release") && name != "validateReleaseBackend") dependsOn(validateReleaseBackend)
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
    implementation("androidx.navigation:navigation-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // 2. Retrofit & Gson (Communicating with Spring Boot REST API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 3. Kotlin Coroutines (Background async tasks)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
