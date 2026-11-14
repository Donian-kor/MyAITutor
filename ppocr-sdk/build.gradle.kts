plugins {
    id("com.android.library")
}

android {
    namespace = "com.paddle.ocr"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.21.1")
    implementation("org.opencv:opencv:4.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core-ktx:1.19.1")

    // 단위 테스트: Android·OpenCV 네이티브 의존성이 없는 순수 Kotlin 로직 전용.
    // 버전은 :app 모듈과 맞춘다.
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:runner:1.2.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    androidTestImplementation("androidx.test:monitor:1.7.2")
    androidTestImplementation("junit:junit:4.13.2")
}
