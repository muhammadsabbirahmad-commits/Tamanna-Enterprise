plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.tamanna.enterprise"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tamanna.enterprise"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("app/release-keystore.jks")
            storeFile = keystoreFile
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.rmtheis:tess-two:9.1.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.firebase:firebase-auth:23.2.1")
    implementation("com.google.firebase:firebase-firestore:25.1.4")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

val ocrDataDir = layout.projectDirectory.dir("src/main/assets/tessdata")

tasks.register("downloadOcrData") {
    outputs.files(
        ocrDataDir.file("ben.traineddata"),
        ocrDataDir.file("eng.traineddata")
    )
    doLast {
        ocrDataDir.asFile.mkdirs()
        val files = mapOf(
            "ben.traineddata" to "https://github.com/tesseract-ocr/tessdata/raw/4.00/ben.traineddata",
            "eng.traineddata" to "https://github.com/tesseract-ocr/tessdata/raw/4.00/eng.traineddata"
        )
        files.forEach { (name, url) ->
            val target = ocrDataDir.file(name).asFile
            if (!target.exists() || target.length() < 100_000) {
                URI(url).toURL().openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadOcrData")
}
