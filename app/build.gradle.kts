import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

// Release signing: keystore.properties (local, git me nahi jaata) ya environment variables (CI).
// Keys: storeFile, storePassword, keyAlias, keyPassword  |  env: FG_KEYSTORE_FILE, FG_KEYSTORE_PASSWORD, FG_KEY_ALIAS, FG_KEY_PASSWORD
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "FG_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "FG_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "FG_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "FG_KEY_PASSWORD")
val hasReleaseKey = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null } &&
    rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "com.fastgallery.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fastgallery.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 36
        versionName = "1.4.30"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Apni keystore mile to usse sign; warna local testing ke liye debug key (Play Store pe upload NAHI hoga).
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("WARNING: release keystore nahi mili -> debug key se sign ho raha hai. Play Store ke liye keystore.properties set karo (README dekho).")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions {
        unitTests {
            // Robolectric ko resources chahiye; android.util.Log jaise stubs unit tests me crash na karein.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Baseline Profile: install ke waqt ART ko hot code batata hai -> cold start tez.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    "baselineProfile"(project(":baselineprofile"))

    // Unit tests (JVM): Robolectric se asli Uri / SharedPreferences milte hain.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}

baselineProfile {
    // CI/normal build me device ki zaroorat na pade; profile manually generate karke commit karo.
    automaticGenerationDuringBuild = false
    saveInSrc = true
    dexLayoutOptimization = true
}
