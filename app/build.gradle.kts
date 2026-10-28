plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.sharkoverlay"; compileSdk = 35
    defaultConfig { applicationId = "com.example.sharkoverlay"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}


// JVM tests cover animation timing and navigator transitions.
dependencies { testImplementation("junit:junit:4.13.2") }
