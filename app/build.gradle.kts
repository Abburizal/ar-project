plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.arpackagevalidator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.arpackagevalidator"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // buildFeatures and composeOptions should be defined once directly under 'android'
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        viewBinding = true // ViewBinding diaktifkan di sini
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    // AndroidX & Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material) // This is com.google.android.material:material
    implementation(libs.androidx.constraintlayout)

    // ViewModel & LiveData (Lifecycle)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3") // Use the latest stable version
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.3")  // Use the latest stable version

    // Activity KTX (for by viewModels() and other utilities)
    implementation("androidx.activity:activity-ktx:1.9.0") // Use the latest stable version

    // RecyclerView dan CardView
    implementation("androidx.recyclerview:recyclerview:1.2.1") // Consider updating if newer stable versions are available
    implementation("androidx.cardview:cardview:1.0.0")

    // export JSON
    implementation("com.google.code.gson:gson:2.8.9") // Consider updating if newer stable versions are available

    // Sceneform (AR)
    implementation(libs.ar.core)
    implementation(libs.sceneform.ux)
    implementation(libs.sceneform.core)

    // Jetpack Compose (UI)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    // debugImplementation(libs.androidx.compose.ui.test.manifest) // Only if you need Compose test manifest
}
