plugins { alias(libs.plugins.android.library); alias(libs.plugins.ksp) }
android {
    namespace = "kz.mura.data"
    compileSdk = 37
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    api(project(":core:domain"))
    implementation(libs.room.runtime); ksp(libs.room.compiler)
    implementation(libs.datastore); implementation(libs.coroutines)
    androidTestImplementation(libs.test.junit); androidTestImplementation(libs.test.runner)
}
