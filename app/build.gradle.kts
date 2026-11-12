
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

// Apply the plugins needed to build the Android application.
plugins {
    // Enables Android application builds (APK/AAB).
    alias(libs.plugins.android.application)

    // Enables Kotlin support for Android.
    alias(libs.plugins.kotlin.android)

    // Enables the Kotlin compiler plugin for Jetpack Compose.
    alias(libs.plugins.kotlin.compose)
}

// ======================================================
// 1. BACKEND API CONFIGURATION
// ======================================================

// Read the API URL from a Gradle property first.
// If unavailable, try the environment variable.
// A release backend is not automatically assumed.
val configuredBaseUrl = providers.gradleProperty("kilivana.apiBaseUrl")
    .orElse(providers.environmentVariable("KILIVANA_API_BASE_URL")).orNull

// Fallback used when no explicit canonical URL is provided.
// This is a local development address, not a production backend.
val canonicalBaseUrl =
    configuredBaseUrl ?: "http://127.0.0.1:8081/api/v1/"

// Load machine-specific settings from local.properties.
// These settings are used for debug configuration.
val localDevelopmentProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")

    // Only read the file if it exists.
    if (localFile.isFile) {
        localFile.inputStream().use { load(it) }
    }
}

// Determine the debug backend URL.
// Priority:
// 1. Gradle property
// 2. Environment variable
// 3. local.properties
// 4. Canonical URL, if configured
// 5. Android emulator local development URL
val debugBaseUrl = providers.gradleProperty("kilivana.debug.apiBaseUrl")
    .orElse(providers.environmentVariable("KILIVANA_DEBUG_API_BASE_URL")).orNull
    ?: localDevelopmentProperties.getProperty("kilivana.debug.apiBaseUrl")
    ?: configuredBaseUrl
    ?: "http://10.0.2.2:8081/api/v1/"

// Optional application ID suffix for debug installations.
// This allows development builds to have a different
// package ID from release builds.
val debugInstallSuffix =
    providers.gradleProperty("kilivana.debug.applicationIdSuffix").orNull
        ?: localDevelopmentProperties.getProperty(
            "kilivana.debug.applicationIdSuffix"
        )

// Ensure that any explicit debug suffix follows the format:
// .dev followed by exactly 16 lowercase hexadecimal digits.
require(
    debugInstallSuffix == null ||
            Regex("\\.dev[0-9a-f]{16}").matches(debugInstallSuffix)
) {
    "Debug install suffix must be .dev followed by sixteen lowercase hex digits"
}

// Validate both canonical and debug API URLs.
listOf(canonicalBaseUrl, debugBaseUrl).forEach { baseUrl ->

    // Try to parse the URL without crashing immediately
    // if the string is malformed.
    val apiUri = runCatching { URI(baseUrl) }.getOrNull()

    // Require:
    // - A valid host
    // - The exact path /api/v1/
    // - No embedded credentials, query, or fragment
    // - HTTPS, except HTTP for approved local hosts
    require(
        apiUri != null &&
                apiUri.host != null &&
                apiUri.rawPath == "/api/v1/" &&
                apiUri.rawUserInfo == null &&
                apiUri.rawQuery == null &&
                apiUri.rawFragment == null &&
                (
                        apiUri.scheme == "https" ||
                                (
                                        apiUri.scheme == "http" &&
                                                apiUri.host in setOf(
                                            "127.0.0.1",
                                            "localhost",
                                            "10.0.2.2"
                                        )
                                        )
                        )
    ) {
        "API base URL must be HTTPS (or HTTP loopback for local development), ending in /api/v1/ without credentials, query or fragment"
    }
}

// Check whether development backend isolation is enabled.
val isolateDevelopmentBackend =
    providers.gradleProperty("kilivana.isolateDevelopmentBackend").orNull == "true"

// Generate a short identifier from the debug backend URL.
// SHA-256 produces a hash; take(8) keeps the first eight bytes.
// Each byte becomes two hexadecimal characters,
// resulting in a 16-character backend identifier.
val backendInstallKey = MessageDigest.getInstance("SHA-256")
    .digest(debugBaseUrl.toByteArray(Charsets.UTF_8))
    .take(8)
    .joinToString("") { "%02x".format(it) }

// ======================================================
// 2. ANDROID APPLICATION CONFIGURATION
// ======================================================

android {

    // Namespace used for generated Android code such as R
    // and BuildConfig, and for resolving relative class names.
    namespace = "com.example.farmapp"

    // Android API level used to compile the application.
    compileSdk = 36

    defaultConfig {
        buildConfigField("boolean", "FARMER_TRANSACTIONS_ENABLED", (providers.gradleProperty("kilivana.farmerTransactions").orNull == "true").toString())

        // Base application ID used to identify the installed app.
        applicationId = "com.example.farmapp"

        // Minimum Android API level supported by the app.
        minSdk = 26

        // Android API level whose behavior the app targets.
        targetSdk = 36

        // Internal application version number.
        versionCode = 1

        // User-visible application version.
        versionName = "1.0"

        // Generate BuildConfig.API_BASE_URL as a String.
        // The value can be accessed from Kotlin application code.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"$canonicalBaseUrl\""
        )

        // Enable the canonical backend unless explicitly disabled.
        buildConfigField(
            "boolean",
            "USE_CANONICAL_BACKEND",
            (
                    providers.gradleProperty("kilivana.canonicalBackend")
                        .orNull != "false"
                    ).toString()
        )

        // M-Pesa environment must be explicitly confirmed
        // before enabling payment behavior that depends on it.
        // These values are configuration markers, not credentials.
        val mpesaEnvironment =
            providers.gradleProperty("kilivana.mpesa.environment")
                .orNull ?: localDevelopmentProperties.getProperty("kilivana.mpesa.environment") ?: "UNKNOWN"

        val mpesaConfirmedUrl =
            providers.gradleProperty("kilivana.mpesa.confirmedApiBaseUrl")
                .orNull ?: localDevelopmentProperties.getProperty("kilivana.mpesa.confirmedApiBaseUrl") ?: ""

        // Only allow recognized M-Pesa environment values.
        require(
            mpesaEnvironment in setOf(
                "UNKNOWN",
                "SANDBOX",
                "PRODUCTION"
            )
        )

        // If a confirmed URL is provided, it must match
        // the configured canonical or debug backend URL.
        require(
            mpesaConfirmedUrl.isEmpty() ||
                    mpesaConfirmedUrl in setOf(
                canonicalBaseUrl,
                debugBaseUrl
            )
        )

        // Expose M-Pesa environment configuration to Kotlin code.
        buildConfigField(
            "String",
            "BUYER_MPESA_ENVIRONMENT",
            "\"$mpesaEnvironment\""
        )

        buildConfigField(
            "String",
            "BUYER_MPESA_CONFIRMED_API_BASE_URL",
            "\"$mpesaConfirmedUrl\""
        )

        // Test runner used for Android instrumented tests.
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // ==================================================
    // 3. DEBUG AND RELEASE BUILD TYPES
    // ==================================================

    buildTypes {

        debug {

            // Enable authentication diagnostics only when
            // the corresponding Gradle property is true.
            val safeAuthDiagnostics =
                providers.gradleProperty("kilivana.authDiagnostics")
                    .orNull == "true"

            buildConfigField(
                "boolean",
                "ENABLE_AUTH_DIAGNOSTICS",
                safeAuthDiagnostics.toString()
            )

            // Supply a value for AndroidManifest placeholders.
            manifestPlaceholders["safeAuthDiagnosticsEnabled"] =
                safeAuthDiagnostics.toString()

            // Optional cart structure diagnostics.
            val cartStructureDiagnostic =
                providers.gradleProperty("kilivana.cartStructureDiagnostic")
                    .orNull == "true"

            buildConfigField(
                "boolean",
                "ENABLE_CART_STRUCTURE_DIAGNOSTIC",
                cartStructureDiagnostic.toString()
            )

            manifestPlaceholders["cartStructureDiagnosticEnabled"] =
                cartStructureDiagnostic.toString()

            // Override the default API URL for debug builds.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$debugBaseUrl\""
            )

            // Enable mock authentication only when requested.
            // Demo builds use separate application storage.
            val mockAuth =
                providers.gradleProperty("kilivana.mockAuth")
                    .orNull == "true"

            buildConfigField(
                "boolean",
                "USE_MOCK_AUTH",
                mockAuth.toString()
            )

            // Choose the debug application ID suffix.
            // Only the first matching condition is used.
            if (mockAuth) {
                applicationIdSuffix = ".demo"
            } else if (debugInstallSuffix != null) {
                applicationIdSuffix = debugInstallSuffix
            } else if (isolateDevelopmentBackend) {
                applicationIdSuffix = ".dev$backendInstallKey"
            } else if (
                providers.gradleProperty("kilivana.demoInstall")
                    .orNull == "true"
            ) {
                applicationIdSuffix = ".canonicaldemo"
            } else {
                applicationIdSuffix = ".debug"
            }
        }

        release {

            // Never use mock authentication in release builds.
            buildConfigField(
                "boolean",
                "USE_MOCK_AUTH",
                "false"
            )

            // Disable code shrinking and obfuscation.
            isMinifyEnabled = false

            // Specify ProGuard/R8 rules for use when applicable.
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    // ==================================================
    // 4. JAVA AND KOTLIN COMPILER SETTINGS
    // ==================================================

    compileOptions {

        // Compile Java source using Java 11 compatibility.
        sourceCompatibility = JavaVersion.VERSION_11

        // Generate Java bytecode compatible with Java 11.
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {

        // Generate JVM bytecode targeting Java 11.
        jvmTarget = "11"
    }

    buildFeatures {

        // Enable Jetpack Compose support.
        compose = true

        // Generate the BuildConfig class.
        buildConfig = true
    }
}

// ======================================================
// 5. RELEASE BACKEND SAFETY VALIDATION
// ======================================================

// Register a custom Gradle task that checks the release API URL.
val validateReleaseBackend = tasks.register("validateReleaseBackend") {

    // Execute the validation when the task runs.
    doLast {

        // Parse the explicitly configured backend URL, if present.
        val endpoint = configuredBaseUrl?.let { URI(it) }

        // A release must use an explicit HTTPS endpoint
        // that is not one of the listed local development hosts.
        require(
            endpoint != null &&
                    endpoint.scheme == "https" &&
                    endpoint.host !in setOf(
                "localhost",
                "127.0.0.1",
                "10.0.2.2",
                "::1",
                "[::1]"
            )
        ) {
            "Release requires an explicit HTTPS backend: -Pkilivana.apiBaseUrl=<url> or KILIVANA_API_BASE_URL. No production endpoint is assumed."
        }
    }
}

// Make tasks whose names contain "Release" depend on
// the release backend validation task.
// Exclude the validation task itself.
tasks.configureEach {
    if (
        name.contains("Release") &&
        name != "validateReleaseBackend"
    ) {
        dependsOn(validateReleaseBackend)
    }
}

// ======================================================
// 6. EXCLUDE OLD DEMO SOURCES
// ======================================================

// Exclude selected legacy demo Kotlin source files
// from Kotlin compilation tasks.
// This does not delete the files from the project.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
    .configureEach {

        exclude(
            "**/com/example/farmapp/demo/**",
            "**/DemoFarmerShell.kt",
            "**/DemoModePresentationTest.kt"
        )
    }

// ======================================================
// 7. APPLICATION DEPENDENCIES
// ======================================================

dependencies {

    // Mapbox: map rendering and Compose integration.
    implementation(libs.mapbox.maps)
    implementation(libs.mapbox.compose)

    // Android core Kotlin extensions.
    implementation(libs.androidx.core.ktx)

    // Android splash screen support.
    implementation(libs.androidx.core.splashscreen)

    // Lifecycle-aware Android runtime functionality.
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Integrates Jetpack Compose with Android Activities.
    implementation(libs.androidx.activity.compose)

    // Compose BOM aligns supported Compose library versions.
    implementation(platform(libs.androidx.compose.bom))

    // Core Compose UI functionality.
    implementation(libs.androidx.ui)

    // Compose graphics support.
    implementation(libs.androidx.ui.graphics)

    // Compose preview annotations and APIs.
    implementation(libs.androidx.ui.tooling.preview)

    // Material Design 3 components for Compose.
    implementation(libs.androidx.material3)

    // Extended Material icons.
    implementation(
        "androidx.compose.material:material-icons-extended"
    )

    // JUnit for local unit tests.
    testImplementation(libs.junit)

    // AndroidX JUnit support for instrumented tests.
    androidTestImplementation(libs.androidx.junit)

    // Espresso UI testing framework.
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose BOM for Android instrumented tests.
    androidTestImplementation(
        platform(libs.androidx.compose.bom)
    )

    // Compose UI testing utilities.
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Compose tooling for debug builds.
    debugImplementation(libs.androidx.ui.tooling)

    // Compose test manifest support for debug builds.
    debugImplementation(libs.androidx.ui.test.manifest)

    // Navigation between Jetpack Compose screens.
    implementation(
        "androidx.navigation:navigation-compose:2.8.6"
    )

    // ViewModel integration with Jetpack Compose.
    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4"
    )

    // Lifecycle-aware collection of state in Compose.
    implementation(
        "androidx.lifecycle:lifecycle-runtime-compose:2.8.4"
    )

    // Retrofit: HTTP API communication.
    implementation(
        "com.squareup.retrofit2:retrofit:2.11.0"
    )

    // Gson converter: JSON to/from Kotlin or Java objects.
    implementation(
        "com.squareup.retrofit2:converter-gson:2.11.0"
    )

    // OkHttp interceptor for HTTP request/response logging.
    implementation(
        "com.squareup.okhttp3:logging-interceptor:4.12.0"
    )

    // Kotlin coroutines support for Android.
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
    )
}
