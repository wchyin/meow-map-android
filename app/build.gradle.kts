import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "cn.edu.meowmap"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.edu.meowmap"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.7.3"
        buildConfigField("String", "MEOW_API_URL", "\"http://8.148.216.205/meow-api\"")
        buildConfigField("boolean", "MEOW_LOCAL_MODE", providers.gradleProperty("meowLocal").orElse("false").get())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            for (field in listOf("EMAIL", "PASSWORD")) {
                val value = localSettings.getProperty("meow.demo." + field.lowercase(), "")
                val literal = value.replace("\\", "\\\\").replace("\"", "\\\"")
                buildConfigField("String", "MEOW_DEFAULT_" + field, "\"" + literal + "\"")
            }
        }
        release {
            buildConfigField("String", "MEOW_DEFAULT_EMAIL", "\"\"")
            buildConfigField("String", "MEOW_DEFAULT_PASSWORD", "\"\"")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testOptions.unitTests.all { it.maxHeapSize = "256m" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
