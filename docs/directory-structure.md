# 文件目录与放置规则

本指南说明仓库各目录、关键文件和生成输出的作用，适合第一次阅读代码或准备增加功能时查阅。系统技术栈和数据流见 [架构说明](architecture.md)，运行命令见 [开发说明](development.md)。

各主要目录的 `README.md` 提供就近阅读入口，说明本目录用途、关键文件和常用命令。GitHub 文件列表中的说明来自最近一次提交，标题与正文按 [中文提交说明](development.md#中文提交说明) 编写。

## 从哪里开始读

| 想了解或修改的内容 | 建议入口 |
|---|---|
| 应用启动与页面组织 | `ui/src/main.tsx` → `ui/src/App.tsx` → `ui/src/screens/` |
| 课表模型、导入和本地保存 | `ui/src/lib/model.ts`、`fileImport.ts`、`storage.ts`，再看 `ui/src/state/AppState.tsx` |
| 课程列表、平铺和编辑 | `ui/src/screens/Home.tsx`、`ui/src/components/DayTimetable.tsx`、`TimetableGrid.tsx`、`CourseEditor.tsx` |
| Android 系统能力 | `app/src/main/AndroidManifest.xml` → `app/src/main/java/com/dolphin/schedule/MainActivity.kt` |
| 安装包如何生成 | `app/build.gradle.kts` → `ui/vite.config.ts` |
| 网页如何发布 | `.github/workflows/pages.yml` → `ui/vite.config.ts` |

表中省略路径前缀的文件，均位于同一行明确写出的目录内。

## 目录总览

```text
.
├── app/                              Android 应用模块
│   ├── build.gradle.kts              Android 配置与界面资源生成任务
│   ├── proguard-rules.pro            release 代码压缩规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   应用身份、权限与组件声明
│       │   ├── java/com/dolphin/schedule/
│       │   │                         Kotlin 宿主与系统能力
│       │   └── res/                 主题、通知图标与 XML 配置
│       └── test/java/com/dolphin/schedule/
│                                     Kotlin/JUnit 单元测试
├── ui/                               Android 与 Pages 共用界面
│   ├── index.html                    Vite 的 HTML 入口
│   ├── vite.config.ts                开发服务器与界面打包配置
│   ├── tsconfig.json                 界面 TypeScript 检查范围
│   └── src/
│       ├── main.tsx                  React 启动入口
│       ├── App.tsx                   页面、弹层、导航与应用级交互
│       ├── meta.ts                   应用名称、版本和作者链接
│       ├── screens/                  完整页面
│       ├── components/               共享 UI 与课表交互组件
│       ├── lib/                      模型、解析、存储及能力封装
│       ├── state/                    应用状态及状态相关 Hook
│       ├── nav/                      导航、返回和宿主导航协作
│       ├── theme/                    样式、外观与系统安全区
│       └── assets/                   品牌图、图标与资源清单
├── tests/                            界面和业务验证
│   ├── unit/                         TypeScript 逻辑测试
│   ├── browser/                      Playwright 浏览器回归
│   ├── android/                      指定设备的原生检查脚本
│   ├── integrity/                    工程约束与资源完整性检查
│   ├── helpers/                      测试共用工具
│   └── fixtures/                     合成课表和规则自测夹具
├── scripts/                          仓库维护与交付入口
│   ├── build/                        Android 构建辅助工具
│   ├── check/                        源码准入与回归编排
│   ├── dev/                          开发期资源生成工具
│   └── release/                      正式 APK 校验与本地交付
├── docs/                             使用、架构、开发与发布说明
├── legal/                            第三方许可证正文
├── gradle/wrapper/                   固定版本的 Gradle Wrapper
├── .githooks/                        可选安装的提交与推送检查
└── .github/workflows/                GitHub Actions 验证及 Pages 部署
```

`app/src/main/java/` 沿用 Android 的源码目录约定，内部文件使用 Kotlin；无需另建一套重复的 Kotlin 源码目录。

## 根目录配置文件

| 文件 | 作用 | 维护时机 |
|---|---|---|
| `README.md` | 项目首页、使用入口、平台差异和文档导航 | 功能、目录或使用方式变化时 |
| `RELEASE_NOTES.md` | 未发布变化与版本记录 | 功能变更和发布时 |
| `THIRD_PARTY_NOTICES.md`、`LICENSE` | 依赖/资源说明与项目许可 | 引入、替换或移除依赖和资源时 |
| `package.json` | npm 命令、界面依赖和工具版本 | 增改命令或依赖时 |
| `package-lock.json` | 固定 npm 依赖解析结果 | 通过 npm 更新依赖时同步生成 |
| `settings.gradle.kts` | Gradle 仓库、项目名和模块列表 | 模块或构建仓库调整时 |
| `build.gradle.kts` | Android/Kotlin Gradle 插件版本 | 构建工具升级时 |
| `gradle.properties` | Gradle JVM、AndroidX 和 Kotlin 构建选项 | 构建环境调整时 |
| `gradlew`、`gradlew.bat` | Gradle Wrapper 启动脚本 | 随 Wrapper 升级，通常不手改 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 下载地址、版本与校验配置 | Wrapper 升级时 |
| `gradle/wrapper/gradle-wrapper.jar` | Wrapper 引导程序 | 与 Wrapper 一起更新 |
| `release-identity.json` | 正式包名和发行证书 SHA-256，不含私钥 | 有明确的发行身份调整时 |
| `source-policy.json` | 可提交路径、类型及必需文件规则 | 增加目录、长期文档或必要资源时 |
| `.editorconfig` | 编辑器共用的文本格式约定 | 格式规范调整时 |
| `.gitattributes` | 换行符和二进制文件处理方式 | 增加资源类型或调整跨平台规则时 |
| `.gitignore` | 排除构建输出、依赖、本机配置和签名材料 | 增加本地生成目录时 |

版本以 `ui/src/meta.ts` 为入口，修改时同步 npm 版本与版本记录，具体规则见 [发布说明](release.md)。

## Android 目录与关键文件

以下 Kotlin 文件位于 `app/src/main/java/com/dolphin/schedule/`：

| 文件 | 负责什么 |
|---|---|
| `MainActivity.kt` | 创建 WebView、加载内置界面、接收内部消息、处理权限、返回手势和系统选择器 |
| `NativeNavigationView.kt` | Android 原生一级导航及其选中状态和布局 |
| `ReminderScheduler.kt` | 原生课表镜像、闹钟安排、接收提醒和系统时间/应用更新事件 |
| `ReminderPlanning.kt` | 计算下一批课前提醒的时间和课程集合 |
| `ReminderValidation.kt` | 核对待触发提醒与当前课程，拒绝失效或过期的提醒 |
| `Notifications.kt` | 课前提醒、导航行程、小伙伴及通知操作的生命周期 |
| `CourseNotificationPresentation.kt` | 课程通知的标题、正文和短地点等展示字段 |
| `LiveNotificationSupport.kt` | 系统实时通知能力与实际提升状态检查 |
| `CalendarSync.kt` | 写入、清理与复原应用自己的课程日历 |
| `CalendarHistory.kt` | 日历复原历史及可见性处理 |
| `SystemHolidays.kt` | 读取用户选择的系统日历来源及节假日数据 |
| `AppUpdates.kt` | 正式 Release 检查、更新调度和 APK 下载 |
| `UpdateVersion.kt` | 更新版本的解析与比较规则 |
| `DocumentExport.kt` | 用户主动导出文本文件的 Android 保存流程 |

`app/src/main/res/drawable/` 保存单色通知图标，`drawable-nodpi/` 保存通知彩图，`values/styles.xml` 定义宿主主题，`xml/` 保存文件共享和数据提取规则。启动器图标从共用品牌源图生成，不在 `res/` 再存一份品牌原图。

`app/src/test/` 是原生逻辑的 JUnit 测试；需要真实 Android 环境的交互脚本放在根目录 `tests/android/`。两者分别验证纯逻辑和设备行为。

## 共用界面的职责划分

本节路径均相对于 `ui/src/`。

| 目录或文件 | 负责什么 | 放置原则 |
|---|---|---|
| `screens/` | 主页、搜索、导入、设置、背景、系统日历等页面 | 面向完整用户任务，组合组件和状态 |
| `components/` | 日课表、平铺、课程编辑器、详情、月历、弹层和导航 | 可组合的 UI 和具体交互，避免直接承接整个应用状态 |
| `lib/model.ts` | 课程、课表、教材、偏好等类型与默认数据 | 公共数据形状的入口 |
| `lib/fileImport.ts`、`import.ts`、`importTime.ts`、`importPreview.ts` | 文件识别、课程/作息解析、校验及预览 | 新格式复用同一预览和确认流程 |
| `lib/storage.ts`、`stateTransactions.ts`、`migration.ts` | IndexedDB、事务/版本控制与旧数据整理 | 读写规则集中维护，不在页面另存数据副本 |
| `lib/editConflicts.ts`、`homeFocus.ts`、`gridLayout.ts`、`scheduleTime.ts` | 编辑冲突、阅读焦点、布局和作息计算 | 可独立验证的业务规则 |
| `lib/native.ts`、`export.ts`、`releases.ts` | 内部原生通信、文本导出和 Release 数据处理 | 页面通过能力封装调用宿主 |
| `lib/background.ts`、`cover.ts` | 自定义背景和教材封面的图片处理 | 与界面展示、品牌源资源分开 |
| `state/AppState.tsx` | 应用初始化、统一状态、保存与原生同步 | 保存成功后才发布状态 |
| `state/useAutosavedText.ts`、`useHomeFocus.ts`、`useAppUpdates.ts`、`useHolidayReader.ts` | 文本草稿、阅读位置、更新及日历异步状态 | 生命周期与状态协作放在 Hook 中 |
| `nav/useNavigation.ts`、`usePrimaryNavigation.ts` | 页面栈、返回手势、一级导航与原生导航协作 | 页面切换规则统一维护 |
| `theme/` | 主题、组件/页面样式、安全区与外观 Hook | 样式跟随所属功能，公共令牌集中维护 |
| `assets/brand/`、`assets/icons/`、`assets/manifest.json` | 品牌源图、SVG 图标、许可与完整性清单 | 只保存必要本地资源，替换时同步清单 |

页面从 `state/` 获取业务状态，通过 `lib/` 的规则和存储封装提交修改；`components/` 负责展示和交互。Android 与 Pages 共用这条数据流，只有系统能力由 Android 宿主承接。

## 测试、维护脚本和自动化

| 路径 | 用途 |
|---|---|
| `tests/unit/` | 导入、数据恢复、课次计算等 TypeScript 逻辑测试，入口为 `npm test` |
| `tests/browser/` | 用户流程、编辑冲突、持久化、外观和响应式回归；Pages 检查也在这里 |
| `tests/android/` | 提醒、系统日历、原生导航和更新等设备脚本，运行前指定隔离设备 |
| `tests/integrity/` | 工程不变量与资源清单检查，随 `npm run check` 执行 |
| `tests/helpers/` | 浏览器启动、导航等测试工具，应用运行时不引用 |
| `tests/fixtures/` | 合成课表及规则引擎自测数据，既不是真实用户数据，也不作为默认课表 |
| `scripts/build/android.ps1` | Windows Wrapper 构建、原生单元测试和 lint 入口 |
| `scripts/check/verify.mjs` | `npm run check` 的总入口 |
| `scripts/check/ci.mjs` | 启动独立测试服务器并编排浏览器测试组 |
| `scripts/check/check-source-files.mjs` | 文件、暂存内容或指定提交的源码准入 |
| `scripts/check/check-source-push.mjs` | 推送提交与新增历史的准入检查 |
| `scripts/dev/vendor-icons.cjs` | 从锁定依赖生成 Material 图标子集并保留项目图标 |
| `scripts/release/check-release.ps1` | 核对正式 APK 的包名、版本、证书及可选覆盖升级条件 |
| `scripts/release/deliver.ps1` | 整理已校验的本地发行 APK 和哈希清单 |
| `.githooks/pre-commit`、`pre-push` | 可选安装的本地提交/推送入口，不由 npm 安装自动启用 |
| `.github/workflows/verify.yml` | 界面、逻辑、浏览器和 Android 构建验证 |
| `.github/workflows/source-admission.yml` | 独立检查源码和新增历史 |
| `.github/workflows/pages.yml` | 构建同一套界面并部署到 GitHub Pages |

`docs/` 保存长期维护说明，由 [README 文档导航](../README.md#文档导航) 串联；`legal/` 和 `ui/src/assets/icons/LICENSE` 保存相应许可正文，依赖归属见 [第三方说明](../THIRD_PARTY_NOTICES.md)。

## 本地生成目录与配置

下列内容可能在开发后出现，均不属于要提交的源码：

| 路径 | 从哪里来 | 如何维护 |
|---|---|---|
| `node_modules/` | `npm ci` 安装的依赖 | 依据锁文件重新安装，不手动修改后提交 |
| `ui/dist/` | Vite 构建结果 | 修改 `ui/src/` 后重新构建，不直接编辑产物 |
| `app/build/generated/assets/ui/` | Gradle `syncUi` 复制界面产物 | 由构建同步，APK 从这里获得内置页面 |
| `app/build/generated/res/brand/` | Gradle `syncBrand` 生成启动器资源 | 修改共用品牌源图后重新生成 |
| `app/build/outputs/`、`app/build/reports/` | Android APK、测试与 lint 输出 | 按实际构建生成，不纳入源码 |
| 根 `build/` | 测试证据、截图、临时报告或本地工具链 | 与 `scripts/build/` 区分；后者是跟踪的脚本源码 |
| `releases/` | 本地交付脚本整理的文件 | 用于上传发行附件，不作为源码目录 |
| `.gradle/`、`.kotlin/` | 本机构建缓存 | 由构建工具管理 |
| `.idea/`、`.run/`、`*.iml` | 个人 IDE 配置 | 保持本机私有 |
| `local.properties` | 本机 Android SDK 路径 | 按当前电脑配置，不提交绝对路径 |
| `signing.local.properties`、`.signing/` | 发行私钥路径与密码、密钥文件 | 由作者保管，不提交或上传到测试产物 |

Pages 直接发布工作流上传的 `ui/dist/`，无需创建提交生成文件的网页分支。用户的课表、教材和背景存放在运行环境的本地数据库中，不放在仓库目录里。

## 新内容应该放在哪里

- 新增页面：放入 `ui/src/screens/`，复用 `components/`；页面切换接入 `App.tsx` 与 `nav/` 的现有规则。
- 新增共用组件：放入 `ui/src/components/`；对应样式放入 `theme/` 并由使用入口引用。
- 新增业务计算或解析：放入 `ui/src/lib/`，相关逻辑测试放入 `tests/unit/`；涉及状态生命周期时再由 `state/` 的 Hook 组织。
- 新增 Android 系统能力：放入现有 Kotlin 包，由 `MainActivity.kt` 和 `ui/src/lib/native.ts` 的内部桥接入；权限变化同步 Manifest 与 [权限说明](permissions.md)。
- 新增品牌或图标资源：放入 `ui/src/assets/`，同步资源清单和许可证；需要原生副本时通过 Gradle 生成。
- 新增测试样本：放入 `tests/fixtures/` 并保持合成数据；运行时页面不导入这些文件。
- 新增维护命令：放入相应 `scripts/` 子目录，必要时在 `package.json` 注册入口，并同步 [开发说明](development.md)。
- 新增长期文档或移动文件：更新 README 的入口、引用路径与 `source-policy.json`，检查 Git 中的精确大小写；提交范围见 [源码准入](source-policy.md)。

目录变化不仅是移动文件：还需同步模块导入、Gradle/Vite 输入输出、测试路径、工作流和文档链接。保持运行时、测试数据、生成产物与本机私有配置各有明确位置。
