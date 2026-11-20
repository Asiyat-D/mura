import java.util.Properties

plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
val muraSigningFile = file(System.getProperty("user.home") + "/Library/Application Support/MuraSigning/release.properties")
val muraSigning = Properties().apply { if(muraSigningFile.isFile) muraSigningFile.inputStream().use { load(it) } }
android {
    namespace = "kz.mura.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "kz.mura.app"
        minSdk = 26; targetSdk = 37; versionCode = 2; versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    signingConfigs {
        if(muraSigningFile.isFile) create("distribution") {
            storeFile = file(muraSigning.getProperty("storeFile"))
            storePassword = muraSigning.getProperty("storePassword")
            keyAlias = muraSigning.getProperty("keyAlias")
            keyPassword = muraSigning.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if(muraSigningFile.isFile) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:data")); implementation(project(":core:domain"))
    implementation(libs.core); implementation(libs.activity); implementation(libs.fragment); implementation(libs.biometric)
    implementation(libs.lifecycle.compose); implementation(libs.viewmodel); implementation(libs.navigation)
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material)
    implementation(libs.compose.icons); implementation(libs.compose.preview); debugImplementation(libs.compose.tooling)
    implementation(libs.coroutines); implementation(libs.zxing)
    androidTestImplementation(platform(libs.compose.bom)); androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.test.junit); androidTestImplementation(libs.test.runner); androidTestImplementation(libs.test.espresso)
    debugImplementation(libs.compose.test.manifest)
}
