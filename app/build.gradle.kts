import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.gms.google.services)
}

// Lógica de versão movida para fora do bloco android para evitar conflitos de DSL
val versionPropsFile = File(project.projectDir, "version.properties")
val versionProps = Properties()
var currentVersionCode = 1

if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { stream ->
        versionProps.load(stream)
    }
    currentVersionCode = versionProps.getProperty("VERSION_CODE")?.toInt() ?: 1
}

// Verifica se é um build de release de forma compatível
var isRelease = false
for (task in gradle.startParameter.taskNames) {
    if (task.contains("Release", ignoreCase = true)) {
        isRelease = true
        break
    }
}

if (isRelease) {
    currentVersionCode++
    versionProps.setProperty("VERSION_CODE", currentVersionCode.toString())
    versionPropsFile.outputStream().use { stream ->
        versionProps.store(stream, null)
    }
}

val major = currentVersionCode / 100
val minor = currentVersionCode % 100
val generatedVersionName = String.format("%d.%02d", major, minor)

android {
    namespace = "com.example.seaquakeiacourse"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.seaquakeiacourse"
        minSdk = 24
        targetSdk = 35

        versionCode = currentVersionCode
        versionName = generatedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ✅ NOVO: Adicionar Gemini API Key de forma segura via BuildConfig
        buildConfigField("String", "GEMINI_API_KEY", "\"AIzaSyB90ZIQ_chSCaGRFYCDOOgz37HHAJEfrMI\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.google.generativeai)

    // ============ FIREBASE ============
    implementation(platform(libs.google.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-config")
    implementation(libs.play.services.auth)
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // ============ NETWORKING ============
    implementation("org.json:json:20230227")

    // ============ LOGGING ============
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
