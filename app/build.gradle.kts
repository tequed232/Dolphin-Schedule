import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val appVersion = Regex("APP_VERSION\\s*=\\s*'([^']+)'").find(rootProject.file("ui/src/meta.ts").readText())!!.groupValues[1]
val versionParts = appVersion.split('.').map(String::toInt)
val signingFile = rootProject.file("signing.local.properties")
val signing = Properties().apply { if(signingFile.exists()) signingFile.inputStream().use { load(it) } }
android {
    namespace = "com.dolphin.schedule"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.dolphin.schedule"
        minSdk = 26
        targetSdk = 36
        versionName = appVersion
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts.getOrElse(2) { 0 }
    }
    signingConfigs {
        if(signingFile.exists()) create("distribution") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "" }
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); if(signingFile.exists()) signingConfig = signingConfigs.getByName("distribution") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/assets"))
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/res/brand"))
}
val buildUi by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    environment("DOLPHIN_BASE_PATH", "./")
    commandLine(if(System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm", "run", "build")
    inputs.dir(rootProject.file("ui/src"))
    inputs.files(rootProject.file("package.json"), rootProject.file("package-lock.json"), rootProject.file("ui/index.html"), rootProject.file("ui/vite.config.ts"), rootProject.file("ui/tsconfig.json"))
    outputs.dir(rootProject.file("ui/dist"))
}
val syncUi by tasks.registering(Sync::class) {
    dependsOn(buildUi)
    from(rootProject.file("ui/dist"))
    into(layout.buildDirectory.dir("generated/assets/ui"))
}
val syncBrand by tasks.registering(Sync::class) {
    from(rootProject.file("ui/src/assets/brand/app-icon.webp"))
    rename("app-icon.webp", "app_icon.webp")
    into(layout.buildDirectory.dir("generated/res/brand/drawable-nodpi"))
}
tasks.named("preBuild") { dependsOn(syncUi, syncBrand) }
tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst { check(signingFile.exists()) { "正式包需要发行签名，请配置 signing.local.properties 和发行密钥。" } }
}
dependencies { implementation("androidx.webkit:webkit:1.12.1"); implementation("androidx.core:core-ktx:1.15.0"); testImplementation("junit:junit:4.13.2") }
