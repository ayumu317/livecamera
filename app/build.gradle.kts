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

fun buildConfigBoolean(name: String, defaultValue: Boolean = false): Boolean {
    val value = (project.findProperty(name) as String?)
        ?: localProperties.getProperty(name)
        ?: return defaultValue
    return value.equals("true", ignoreCase = true)
            || value == "1"
            || value.equals("yes", ignoreCase = true)
            || value.equals("on", ignoreCase = true)
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
        versionCode = 15
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ARK_API_KEY", "\"${buildConfigString("ARK_API_KEY")}\"")
        buildConfigField("String", "DOUBAO_MODEL_ID", "\"${buildConfigString("DOUBAO_MODEL_ID")}\"")
        buildConfigField("String", "DOUBAO_RESPONSES_URL", "\"${buildConfigString("DOUBAO_RESPONSES_URL")}\"")
        buildConfigField("String", "DOUBAO_BASE_URL", "\"${buildConfigString("DOUBAO_BASE_URL")}\"")
        buildConfigField("String", "DOUBAO_API_KEY", "\"${buildConfigString("DOUBAO_API_KEY")}\"")
        buildConfigField("String", "DOUBAO_MODEL", "\"${buildConfigString("DOUBAO_MODEL")}\"")
        buildConfigField("String", "NGROK_BASE_URL", "\"${buildConfigString("NGROK_BASE_URL")}\"")
        buildConfigField("String", "MANAGEMENT_BASE_URL", "\"${buildConfigString("MANAGEMENT_BASE_URL", "https://backend-production-d4a53.up.railway.app")}\"")
        buildConfigField("String", "SERPAPI_KEY", "\"${buildConfigString("SERPAPI_KEY")}\"")
        buildConfigField("String", "TENCENT_MAP_SDK_KEY", "\"${buildConfigString("TENCENT_MAP_SDK_KEY")}\"")
        buildConfigField("String", "SERPAPI_COST_CNY_PER_SEARCH", "\"${buildConfigString("SERPAPI_COST_CNY_PER_SEARCH")}\"")
        buildConfigField("String", "TENCENT_LOCATION_COST_CNY_PER_CALL", "\"${buildConfigString("TENCENT_LOCATION_COST_CNY_PER_CALL")}\"")
        buildConfigField("String", "LOCATION_GATEWAY_COST_CNY_PER_CALL", "\"${buildConfigString("LOCATION_GATEWAY_COST_CNY_PER_CALL")}\"")
        manifestPlaceholders["TencentMapSDK_KEY"] = buildConfigString("TENCENT_MAP_SDK_KEY")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.tencent.map.geolocation:TencentLocationSdk-openplatform:7.6.1.12")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

val startNgrok by tasks.registering(Exec::class) {
    group = "application"
    description = "Ensure an HTTPS ngrok tunnel is available before building the Android app."
    workingDir = rootProject.projectDir
    commandLine("node", rootProject.file("scripts/start-ngrok.js").absolutePath)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    if (buildConfigBoolean("ENABLE_NGROK_PREBUILD", false)) {
        dependsOn(startNgrok)
    }
}
