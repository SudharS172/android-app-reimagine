plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.andoridappforreimagine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.andoridappforreimagine"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments += mapOf(
            "clearPackageData" to "true",
            "useTestStorageService" to "true"
        )
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

    lint {
        disable += "ProtectedPermissions"
        abortOnError = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }

    tasks.withType<Test> {
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // Skip connected tests during build
    tasks.matching { it.name.contains("connectedCheck") }.configureEach {
        enabled = false
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
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.generativeai)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Test orchestrator
    androidTestUtil("androidx.test:orchestrator:1.4.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}
