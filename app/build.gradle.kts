import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hzyazilim.betalink"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hzyazilim.betalink"
        minSdk = 24
        targetSdk = 36
        versionCode = 31
        versionName = "0.9.15"
        buildConfigField("String", "SUPABASE_URL", "\"https://lwloymjcnmzobjmgvlnp.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_iafVZ3uELvqqUKYaMfYwgA_kEu5LACD\"")
        // Resolve the public Google Web OAuth client ID for Android Studio and CI builds.
        // Keep local.properties uncommitted; GitHub Actions supplies the Gradle property with -P.
        val localProperties = Properties().apply {
            val localFile = rootProject.file("local.properties")
            if (localFile.isFile) localFile.inputStream().use { load(it) }
        }
        val googleWebClientId = providers.gradleProperty("BETALINK_GOOGLE_WEB_CLIENT_ID")
            .orElse(providers.environmentVariable("BETALINK_GOOGLE_WEB_CLIENT_ID"))
            .orElse(localProperties.getProperty("BETALINK_GOOGLE_WEB_CLIENT_ID").orEmpty())
            .orElse("202417827129-84f6j6in9e7m98n9m840n68b1o54l5l2.apps.googleusercontent.com")
            .get()
        val escapedGoogleWebClientId = googleWebClientId.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$escapedGoogleWebClientId\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
}
