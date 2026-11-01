plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
val persistentKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
 namespace = "com.namakkalmarket.app"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.namakkalmarket.app"
  minSdk = 24
  targetSdk = 35
  versionCode = 3
  versionName = "1.0.2"
  buildConfigField("String", "SUPABASE_URL", "\"https://zvjizwzuejqboetjrwkl.supabase.co\"")
  buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_hg4ZDwD1sXFvCYGdRdnFEw_ro0ra4w7\"")
 }
 signingConfigs {
  if (persistentKeystorePath != null) {
   create("persistent") {
    storeFile = file(persistentKeystorePath)
    storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    keyAlias = System.getenv("ANDROID_KEY_ALIAS")
    keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
   }
  }
 }
 buildTypes {
  getByName("debug") {
   if (persistentKeystorePath != null) {
    signingConfig = signingConfigs.getByName("persistent")
   }
  }
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("androidx.core:core-ktx:1.15.0")
}
