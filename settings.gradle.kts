// 插件从 Google、Maven Central 与 Gradle Plugin Portal 解析。
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
// 统一依赖仓库，阻止模块另行声明仓库；当前仅包含 Android app 模块。
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Dolphin Schedule"
include(":app")
