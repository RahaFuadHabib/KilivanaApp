plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
            "\"https://march-cacti-flashcard.ngrok-free.dev/api/v1/\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Explicit opt-in only. Demo builds have separate app storage from the real client.
            val mockAuth = providers.gradleProperty("kilivana.mockAuth").orNull == "true"
            buildConfigField("boolean", "USE_MOCK_AUTH", mockAuth.toString())
            if (mockAuth) applicationIdSuffix = ".demo"
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

dependencies {

    implementation(libs.androidx.core.ktx)
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
