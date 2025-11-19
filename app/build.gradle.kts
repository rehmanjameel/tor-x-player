plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("androidx.navigation.safeargs")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.torx.torxplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.torx.torxplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // create separate apk
//    splits {
//        abi {
//            isEnable = true // Enable ABI splits
//            reset() // Clear the default list of ABIs
//            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64") // Specify desired ABIs
//            isUniversalApk = false // Set to false to generate separate APKs
//        }
//    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.common.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // glide for images/videos thumbnail
    implementation(libs.glide)
    annotationProcessor(libs.compiler) // Required for generated API

    // exoplayer for video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.ui.compose)

    implementation(libs.lottie)

    implementation(libs.androidx.room.runtime)

    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    annotationProcessor(libs.room.compiler)

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.ktx)

    ksp(libs.room.compiler)

    // Import the Firebase BoM
    implementation(platform(libs.firebase.bom))
    // Add the dependency for the Firebase SDK for Google Analytics
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
}