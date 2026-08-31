plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "net.aokaze.osupanel"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.aokaze.osupanel"
        // 26+ = EncryptedSharedPreferences (Keystore), font & API modern
        minSdk = 26
        targetSdk = 34
        // Version centralized in gradle.properties (APP_VERSION_CODE / APP_VERSION_NAME)
        versionCode = (project.findProperty("APP_VERSION_CODE") as String).toInt()
        versionName = project.findProperty("APP_VERSION_NAME") as String

        // osu! OAuth redirect (osupanel://callback) — used by
        // RedirectUriReceiverActivity from AppAuth (same as the Flutter version).
        manifestPlaceholders["appAuthRedirectScheme"] = "osupanel"
    }

    // Signing like the reference project (Z Html Editor):
    // credentials come from env (locally ~/.environment_variable.sh, in CI
    // from the SIGN_* repository secrets). Empty env values are treated as
    // absent so the build still runs unsigned / debug.
    signingConfigs {
        val signKey = System.getenv("SIGN_KEY")?.takeIf { it.isNotBlank() }
        val signAlias = System.getenv("SIGN_ALIAS")?.takeIf { it.isNotBlank() }
        val signKeyPass = System.getenv("SIGN_KEY_PASS")?.takeIf { it.isNotBlank() }
        val signStorePass = System.getenv("SIGN_STORE_PASS")?.takeIf { it.isNotBlank() }
        if (signKey != null && signAlias != null && signKeyPass != null && signStorePass != null) {
            create("release") {
                storeFile = file(signKey)
                storePassword = signStorePass
                keyAlias = signAlias
                keyPassword = signKeyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Jetpack Compose — Material 3
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Networking — OkHttp + Retrofit + kotlinx.serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // OAuth (Authorization Code Grant) — same library as flutter_appauth
    implementation("net.openid:appauth:0.11.1")

    // Custom Tabs — open web pages as an in-app overlay (like the login
    // flow), NOT the full standalone browser.
    implementation("androidx.browser:browser:1.8.0")

    // Token stored encrypted (Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Image loading (avatar / cover) + animated GIF support
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Render SVG -> Bitmap (stat-sign widget signature)
    implementation("com.caverock:androidsvg-aar:1.4")

    // Beatmap audio preview (counterpart of Flutter's just_audio)
    implementation("androidx.media3:media3-exoplayer:1.4.1")

    // QR code scanner (CameraX + ML Kit barcode)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // QR code generation (ZXing — encode beatmap data into QR bitmap)
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
