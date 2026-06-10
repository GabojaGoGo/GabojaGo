import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.tripmate"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.tripmate"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val envProperties = Properties()
        val envPropertiesFile = rootProject.projectDir.parentFile.resolve(".env")
        if (envPropertiesFile.exists()) {
            envProperties.load(FileInputStream(envPropertiesFile))
        }

        fun resolveConfig(name: String): String? =
            localProperties.getProperty(name)
                ?: envProperties.getProperty(name)
                ?: System.getenv(name)

        manifestPlaceholders["kakaoNativeAppKey"] =
            resolveConfig("KAKAO_NATIVE_APP_KEY")
                ?: ""
        manifestPlaceholders["naverClientId"] =
            resolveConfig("NAVER_CLIENT_ID")
                ?: ""
        manifestPlaceholders["naverClientSecret"] =
            resolveConfig("NAVER_CLIENT_SECRET")
                ?: ""
        manifestPlaceholders["naverClientName"] =
            resolveConfig("NAVER_CLIENT_NAME")
                ?: "가보자GO"
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
