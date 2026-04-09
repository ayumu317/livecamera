import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

fun buildConfigString(name: String, defaultValue: String = ""): String {
    val value = (project.findProperty(name) as String?)
        ?: localProperties.getProperty(name)
        ?: defaultValue
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

android {
    namespace = "com.example.livecamera"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.livecamera"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DOUBAO_BASE_URL", "\"${buildConfigString("DOUBAO_BASE_URL")}\"")
        buildConfigField("String", "DOUBAO_API_KEY", "\"${buildConfigString("DOUBAO_API_KEY")}\"")
        buildConfigField("String", "DOUBAO_MODEL", "\"${buildConfigString("DOUBAO_MODEL")}\"")
        buildConfigField("String", "SERPAPI_KEY", "\"${buildConfigString("SERPAPI_KEY")}\"")
        buildConfigField("String", "TENCENT_MAP_SDK_KEY", "\"${buildConfigString("TENCENT_MAP_SDK_KEY", "60e19a9ee01fdc4ee0f940aa661ac76e")}\"")
        manifestPlaceholders["TencentMapSDK_KEY"] = buildConfigString("TENCENT_MAP_SDK_KEY", "60e19a9ee01fdc4ee0f940aa661ac76e")
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.tencent.map.geolocation:TencentLocationSdk-openplatform:7.6.1.12")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
