import java.util.Properties
import java.io.FileInputStream
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
}




android {
    namespace = "com.openchat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openchat"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }

        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(FileInputStream(localPropsFile))
        }

        val fcmServiceAccountFile = rootProject.file("app/service-account.json")
        val fcmServiceAccountBase64 = if (fcmServiceAccountFile.exists()) {
            Base64.getEncoder().encodeToString(fcmServiceAccountFile.readBytes())
        } else {
            ""
        }
        buildConfigField("String", "FCM_SERVICE_ACCOUNT_JSON", "\"${fcmServiceAccountBase64}\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"${localProps.getProperty("cloudinary.apiSecret", "")}\"")
        buildConfigField("String", "TURN_USERNAME", "\"${localProps.getProperty("turn.username", "")}\"")
        buildConfigField("String", "TURN_CREDENTIAL", "\"${localProps.getProperty("turn.credential", "")}\"")
        // API base URL was hard-coded to localhost, which only resolves on a device
        // with `adb reverse tcp:3000 tcp:3000`. Move it to config so release builds
        // can point at a real host without editing source.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${localProps.getProperty("api.baseUrl", "http://10.0.2.2:3000/")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {



    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    
    implementation(libs.bundles.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    
    implementation(libs.bundles.retrofit)
    
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)
    
    implementation(libs.bundles.coroutines)



    // Keep Firebase for now (comment out when switching)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)

    // Google Sign-In
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    
    implementation(libs.bundles.media3)
    
    implementation(libs.bundles.camerax)
    
    // WebRTC - local AAR file for voice/video calling
    implementation(files("libs/libwebrtc.aar"))
    
    // Signal Protocol - TODO: implement E2EE using Android Keystore + custom protocol
    
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    
    implementation(libs.androidx.work.runtime)
    
    implementation(libs.androidx.lifecycle.service)
    
    implementation(libs.lottie.compose)
    
    // QR Scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    
    implementation(libs.androidx.security.crypto)
    
    implementation(libs.androidx.biometric)

    // On-Device AI (Llama 3.2, etc)
    implementation("com.google.mediapipe:tasks-genai:0.10.14")
    
    // On-Device Translation & Speech
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.4")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.placeholder)
    
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.protobuf.javalite)
    
    // Cloudinary - Free image/video storage
    implementation("com.cloudinary:cloudinary-android:2.5.0")
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

