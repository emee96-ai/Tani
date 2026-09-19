plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val taniAdminVersionCode = providers.gradleProperty("TANI_ADMIN_VERSION_CODE")
    .orNull?.toIntOrNull() ?: 5
val taniAdminVersionName = providers.gradleProperty("TANI_ADMIN_VERSION_NAME")
    .orElse("2.1.0")
    .get()
val taniReleaseStoreFile = providers.gradleProperty("TANI_RELEASE_STORE_FILE").orNull
val taniReleaseStorePassword = providers.gradleProperty("TANI_RELEASE_STORE_PASSWORD").orNull
val taniReleaseKeyAlias = providers.gradleProperty("TANI_RELEASE_KEY_ALIAS").orNull
val taniReleaseKeyPassword = providers.gradleProperty("TANI_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    taniReleaseStoreFile,
    taniReleaseStorePassword,
    taniReleaseKeyAlias,
    taniReleaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.tani.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tani.admin"
        minSdk = 24
        targetSdk = 35
        versionCode = taniAdminVersionCode
        versionName = taniAdminVersionName
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(taniReleaseStoreFile))
                storePassword = requireNotNull(taniReleaseStorePassword)
                keyAlias = requireNotNull(taniReleaseKeyAlias)
                keyPassword = requireNotNull(taniReleaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
