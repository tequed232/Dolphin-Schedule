<p align="center"><img src="app/src/main/res/drawable-nodpi/app_icon.webp" width="76" height="76" alt="Dolphin Schedule 图标" /></p>

# Dolphin Schedule

Dolphin Schedule 是一款 **Android 本地课表应用**，使用 **Kotlin＋Jetpack Compose** 编写。支持教务与文件导入、课程编辑、教材管理、地图导航和个性化外观，课表与图片保存在设备上，无需注册 Dolphin 账号。

[下载 Android 安装包](https://github.com/tequed232/Dolphin-Schedule/releases) · [导入与数据迁移](docs/data-and-migration.md) · [反馈问题](https://github.com/tequed232/Dolphin-Schedule/issues)

当前源码版本为 **1.6.0**，最低支持 **Android 8.0（API 26）**。已发布版本和安装附件以 Releases 为准。

## 主要功能

- **列表与平铺课表**：日期条、月历、下一节课、课程搜索、跨节课程块、横向浏览与边缘翻周，保留青色强调色和圆角卡片。
- **课程与作息编辑**：课程增删改、复杂周次、指定日期临时课程、长按移动、上下把手调整节次；点击左侧时间编辑或删除空闲节次。自动收起未占用的午休时段，显示当前时间线。
- **教务导入**：首版适配 `jwxt.gdcmxy.edu.cn:1004`。在 App 内自行登录，进入个人学期课表后识别，核对开学日期、逐节作息和课程，确认后替换；支持撤销上次导入及选择是否记住登录。
- **文件导入**：支持 JSON、CSV、XLS、XLSX，多工作表选择、字段映射和错误明细。识别结果先预览，确认前不会改动已保存的课表。
- **教材管理**：记录书名、出版社、版次和备注，从相册选封面或调用系统相机拍摄；同名课程共用教材。
- **课程导航**：设置学校与高德/百度地图后，手动打开地图。导航成功后可显示行程通知，并请求系统实时活动/灵动岛展示；点“我到了”结束行程。
- **外观与备份**：浅色、深色、跟随系统、动态取色、文字大小、自选背景、模糊和安全区；完整备份可转移课表、教材、背景和偏好。
- **简单更新与关于**：检查 GitHub 正式版本，通过浏览器下载；App 内可离线查看 MIT、第三方说明和 Apache 2.0 许可全文。

App 默认显示主题底色，不内置背景图片。通知仅用于用户主动导航后的行程，不提供课前提醒、桌宠或台词功能。系统是否显示灵动岛/实时活动，取决于 Android 版本、通知权限和厂商实现，详见 [通知说明](docs/notifications.md)。

## 开始使用

1. 从 [Releases](https://github.com/tequed232/Dolphin-Schedule/releases) 下载并安装 APK。
2. 在首次引导中选择“导入课表”或“手动添加课程”，也可以先进入空课表。
3. 导入时先核对开学日期、学期周数、节次时间和课程，再确认保存；教务未提供日期时先手动设置。
4. 按需填写教材、学校和外观。在“设置 → 数据与备份”定期导出完整备份。

卸载或清除应用数据会删除本地内容。正式包为 `com.dolphin.schedule`，调试包另加 `.debug`，不同包名的数据彼此独立。更名前的旧包不能直接覆盖安装，请先导出课表 JSON；同包旧版本首次升级时可读取本机旧数据并转入原生数据库，详见 [迁移说明](docs/data-and-migration.md)。

## 内置教务导入的 WebView 兼容性

**目前明确确认可用的系统 WebView 版本是 `154.0.8037.57`。** 2026-10-27 在 RMX6688 真机通过 ADB 复验：保留数据升级后，教务页可以打开，记住的登录状态保留，并进入真实学校课表的识别预览。以下记录区分实际使用结果与尚待验证的兼容代码：

| 系统 WebView 版本 / 环境 | 内置教务导入状态 | 说明 |
|---|---|---|
| `154.0.8037.57` | 已确认可用 | RMX6688 真机复验通过网页加载、登录保留和课表识别预览；此前用户也确认导入正常。不代表所有厂商的同版本设备均已测试 |
| `114.0.5.302` | 已有不可用反馈，兼容修复待验证 | 已发布版本存在兼容问题；当前源码新增旧内核隔离路径，但尚未在这个确切版本上完成登录和课表导入验收 |
| 其他版本 | 按运行时能力判断，未逐个版本验证 | 版本号本身不能保证学校页面和登录流程可用 |

当前源码优先使用 WebView 的 `MULTI_PROFILE` 能力隔离教务登录；不支持该能力时，在 **Android 9.0（API 28）及以上**使用专用进程和独立 WebView 数据目录。Android 8.x 上若缺少多 Profile 能力，则需要更新系统 WebView，或改用文件导入。这里的 Android 9.0 是兼容路径的系统要求，**不是 WebView 最低版本号**；兼容路径属于尚未发布的源码变更，不能据此认定已下载的 v1.6.0 安装包已经支持旧内核。

暂时不承诺“WebView 某一版本以上全部可用”：不同厂商提供的能力和学校页面兼容性可能不同。实现依据见 Android 官方的 [WebView 独立数据目录](https://developer.android.com/reference/android/webkit/WebView#setDataDirectorySuffix(java.lang.String)) 和 [WebKit Profile](https://developer.android.com/reference/androidx/webkit/Profile) 说明。

遇到问题时，请在系统应用信息中查看 **Android System WebView**，或在开发者选项的“WebView 实现”中确认当前提供组件及版本；Chrome 版本不一定等于 App 实际使用的 WebView 版本。反馈时附上 App、Android 和 WebView 版本，以及停在登录、加载还是识别阶段。加载或识别失败不会替换已有课表，仍可使用文件导入。

## 技术栈

| 层级 | 技术与职责 |
|---|---|
| 界面 | Jetpack Compose、Material 3，原生页面与悬浮胶囊导航 |
| 业务与状态 | Kotlin 2.0.21、ViewModel、协程与 Flow，处理日期、周次、布局、导入及保存冲突 |
| 本地存储 | SQLite 事务快照；应用私有文件保存导入/编辑草稿和迁移备份 |
| 教务网页 | Android WebView 与 AndroidX WebKit，隔离登录环境、读取允许学校的课表 |
| Excel | 锁定的 SheetJS CE `@e965/xlsx 0.20.3` 在本地无界面 WebView 解码单元格，课程规则由 Kotlin 处理 |
| 构建 | Gradle 8.13、AGP 8.13.0、JDK 17、Android SDK 36 |
| 验证 | JUnit、Compose 设备测试、Android lint、Node 源码与工程约束检查 |

应用界面不依赖 React、Vite 或浏览器网页。保留的 WebView 分别用于学校登录、本地 Excel 解码和一次性旧数据迁移，边界见 [架构说明](docs/architecture.md)。不提供独立网页版、Pages/PWA 发布或第三方插件系统。

## 仓库目录

```text
app/
  src/main/
    kotlin/com/dolphin/schedule/
      ui/                    Compose 页面框架与共享状态
        timetable/           课表、课程详情、作息与拖动
        settings/            设置入口与图标
        appearance/          背景与外观页面
        books/               教材页面和编辑状态
        importing/           导入界面
        notifications/       行程设置与权限交接
        support/             引导、更新与关于
        navigation/          胶囊与页面过渡
        common/              主题、安全区与系统栏
      notifications/         系统行程卡片、令牌与接收器
      domain/                课程、日期、布局和业务校验
      data/                  SQLite、备份编解码与旧数据迁移
      importing/             文件解析、映射和导入草稿
      academic/              教务浏览与学校访问边界
      media/                 本地图片处理
    assets/academic/         学校课表提取脚本
    res/                     品牌图、主题、图标和系统配置
  src/test/                  Kotlin 单元测试
  src/androidTest/           模拟器与设备界面测试
docs/                        使用、架构、目录、开发和发布说明
scripts/                     构建、源码准入和交付工具
tests/integrity/              原生架构与资源不变量检查
legal/                       第三方许可证正文
gradle/wrapper/              固定 Gradle 版本的启动配置
.github/workflows/           构建验证与源码准入
.githooks/                   显式安装的提交/推送检查
```

[文件目录与放置规则](docs/directory-structure.md) 进一步解释根文件、各层职责、资源生成和新增文件的位置。`app/build/`、根 `build/`、`node_modules/` 和 `releases/` 为本机输出，不提交到源码仓库。

## 本地构建

准备 Node.js 24、JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0，配置 SDK 路径后，在仓库根目录执行：

```sh
npm ci --ignore-scripts
npm run check
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

macOS / Linux：

```sh
bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。npm 仅提供仓库检查、Excel 解码依赖和测试工作簿生成，不构建应用界面。设备测试、发行签名与详细环境见 [开发说明](docs/development.md) 和 [发布说明](docs/release.md)。

## 文档

| 内容 | 入口 |
|---|---|
| 导入格式、备份和旧版迁移 | [数据与迁移](docs/data-and-migration.md) |
| 课表手势与返回规则 | [导航交互](docs/navigation.md) |
| 背景、主题与时间轴 | [外观说明](docs/appearance.md) |
| 导航行程与灵动岛展示边界 | [通知说明](docs/notifications.md) |
| 系统权限及触发时机 | [权限说明](docs/permissions.md) |
| 文件放置与分层设计 | [目录指南](docs/directory-structure.md)、[架构说明](docs/architecture.md) |
| 构建、测试与发布 | [开发说明](docs/development.md)、[发布说明](docs/release.md) |
| 源码文件与提交规则 | [源码准入](docs/source-policy.md) |
| 教务适配和本版功能范围 | [教务导入](docs/decisions-academic-import.md)、[功能清单](docs/native-rebuild.md) |
| 版本变化 | [版本记录](RELEASE_NOTES.md) |

作者：[tequed232](https://github.com/tequed232/)。反馈问题时，请附版本、Android 版本、重现步骤与实际结果；涉及教务或 Excel 时补充系统 WebView 版本。截图和日志请移除个人信息。

## 许可

项目业务代码采用 [MIT 许可证](LICENSE)，Copyright (c) 2026 tequed232。第三方组件保留各自的版权声明与许可，见 [第三方说明](THIRD_PARTY_NOTICES.md) 和 [legal/](legal/README.md)。
