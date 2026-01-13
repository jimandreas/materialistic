plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "io.github.hidroh.materialistic"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.hidroh.materialistic"
        minSdk = 23
        targetSdk = 36
        versionCode = 79
        versionName = "3.3"

        buildConfigField("int", "LATEST_RELEASE", "77")
        buildConfigField("String", "GITHUB_TOKEN", "\"\"")
        buildConfigField("String", "MERCURY_TOKEN", "\"\"")

        proguardFiles(
            getDefaultProguardFile("proguard-android.txt"),
            "proguard-rules.pro",
            "proguard-square.pro",
            "proguard-support.pro",
            "proguard-rx.pro"
        )

    }

    androidResources {
        localeFilters += listOf("en", "zh-rCN", "es")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        htmlReport = false
        xmlReport = false
        textReport = true
        lintConfig = file("${rootProject.rootDir}/lint.xml")
        abortOnError = true
        explainIssues = false
        absolutePaths = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.browser)
    implementation(libs.google.material)

    // Room
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    kaptTest(libs.androidx.room.compiler)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.compiler)

    // Dagger 1.x
    implementation(libs.dagger)
    kapt(libs.dagger.compiler)
    kaptTest(libs.dagger.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.adapter.rxjava)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Reactive
    implementation(libs.rxandroid)
    implementation(libs.rxjava)

    // Kotlin
    implementation(libs.kotlin.stdlib)
}
