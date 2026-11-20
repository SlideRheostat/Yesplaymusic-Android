plugins {
    id("com.android.application")
}

android {
    namespace = "com.litemusic"
    compileSdk = 36

    defaultConfig {
        // 发布包名；源码 namespace 仍为 com.litemusic（不影响安装身份）
        applicationId = "com.yesplaymusic.mobile"
        minSdk = 19
        targetSdk = 34
        versionCode = 70
        versionName = "3.4.1"

        // API 19/20 无原生 VectorDrawable：由 AppCompat 在运行时解析矢量图，
        // 布局图标与代码 setImageResource 均走兼容路径，视觉与 21+ 一致。
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // minSdk 19 无原生 multidex：开启 R8 收缩，确保方法数低于 64K 单 dex 上限，
            // 同时不引入额外的 multidex 依赖。
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // 以下版本为兼容 minSdk 19（Android 4.4）的最低要求组合：
    // material 1.13+/core 1.15+ 要求更高 minSdk，需保持当前版本
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.google.zxing:core:3.5.3")
}
