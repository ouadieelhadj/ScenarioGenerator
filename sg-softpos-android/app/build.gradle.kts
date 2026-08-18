plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.futurpayment.softpos"; compileSdk = 35
    defaultConfig { applicationId = "com.futurpayment.softpos"; minSdk = 31; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildTypes { debug { buildConfigField("boolean", "LABORATORY_SDK", "true") }; release { isMinifyEnabled = true; buildConfigField("boolean", "LABORATORY_SDK", "false"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    kotlinOptions { jvmTarget = "21" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
