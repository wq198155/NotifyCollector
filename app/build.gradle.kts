plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.notifycollector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.notifycollector"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "1.14"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    signingConfigs {
        create("release") {
            storeFile = file("C:/Users/wq/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 离线 AI 解析（本地 LLM，首次运行下载模型）
    // 0.10.22+ 才支持 Gemma-3（0.10.14 是 Gemma-2 时代，加载 gemma3 模型会原生 SIGABRT）
    // 该版本为 Java 21 字节码，构建须用 JDK 21（org.gradle.java.home / JAVA_HOME 指向 JDK21）
    implementation("com.google.mediapipe:tasks-genai:0.10.29")
}
