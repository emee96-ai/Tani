plugins {
    id("com.android.application")
}

android {
    namespace = "com.eman.clinic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eman.clinic"
        minSdk = 24
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
