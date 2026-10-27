# 第三方组件说明

项目业务代码采用根目录 [MIT 许可证](LICENSE)，Copyright (c) 2026 tequed232。以下组件保留自身版权与许可证；项目 MIT 许可不替换第三方条款。

| 组件 | 版本或版本来源 | 用途 | 许可证 |
|---|---|---|---|
| SheetJS CE，`@e965/xlsx` 分发 | 0.20.3，由 `package-lock.json` 锁定 | 本地 XLS / XLSX 单元格解码；测试时生成合成工作簿 | Apache-2.0 |
| Kotlin | 2.0.21 | 原生界面、课表业务、数据及系统能力 | Apache-2.0 |
| AndroidX Compose / Material 3 | BOM 2025.04.01 | 原生页面、主题、手势与动画 | Apache-2.0 |
| AndroidX Activity Compose | 1.10.1 | Activity、系统结果与 Compose 接入 | Apache-2.0 |
| AndroidX Lifecycle | 2.8.7 | ViewModel、生命周期与状态收集 | Apache-2.0 |
| AndroidX WebKit | 1.12.1 | 学校浏览器的隔离配置与 WebView 兼容 | Apache-2.0 |
| AndroidX Core | 1.15.0 | 系统兼容、文件 URI 和 FileProvider | Apache-2.0 |
| AndroidX ExifInterface | 1.4.2 | 本地图片旋转与镜像方向校正 | Apache-2.0 |
| Kotlin 协程 / Flow | 由 Gradle 解析的依赖树确定 | 异步处理、状态流与事务协调 | Apache-2.0 |
| Android Gradle Plugin | 8.13.0 | Android 编译与打包 | Apache-2.0 |
| Gradle | 8.13 | 可重复构建 | Apache-2.0 |
| JUnit | 4.13.2，仅测试 | JVM 与设备测试断言 | EPL-1.0 |
| AndroidX Test / Compose UI Test | 见 `app/build.gradle.kts`，仅测试 | 隔离模拟器与设备界面验证 | Apache-2.0 |
| JSON-java，`org.json:json` | 20240303，仅 JVM 测试 | 在 JVM 测试中解析 Android 同形 JSON | Public Domain |

Apache-2.0 全文位于 [legal/APACHE-2.0.txt](legal/APACHE-2.0.txt)。SheetJS 上游为 [SheetJS Community Edition](https://git.sheetjs.com/sheetjs/sheetjs)，AndroidX 上游为 [AndroidX](https://android.googlesource.com/platform/frameworks/support/)，Kotlin 与协程上游分别为 [Kotlin](https://github.com/JetBrains/kotlin) 和 [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)。测试工具不进入主应用 APK。

应用内“关于”将根 MIT、本说明和 Apache-2.0 正文作为离线资源随应用分发。构建使用 Gradle 的实际依赖树以及 `package-lock.json` 固定的 Excel 解码器；新增或替换依赖时同步维护本说明与所需版权材料。

应用品牌图由项目作者提供。Compose 导航图形与系统单色图标由项目维护，不再分发原 Web 界面的 React、Material Symbols SVG 子集、Vite 或 TypeScript。
