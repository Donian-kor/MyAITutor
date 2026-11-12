plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gitCommitCount = try {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0) output.toIntOrNull()?.takeIf { it > 0 } else null
} catch (error: java.io.IOException) {
    logger.warn("Git 커밋 수를 확인할 수 없어 앱 버전을 1로 설정합니다: ${error.message}")
    null
} catch (error: InterruptedException) {
    Thread.currentThread().interrupt()
    logger.warn("Git 커밋 수 조회가 중단되어 앱 버전을 1로 설정합니다.")
    null
} ?: 1

android {
    namespace = "com.donian.myaitutor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.donian.myaitutor"
        minSdk = 26
        targetSdk = 36
        // 커밋이 추가되면 Android versionCode와 표시 버전이 함께 증가합니다.
        versionCode = gitCommitCount
        versionName = "0.${gitCommitCount / 10}.${gitCommitCount % 10}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // LiteRT-LM 네이티브 라이브러리는 64비트만 제공한다.
        // 32비트 기기에서는 실행 즉시 실패하므로 APK에서 제외해 설치 자체를 막는다.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    implementation(project(":ppocr-sdk"))

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.huarangmeng:latex-renderer:1.7.2")

    // CameraX: 앱 내부 사각형 영역에 카메라 PreviewView를 표시하고 정지 이미지를 촬영.
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // EXIF orientation 보정용.
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // Gemma 4 E4B-IT LiteRT-LM Android runtime.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // 단위 테스트: 순수 함수(파서 등) 검증용. Android 의존성이 없는 모듈만 대상.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}