import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Same keystore as the legacy app, so this installs as an update of shakir.swalah.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// API servers + secret live in the git-ignored api.properties (see api.properties.example).
val apiProps = Properties().apply {
    val f = rootProject.file("api.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val apiServers: List<String> = apiProps.getProperty("api.servers", "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
val apiSecret: String = apiProps.getProperty("api.secret", "")

/** Writes res/xml/network_security_config.xml allowing plain HTTP to the API hosts only. */
abstract class GenerateNetworkConfig : DefaultTask() {
    @get:Input abstract val hosts: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDir.file("xml/network_security_config.xml").get().asFile
        file.parentFile.mkdirs()
        val domains = hosts.get().joinToString("\n") { "        <domain includeSubdomains=\"false\">$it</domain>" }
        val body = if (hosts.get().isEmpty()) "" else "    <domain-config cleartextTrafficPermitted=\"true\">\n$domains\n    </domain-config>\n"
        file.writeText(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!-- Generated from api.properties: the app's own API servers speak plain HTTP. -->\n" +
                "<network-security-config>\n$body</network-security-config>\n",
        )
    }
}

android {
    namespace = "shakir.swalah"
    compileSdk = 36

    defaultConfig {
        applicationId = "shakir.swalah"
        minSdk = 26
        targetSdk = 36
        // Legacy app ended at 159 / 2.8.7.
        versionCode = 200
        versionName = "3.0.0"
        buildConfigField("String", "API_SERVERS", "\"${apiServers.joinToString(",")}\"")
        buildConfigField("String", "API_SECRET", "\"$apiSecret\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the Play Store app (which Play re-signs with its own key).
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "أَذَان Debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", "أَذَان")
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // The GIF and sounds are read as raw streams.
        noCompress += listOf("gif")
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<GenerateNetworkConfig>("generate${variant.name.replaceFirstChar { it.uppercase() }}NetworkConfig") {
            hosts.set(apiServers.map { URI(it).host })
        }
        variant.sources.res?.addGeneratedSourceDirectory(task, GenerateNetworkConfig::outputDir)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // GPS for "use my current location" (city name comes from the Android Geocoder).
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Play Services pulls in an old Fragment; activity-result APIs need >= 1.3.
    implementation("androidx.fragment:fragment:1.9.1")

    testImplementation("junit:junit:4.13.2")
}
