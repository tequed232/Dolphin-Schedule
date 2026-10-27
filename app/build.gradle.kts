import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
val appVersion = providers.gradleProperty("dolphinVersion").get()
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures { buildConfig = true; compose = true }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/assets"))
    sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("generated/test-assets"))
}
val syncLegal by tasks.registering(Sync::class) {
    from(rootProject.file("LICENSE")) { rename { "MIT.txt" } }
    from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    from(rootProject.file("legal/APACHE-2.0.txt"))
    into(layout.buildDirectory.dir("generated/assets/legal"))
}
val syncSpreadsheet by tasks.registering(Sync::class) {
    from(rootProject.file("node_modules/@e965/xlsx/dist/xlsx.full.min.js"))
    rename("xlsx.full.min.js", "xlsx.js")
    into(layout.buildDirectory.dir("generated/assets/spreadsheet"))
    doFirst { check(rootProject.file("node_modules/@e965/xlsx/dist/xlsx.full.min.js").isFile) { "Excel 解码器缺失，请先执行 npm ci" } }
}
tasks.named("preBuild") { dependsOn(syncLegal, syncSpreadsheet) }
val nativeImportFixtures by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("node", "scripts/build/native-import-fixtures.mjs", layout.buildDirectory.dir("generated/test-assets/file-import").get().asFile.absolutePath)
    inputs.file(rootProject.file("scripts/build/native-import-fixtures.mjs"))
    outputs.dir(layout.buildDirectory.dir("generated/test-assets/file-import"))
}
tasks.matching { it.name == "mergeDebugAndroidTestAssets" || it.name == "generateDebugAndroidTestLintModel" || it.name == "lintAnalyzeDebugAndroidTest" }.configureEach { dependsOn(nativeImportFixtures) }
tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst { check(signingFile.exists()) { "正式包需要发行签名，请配置 signing.local.properties 和发行密钥。" } }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
