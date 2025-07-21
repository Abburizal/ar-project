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
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    // PERBAIKAN: Tambahan konfigurasi untuk mengatasi potential issues
    packagingOptions {
        pickFirst("**/libc++_shared.so")
        pickFirst("**/libjsc.so")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/license.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        exclude("META-INF/notice.txt")
        exclude("META-INF/ASL2.0")
        exclude("META-INF/*.kotlin_module")
    }
}

dependencies {
    // AndroidX & Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // PERBAIKAN: Lifecycle components dengan versi yang konsisten
    val lifecycleVersion = "2.8.3"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion") // Untuk coroutines
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion") // Untuk lifecycle annotations

    // Activity KTX (for by viewModels() and other utilities)
    implementation("androidx.activity:activity-ktx:1.9.0")

    // PERBAIKAN: Fragment KTX untuk better fragment support
    implementation("androidx.fragment:fragment-ktx:1.8.0")

    // RecyclerView dan CardView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // PERBAIKAN: Kotlin Coroutines untuk async operations
    val coroutinesVersion = "1.7.3"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // Export JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Sceneform (AR) - menggunakan versi yang stabil
    implementation(libs.ar.core)
    implementation(libs.sceneform.ux)
    implementation(libs.sceneform.core)

    // PERBAIKAN: Tambahan dependency untuk AR yang lebih stabil
    implementation("com.google.ar:core:1.41.0")

    // Jetpack Compose (UI)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // PERBAIKAN: Logging untuk better debugging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // PERBAIKAN: Permission handling yang lebih baik
    implementation("androidx.core:core:1.13.1")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    
    // PERBAIKAN: Test dependencies yang lebih lengkap
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}