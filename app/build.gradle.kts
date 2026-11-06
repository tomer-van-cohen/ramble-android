plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
android {
    namespace = "io.github.tomervancohen.rambleflow"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.tomervancohen.rambleflow"
        minSdk = 30
        targetSdk = 36
        versionCode = 13
        versionName = "0.3.6-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    kapt("androidx.room:room-compiler:2.8.3")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.work:work-testing:2.10.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Keep transitive build/test libraries on compatible, security-fixed release lines.
// Matching an existing dependency does not add these libraries to the runtime APK.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        val patched = when {
            requested.group == "com.google.guava" && requested.name == "guava" -> "33.7.2-jre"
            requested.group == "com.google.protobuf" && requested.name in setOf("protobuf-java", "protobuf-kotlin", "protobuf-java-util") -> "3.25.5"
            requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true -> "4.1.137.Final"
            requested.group == "io.opentelemetry" && requested.name in setOf("opentelemetry-api", "opentelemetry-context") -> "1.67.0"
            requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.85"
            else -> null
        }
        if (patched != null) {
            useVersion(patched)
            because("Apply security fixes while retaining compatible library release lines; see docs/DEPENDENCY-SECURITY.md")
        }
    }
}

dependencyLocking { lockAllConfigurations() }
