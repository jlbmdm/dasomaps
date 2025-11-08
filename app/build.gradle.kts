plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.dasomaps.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dasomaps.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true  // Generar BuildConfig
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.coreKtx)
    implementation(libs.lifecycleRuntimeKtx)
    implementation(libs.lifecycleViewmodelKtx)
    implementation(libs.lifecycleLivedataKtx)

    implementation(libs.activityCompose)
    implementation(platform(libs.composeBom))
    implementation(libs.composeUi)
    implementation(libs.composeUiGraphics)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.composeMaterial3)
    implementation(libs.composeMaterialIconsExtended)

    implementation(libs.navigationCompose)

    implementation(libs.coroutinesAndroid)
    implementation(libs.coroutinesCore)

    // osmdroid incluye soporte para MBTiles nativamente
    implementation(libs.osmdroidAndroid)

    implementation(libs.accompanistPermissions)

    implementation(libs.jtsCore)

    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    ksp(libs.roomCompiler)

    implementation(libs.sqliteKtx)

    implementation(libs.postgresql)

    implementation(libs.retrofit)
    implementation(libs.retrofitConverterGson)
    implementation(libs.okhttpLoggingInterceptor)

    implementation(libs.locationServices)

    implementation(libs.gson)

    implementation(libs.timber)

    implementation(libs.datastorePreferences)

    testImplementation(libs.junit)
    testImplementation(libs.coroutinesTest)
    androidTestImplementation(libs.androidTestJunit)
    androidTestImplementation(libs.espressoCore)
    androidTestImplementation(platform(libs.composeBom))
    androidTestImplementation(libs.composeUiTestJunit4)

    debugImplementation(libs.composeUiTooling)
    debugImplementation(libs.composeUiTestManifest)
}
