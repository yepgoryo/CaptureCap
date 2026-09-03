import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

abstract class DownloadAndCheckSha256Task : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val assetName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(4096)
            var bytesRead = input.read(buffer)

            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().hex()
    }

    @TaskAction
    fun downloadAndCheck() {
        val targetFile = outputDir.get().file(assetName.get()).asFile

        if (targetFile.exists() && calculateSha256(targetFile).equals(sha256.get(), true)) {
            logger.info("${assetName.get()} already exists and up-to-date")
            return
        }

        targetFile.parentFile.mkdirs()
        logger.lifecycle("Downloading ${assetName.get()}")

        URI(url.get()).toURL().openStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val actualHash = calculateSha256(targetFile)
        if (!actualHash.equals(sha256.get(), true)) {
            targetFile.delete()
            throw GradleException("Wrong SHA256 hash: $actualHash. Expected: ${sha256.get()}")
        }
    }
}

val scrcpyServerVersion = "4.1"
val scrcpyServerUrl = "https://github.com/Genymobile/scrcpy/releases/download/v$scrcpyServerVersion/scrcpy-server-v$scrcpyServerVersion"
val scrcpyServerSha256 = "deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae"
val scrcpyServerAssetName = "scrcpy-server"
val scrcpyServerDownloadDir = layout.buildDirectory.dir("generated/scrcpy-server/assets")

val downloadScrcpyServer = tasks.register<DownloadAndCheckSha256Task>("downloadScrcpyServer") {
    url.set(scrcpyServerUrl)
    sha256.set(scrcpyServerSha256)
    assetName.set(scrcpyServerAssetName)
    outputDir.set(scrcpyServerDownloadDir)
}

android {
    namespace = "com.yepgoryo.CaptureCap"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.yepgoryo.CaptureCap"
        minSdk = 24
        targetSdk = 36
        versionCode = 41
        versionName = "3.9.7"

        buildConfigField("String", "SCRCPY_SERVER_VERSION", "\"$scrcpyServerVersion\"")
        buildConfigField("String", "SCRCPY_SERVER_SHA256", "\"$scrcpyServerSha256\"")
        buildConfigField("String", "SCRCPY_SERVER_ASSET_NAME", "\"$scrcpyServerAssetName\"")

        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                "-DANDROID_TOOLCHAIN=clang",
                "-DNDK_ROOT=${android.ndkDirectory.absolutePath}")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "27.0.12077973"
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            downloadScrcpyServer,
            DownloadAndCheckSha256Task::outputDir
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.appcompat:appcompat-resources:1.7.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.media3:media3-common:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation(libs.androidx.transition.ktx)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}