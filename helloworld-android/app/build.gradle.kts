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
//    hilt{
//        enableAggregatingTask = false
//    }
}

kapt {
    correctErrorTypes = true
    showProcessorStats = true
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

    // Compose Animation: 한 줄만, 버전 제거(BOM 사용)
    implementation("androidx.compose.animation:animation")

    // LiveData interop: 올바른 아티팩트명 + 버전 제거
    implementation("androidx.compose.runtime:runtime-livedata")

    implementation(libs.androidx.hilt.common)

    // Retrofit / OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0") // ← 중복 제거

    // Splash
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Lifecycle Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    implementation(libs.volley)
    implementation(libs.androidx.junit.ktx)
    testImplementation(libs.testng)
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")

    // Lottie
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Accompanist: 버전 통일
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Google Login
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // DataStore / Security
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")

    // Activity Result API
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Debug
    debugImplementation(libs.androidx.ui.tooling)

    // Wearable
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coroutines (Play Services)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Coil - Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // ✅ 단위 테스트(JUnit4) — 이미 있음
    testImplementation("junit:junit:4.13.2")

    // ⚠️ TestNG 혼용은 당분간 비활성화 권장 (꼬임 방지)
    // // testImplementation(libs.testng)

    // ✅ 계측 테스트(androidTest) 기본 셋
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    // (안전하게) junit도 명시
    androidTestImplementation("junit:junit:4.13.2")

    // ✅ Hilt를 앱에서 사용 중이므로, androidTest 변형에도 Hilt 테스트/컴파일러 추가
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48.1")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48.1")

}