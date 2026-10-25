<p align="center"><img src="ui/src/assets/brand/app-icon.webp" width="76" height="76" alt="Dolphin Schedule 图标" /></p>

# Dolphin Schedule

把课程、教材和上课提醒，轻轻放在一起。

面向校园日常的本地 Android 课表应用。当前源码基于 GitHub `main` 的 **1.5.1**，保留列表/平铺课表、JSON/CSV/Excel 导入、临时课程、教材、课前提醒、系统日历、应用更新和编辑冲突保护。本轮移除第三方插件系统及 App 内置背景插画，未选自定义图片时使用主题底色。

课表、教材封面和自定义背景在本机保存。无需账号或业务服务器；开启应用更新检查时访问 GitHub Release，导航由用户主动打开外部地图。**Android App 为主，同时保留 GitHub Pages 浏览器访问**；网页共用同一套 React 界面，不提供 Android 后台提醒或系统日历能力。浏览器与 App 的本地数据独立，课表可通过 JSON 导出、导入转移。

## 开发与构建

需要 Node.js 24、npm、JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。

```sh
npm ci
npm run dev
npm run build
npm run check
npm test
npm run check:ui
```

浏览器开发入口：`http://127.0.0.1:5173`。配置本地 SDK 与 `JAVA_HOME` 后构建 Android：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

macOS / Linux 使用 `./gradlew`。Gradle 自动构建界面、同步 APK 内置资源、生成启动器图标。APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`；环境、验证与设备检查见 [开发说明](docs/development.md)。

## 技术栈与目录

React 19、TypeScript 5.9、Vite 7 和 CSS 负责界面；Kotlin 2.0、AndroidX Core / WebKit 与 WebViewAssetLoader 负责 Android 系统能力。数据使用 IndexedDB，原生提醒镜像使用 SharedPreferences。Excel 解析使用本地依赖 `@e965/xlsx`，按导入任务延迟加载。

```text
app/                Android 宿主、系统能力及原生单元测试
ui/                 Android / Pages 共用界面、组件、业务逻辑与本地资源
tests/              浏览器、TypeScript 单元、完整性测试及测试夹具
scripts/            构建、源码准入、测试编排、开发和 APK 交付工具
docs/               当前架构、开发、迁移和功能说明
legal/              第三方许可证
gradle/             Gradle Wrapper
.githooks/           可选安装的提交与推送源码检查
.github/workflows/  构建、回归、源码准入及 GitHub Pages 部署
```

[架构与目录职责](docs/architecture.md) 说明各层职责和数据流。[源码准入](docs/source-policy.md) 约束可提交文件；构建结果、日志、验证证据和临时工具链不进入源码仓库。

## 使用与迁移

列表与平铺共享同一份课表和选中日期；可手动编辑、添加单次课程，或导入 JSON、CSV、XLS、XLSX。支持课程与逐节作息同时导入，也支持仅更新时间表。所有导入先预览再确认，保存时检查其他窗口的修改。

本轮将正式包名改为 `com.dolphin.schedule`，debug 包增加 `.debug`，数据库名为 `dolphin-schedule`，**按新 App 安装，不覆盖旧包、不自动迁移旧私有数据**。请从旧版导出课表 JSON，再导入新版；封面、背景、偏好和系统授权需重新设置。详见 [数据与迁移](docs/data-and-migration.md)。

默认不附带背景图片；可在外观设置选择 JPG、PNG 或 WebP，并调节自定义背景模糊。关闭或移除图片后恢复主题底色。详见 [外观资源](docs/appearance.md)。

通知、日历与权限边界见 [通知说明](docs/notifications.md) 和 [权限说明](docs/permissions.md)。提醒准时性和实时通知外观受系统策略影响；节假日只做标记，不自动调课。

## 文档与发布

- [开发环境与测试](docs/development.md)
- [目录、技术栈与维护约定](docs/architecture.md)
- [导入格式与迁移](docs/data-and-migration.md)
- [导航与列表/平铺交互](docs/navigation.md)
- [签名与 APK 交付](docs/release.md)
- [版本变化](RELEASE_NOTES.md)

作者：[tequed232](https://github.com/tequed232/)。问题报告请说明版本、Android / WebView 版本、重现步骤及实际结果；样本请去除个人信息。

## 许可

项目业务代码按根目录 [GNU GPL v3](LICENSE) 授权。第三方依赖遵循各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `legal/`。作者提供的品牌美术资源授权单独说明，见 [资源说明](docs/appearance.md)。
