# Android 应用模块

这个目录将共用界面封装为 Android App，并提供系统提醒、通知、日历和文件保存等原生能力。

| 路径 | 作用 |
|---|---|
| `build.gradle.kts` | 配置 Android 构建、包名、依赖以及界面和品牌资源生成任务 |
| `src/main/AndroidManifest.xml` | 声明应用身份、权限和系统组件 |
| `src/main/java/com/dolphin/schedule/` | Kotlin 宿主、内部通信、导航、提醒、日历和版本更新 |
| `src/main/res/` | 主题、通知图标及文件共享等系统配置 |
| `src/test/` | 原生逻辑的 JUnit 单元测试 |
| `proguard-rules.pro` | 正式版本的代码压缩规则 |

在仓库根目录执行 `npm ci` 后，Windows 可用 `npm run android` 构建并验证；其他系统按 [开发说明](../docs/development.md) 调用 Gradle Wrapper。

界面源码位于 `ui/`。Gradle 在构建时生成和同步资源，`app/build/` 不提交。各 Kotlin 文件的职责见 [文件目录指南](../docs/directory-structure.md#android-目录与关键文件)，权限用途见 [权限说明](../docs/permissions.md)。
