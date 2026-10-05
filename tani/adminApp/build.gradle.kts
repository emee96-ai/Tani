plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val taniAdminVersionCode = providers.gradleProperty("TANI_ADMIN_VERSION_CODE")
    .orNull?.toIntOrNull() ?: 6
val taniAdminVersionName = providers.gradleProperty("TANI_ADMIN_VERSION_NAME")
    .orElse("2.2.0")
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

val taniSupabaseUrl = providers.gradleProperty("TANI_SUPABASE_URL")
    .orElse("https://sihttimibjzoahvwuwbm.supabase.co")
    .get().trimEnd('/')
val taniSupabaseKey = providers.gradleProperty("TANI_SUPABASE_PUBLISHABLE_KEY")
    .orElse("sb_publishable_9syQMNqMr0q9V0_Z4W-jvA_OQjhHj1Y")
    .get()
require(taniSupabaseUrl.startsWith("https://")) { "TANI_SUPABASE_URL must use HTTPS" }
require(taniSupabaseKey.startsWith("sb_publishable_")) {
    "TANI_SUPABASE_PUBLISHABLE_KEY must be a publishable key, never a server key"
}

android {
    namespace = "com.tani.admin"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.tani.admin"
        minSdk = 24
        targetSdk = 36
        versionCode = taniAdminVersionCode
        versionName = taniAdminVersionName
        buildConfigField("String", "SUPABASE_URL", "\"$taniSupabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$taniSupabaseKey\"")
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
