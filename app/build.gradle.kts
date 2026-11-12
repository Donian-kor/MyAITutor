plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.donian.myaitutor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.donian.myaitutor"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // LiteRT-LM ?ㅼ씠?곕툕 ?쇱씠釉뚮윭由щ뒗 64鍮꾪듃留??쒓났?쒕떎.
        // 32鍮꾪듃 湲곌린?먯꽌???ㅽ뻾 利됱떆 ?ㅽ뙣?섎?濡?APK?먯꽌 ?쒖쇅???ㅼ튂 ?먯껜瑜?留됰뒗??
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

    // CameraX: ???대? ?ш컖???곸뿭??移대찓??PreviewView瑜??쒖떆?섍퀬 ?뺤? ?대?吏瑜?珥ъ쁺.
    val cameraX = "1.6.2"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    // EXIF orientation 蹂댁젙??
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // Gemma 4 E4B-IT LiteRT-LM Android runtime.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // ?⑥쐞 ?뚯뒪?? ?쒖닔 ?⑥닔(?뚯꽌 ?? 寃利앹슜. Android ?섏〈?깆씠 ?녿뒗 紐⑤뱢留????
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}