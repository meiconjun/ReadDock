import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.readdock.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.readdock.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "0.5.0-beta05"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    if (signingPropertiesFile.isFile) {
        signingConfigs {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signingPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

tasks.register("verifyReleasePublicSurface") {
    dependsOn("assembleRelease")
    doLast {
        val releaseDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apk = listOf("app-release.apk", "app-release-unsigned.apk")
            .map { releaseDirectory.resolve(it) }
            .firstOrNull { it.isFile }
        check(apk != null) { "release APK not found in ${releaseDirectory.absolutePath}" }
        val forbiddenTokens = listOf(
            "MYCOMIC",
            "MockSource",
            "本地示例源",
            "插件化漫画阅读器原型",
            "com.readdock.mock",
            "com.readdock.test.synthetic"
        )
        val findings = mutableListOf<String>()
        zipTree(apk).visit {
            if (!isDirectory) {
                val content = file.readBytes().toString(Charsets.ISO_8859_1)
                forbiddenTokens.filter(content::contains).forEach { token ->
                    findings += "$relativePath: $token"
                }
            }
        }
        check(findings.isEmpty()) {
            "release APK contains forbidden public-surface content: ${findings.distinct().joinToString() }"
        }
    }
}

dependencies {
    implementation(project(":core:source-api"))
    implementation(project(":core:source-runtime"))
    implementation(project(":core:data"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
