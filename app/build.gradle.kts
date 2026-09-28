import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Felles debug-nøkkel i prosjektet (app/rigg-debug.keystore), så apper bygget på GitHub og i Android Studio
// kan installeres over hverandre. Mangler filen, brukes Android Studios vanlige debug-nøkkel.
val sharedDebugKey: File = file("rigg-debug.keystore")

// Release-nøkkel: fra miljøvariabler (GitHub-hemmeligheter) eller keystore.properties (ikke i git).
val releaseProps = Properties()
val releasePropsFile: File = rootProject.file("keystore.properties")
if (releasePropsFile.exists()) releasePropsFile.inputStream().use { releaseProps.load(it) }
val releaseStore: String? = System.getenv("RIGG_KEYSTORE") ?: releaseProps.getProperty("storeFile")

android {
    namespace = "no.rigg.app"
    // 36 fordi Health Connect-biblioteket krever det. targetSdk (hvordan appen oppfører seg) er fortsatt 35.
    compileSdk = 36

    defaultConfig {
        applicationId = "no.rigg.app"
        minSdk = 26
        targetSdk = 35
        // Antall commits i git: stiger for hver endring og blir det samme på din PC og på GitHub,
        // så en nyere versjon alltid kan installeres over en eldre, uansett hvor den ble bygget.
        versionCode = runCatching {
            providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
        }.getOrDefault(1)
        versionName = "0.2"
    }

    signingConfigs {
        if (sharedDebugKey.exists()) {
            getByName("debug") {
                storeFile = sharedDebugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseStore != null) {
            create("release") {
                storeFile = rootProject.file(releaseStore)
                storePassword = System.getenv("RIGG_KEYSTORE_PASSWORD") ?: releaseProps.getProperty("storePassword")
                keyAlias = System.getenv("RIGG_KEY_ALIAS") ?: releaseProps.getProperty("keyAlias")
                keyPassword = System.getenv("RIGG_KEY_PASSWORD") ?: releaseProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    testOptions {
        // Android-klasser i vanlige enhetstester returnerer standardverdier i stedet for å kaste unntak.
        unitTests.isReturnDefaultValues = true
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
