plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val taniResetRedirect = providers.gradleProperty("TANI_RESET_REDIRECT")
    .orElse("https://tani.invalid/auth/reset")
    .get()
val taniAppLinkHost = providers.gradleProperty("TANI_APP_LINK_HOST")
    .orElse("tani.invalid")
    .get()
val taniVersionCode = providers.gradleProperty("TANI_VERSION_CODE")
    .orNull?.toIntOrNull() ?: 4
val taniVersionName = providers.gradleProperty("TANI_VERSION_NAME")
    .orElse("3.1.0")
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
    signingConfigs {
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
            buildConfigField("String", "PASSWORD_RESET_REDIRECT", "\"tani://auth/reset\"")
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

    buildFeatures {
        buildConfig = true
    }

    namespace = "com.tani.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tani.app"
        minSdk = 24
        targetSdk = 35
        versionCode = taniVersionCode
        versionName = taniVersionName

        buildConfigField("String", "PASSWORD_RESET_REDIRECT", "\"$taniResetRedirect\"")
        buildConfigField("String", "APP_LINK_HOST", "\"$taniAppLinkHost\"")
        manifestPlaceholders["taniAppLinkHost"] = taniAppLinkHost
    }

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
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    testImplementation("junit:junit:4.13.2")
}
