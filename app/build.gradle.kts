plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.shinyalatte.btwidget"
    compileSdk = 34
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    defaultConfig { applicationId = "com.shinyalatte.btwidget"; minSdk = 26; targetSdk = 34; versionCode = 2; versionName = "1.1"; testInstrumentationRunner = "android.test.InstrumentationTestRunner" }
    // 毎回同じ鍵で署名して上書きインストールを可能にする（消さないこと）
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
