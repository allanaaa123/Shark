plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.sharkoverlay"; compileSdk = 35
    defaultConfig { applicationId = "com.example.sharkoverlay"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "1.2" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}


// JVM tests cover animation timing and navigator transitions.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

