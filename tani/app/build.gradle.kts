plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.serialization") }

android { namespace = "com.tani.app"; compileSdk = 35
    defaultConfig { applicationId = "com.tani.app"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
