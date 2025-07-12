
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.project.realtimechatui"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.project.realtimechatui"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
//    Enable ViewBinding for easier UI handling
    buildFeatures {
        viewBinding = true
    }

}

dependencies {

    // Core Android dependencies
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // Additional Android dependencies
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Scrollable list view in chat
    implementation(libs.recyclerview)
    //implementation(libs.swiperefreshlayout)

    // For circle user profile
    implementation (libs.circleimageview)

    // Supports fetching, decoding, and displaying video stills, images, and animated GIFs
    implementation (libs.glide)

    // Network dependencies for API communication
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp)

    // Gson for JSON parsing
    implementation(libs.gson)

    // WebSocket for real-time chat
    implementation(libs.java.websocket)
    implementation(libs.stompprotocolandroid)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.webkit)

    // Core Android Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}