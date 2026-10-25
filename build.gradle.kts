// 统一声明 Android 与 Kotlin 构建插件版本，由 app 模块按需应用。
// Android 打包、界面同步和品牌资源生成任务位于 app/build.gradle.kts。
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
