# Android 应用模块

应用的正式启动入口为 `NativeActivity`。Kotlin＋Compose 负责全部应用页面，SQLite 负责本地事务保存。学校网页、Excel 解码与一次性旧数据迁移各有独立且受限的 WebView。

| 路径 | 作用 |
|---|---|
| `build.gradle.kts` | 包名、SDK、依赖、签名和资源生成；读取根 `dolphinVersion` |
| `src/main/AndroidManifest.xml` | 启动入口、教务页、文件共享、通知及网络权限 |
| `src/main/kotlin/com/dolphin/schedule/ui/` | Compose 课表、导入、教材、设置、关于及其状态控制 |
| `src/main/kotlin/com/dolphin/schedule/domain/` | 课程、日期、节次、冲突、布局和导入规则 |
| `src/main/kotlin/com/dolphin/schedule/data/` | SQLite 事务、备份编解码、旧数据迁移及私有草稿 |
| `src/main/kotlin/com/dolphin/schedule/importing/` | JSON / CSV / Excel 解析、映射与预览 |
| `src/main/kotlin/com/dolphin/schedule/academic/` | 允许学校的浏览器、登录环境与识别控制 |
| `src/main/kotlin/com/dolphin/schedule/notifications/` | 系统行程卡片、有效令牌、超时与到达接收器 |
| `src/main/kotlin/com/dolphin/schedule/media/` | 封面与背景的本机压缩、方向校正 |
| `src/main/assets/academic/extract.js` | 学校课表 DOM / 同源学期信息提取 |
| `src/main/res/` | 品牌图、主题、系统图标、文件共享和备份规则 |
| `src/test/` | Kotlin 业务与数据规则的 JVM 测试 |
| `src/androidTest/` | Compose、事务、导入及系统交互测试 |
| `proguard-rules.pro` | 正式构建压缩规则 |

根目录执行 `npm ci --ignore-scripts` 后即可用 Gradle 构建；Node 仅提供 Excel 依赖和检查工具。`app/build/generated/` 内的 Excel、许可与测试工作簿由构建生成，不提交；合成工作簿仅进入测试 APK。

完整分层见 [架构说明](../docs/architecture.md)，文件放置见 [目录指南](../docs/directory-structure.md)，开发与设备验证见 [开发说明](../docs/development.md)。
