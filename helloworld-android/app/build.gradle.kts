plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ms.helloworld"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ms.helloworld"
        minSdk = 26
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    hilt{
        enableAggregatingTask = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.hilt.common)

    // Retrofit + Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // 스플래시 화면 관련 의존성 추가
    implementation("androidx.compose.animation:animation:1.7.8")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Lifecycle Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt for Compose
    implementation("com.google.dagger:hilt-android:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt WorkManager
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // Lottie
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // 시스템 UI 제어 (상태바/네비게이션바 색상 등)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    //Google Login
    implementation ("androidx.credentials:credentials:1.5.0")
    implementation ("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation ("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Kakao Login - 임시 주석 처리
    // implementation("com.kakao.sdk:v2-user:2.20.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Android Animation
    implementation("androidx.compose.animation:animation:1.9.0")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // Activity Result API
    implementation("androidx.activity:activity-ktx:1.8.2")

    // 개발/디버그
    debugImplementation(libs.androidx.ui.tooling)

    // Google Play Services - Wearable
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // 기타 필요한 의존성들
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

}