plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from ~/.gradle/gradle.properties (never committed):
//   RCA_STORE_FILE, RCA_STORE_PASSWORD, RCA_KEY_ALIAS, RCA_KEY_PASSWORD
val releaseStoreFile = providers.gradleProperty("RCA_STORE_FILE").orNull

android {
    namespace = "io.github.jimspijker.repeatcallalert"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jimspijker.repeatcallalert"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("RCA_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("RCA_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("RCA_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}
