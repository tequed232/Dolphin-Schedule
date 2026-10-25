<p align="center"><img src="ui/src/assets/brand/app-icon.webp" width="76" height="76" alt="Dolphin Schedule 图标" /></p>

# Dolphin Schedule

Dolphin Schedule 是面向校园日常的本地课表应用，以 **Android App** 为主，同时提供 **GitHub Pages 网页访问**。无需账号或业务服务器，课表、教材封面、外观和偏好保存在当前设备。

[下载 Android 版本](https://github.com/tequed232/Dolphin-Schedule/releases) · [浏览器访问](https://tequed232.github.io/Dolphin-Schedule/) · [使用与迁移](docs/data-and-migration.md) · [反馈问题](https://github.com/tequed232/Dolphin-Schedule/issues)

## 可以做什么

- **查看与编辑课表**：列表和平铺共用课程与选中日期，支持搜索、临时课程、拖动和调整节次。
- **导入文件**：支持 JSON、CSV、XLS、XLSX，预览学期、课程和逐节作息后确认；也可仅更新时间表。
- **管理教材**：为课程填写教材信息、选择封面，在课程详情中查看。
- **安排上课提醒**：Android 提供课前提醒、导航行程和通知栏小伙伴；地图由用户主动打开。
- **连接系统日历**：Android 可导入、清理和复原应用自己的课程日历，读取所选来源的节假日标记。
- **调整外观**：支持深浅模式、文字缩放和自定义背景。默认使用主题底色，不附带背景图片。
- **保护本地修改**：保存使用事务，多窗口修改和过期草稿发生冲突时明确提示。

项目不提供第三方插件扩展、PWA 或单文件离线 HTML 发布。

## Android 与网页版

| 能力 | Android App | GitHub Pages |
|---|---|---|
| 课表浏览、编辑、搜索、文件导入与 JSON 导出 | 支持 | 支持 |
| 教材、自定义背景、深浅模式 | 支持 | 支持 |
| 数据保存 | App 自己的本地数据 | 当前浏览器、当前站点的本地数据 |
| 后台提醒、实时行程通知、系统日历 | 原生能力 | 不提供 |
| 版本更新 | GitHub Release 检查、下载 APK | 由 Pages 部署更新网页 |

Android 最低支持 **Android 8.0（API 26）**。网页版面向支持现代 Web 标准的浏览器，不与 App 自动同步数据；两端可通过课表 JSON 转移课程。清除应用或站点数据会删除本地内容，请定期导出课表。

## 开始使用

1. 安装 [Releases](https://github.com/tequed232/Dolphin-Schedule/releases) 中的 APK，或打开 [网页版](https://tequed232.github.io/Dolphin-Schedule/)。发布内容以 Releases 和 Pages 的实际部署为准。
2. 从主页导入课表文件，或手动添加第一门课程。只有截图或教务网页时，可使用导入页的转换提示词，先整理为 JSON。
3. 核对预览中的开学日期、周数、节次时间与课程，确认后保存。
4. 按需填写教材、设置学校和外观；Android 用户可开启提醒或使用系统日历。

**更名版本的安装身份已变化。** 正式包名为 `com.dolphin.schedule`，debug 包为 `com.dolphin.schedule.debug`，数据库名为 `dolphin-schedule`。更名后的版本按新 App 安装，不能覆盖更名前的旧包；请先从旧 App 导出课表 JSON，再导入新版。JSON 不包含教材封面、背景和应用偏好，详见 [迁移说明](docs/data-and-migration.md)。

## 技术栈

| 层级 | 技术 |
|---|---|
| 共用界面 | React 19、TypeScript 5.9、CSS |
| 界面构建 | Vite 7、Node.js 24、npm |
| Android 宿主 | Kotlin 2.0、AndroidX Core / WebKit、WebViewAssetLoader |
| 本地数据 | IndexedDB；原生提醒镜像使用 SharedPreferences |
| Excel 导入 | `@e965/xlsx`，在导入时延迟加载 |
| Android 构建 | Gradle 8.13、AGP 8.13、JDK 17、Android SDK 36 |
| 验证 | Node test runner、tsx、Playwright、JUnit、Android lint |

精确依赖版本见 [package.json](package.json)、[Gradle 配置](app/build.gradle.kts) 和 [架构说明](docs/architecture.md)。

## 仓库目录

完整的 **[文件目录与放置规则](docs/directory-structure.md)** 包含目录树、根目录配置、Android/界面关键文件、测试和脚本用途，以及生成目录和本机配置的维护方式。下面是总览：

主要目录内也有中文 `README.md`，进入目录即可查看用途和维护入口。提交说明采用 `feat`、`fix`、`build`、`docs` 等英文类型配合中文描述，约定见 [开发说明](docs/development.md#中文提交说明)。

```text
app/                         Android 宿主、系统能力、原生单元测试
ui/
  src/
    screens/                 页面
    components/              课表、课程编辑器、弹层等共用组件
    state/                   应用状态、事务、同步与编辑冲突
    lib/                     模型、导入、存储与原生通信
    nav/                     页面切换与返回手势
    theme/                   主题与布局
    assets/                  本地图标和统一品牌资源
tests/
  unit/ browser/ android/    逻辑、浏览器及设备验证
  helpers/ fixtures/         测试工具与合成数据
  integrity/                 工程约束和资源完整性
scripts/
  build/ check/ dev/ release/ 构建、检查、开发及交付工具
docs/                        当前使用与维护说明
legal/                       第三方许可证
gradle/                      Gradle Wrapper
.githooks/                   可选安装的源码准入钩子
.github/workflows/           验证、源码准入和 Pages 部署
```

`ui/dist/`、`app/build/` 和根目录 `build/` 是生成输出，不进入 Git。测试数据不打入 APK。新增页面、业务逻辑、原生能力或资源时，可按 [文件放置规则](docs/directory-structure.md#新内容应该放在哪里) 选择位置；技术分层、数据流和资源生成流程见 [架构说明](docs/architecture.md)。

## 本地开发

安装 Node.js 24 后，从仓库根目录执行：

```sh
npm ci
npm run dev
```

开发入口为 `http://127.0.0.1:5173`。常用验证命令：

```sh
npm run build
npm run check
npm test
npm run check:ui
```

构建 Android 还需 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。配置 SDK 路径后，在 Windows PowerShell 执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

macOS / Linux 使用 `bash ./gradlew`。Gradle 会构建共用界面并同步内置资源；debug APK 输出为 `app/build/outputs/apk/debug/app-debug.apk`。详细环境、Pages 子路径验证和设备测试见 [开发说明](docs/development.md)。

## 文档导航

| 想了解什么 | 文档 |
|---|---|
| 导入、导出、数据范围与旧版迁移 | [数据与迁移](docs/data-and-migration.md) |
| 日期、列表 / 平铺、拖动和返回 | [导航交互](docs/navigation.md) |
| 背景、主题和品牌资源 | [外观与资源](docs/appearance.md) |
| 提醒、行程和系统展示 | [通知说明](docs/notifications.md) |
| Android 权限及触发时机 | [权限说明](docs/permissions.md) |
| 每层目录、关键文件和新增内容的放置位置 | [文件目录指南](docs/directory-structure.md) |
| 技术栈、目录边界与数据流 | [架构说明](docs/architecture.md) |
| 开发环境、命令与验证 | [开发说明](docs/development.md) |
| 正式签名、APK 交付与 Pages 部署 | [发布说明](docs/release.md) |
| 可提交文件、生成输出与 Git 钩子 | [源码准入](docs/source-policy.md) |
| 未发布变化与版本记录 | [版本记录](RELEASE_NOTES.md) |

作者：[tequed232](https://github.com/tequed232/)。[反馈问题](https://github.com/tequed232/Dolphin-Schedule/issues) 时，请说明版本、运行平台、重现步骤和实际结果；Android 请附系统及 WebView 版本，网页请附浏览器版本。分享课表、截图或日志前，请移除个人信息。

## 许可

项目业务代码采用 [MIT 许可证](LICENSE)，版权归 tequed232 所有。使用、修改或分发时请保留版权声明和许可证文本。第三方依赖遵循各自许可，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `legal/`。作者提供的品牌美术资源授权单独说明，见 [资源说明](docs/appearance.md)。
