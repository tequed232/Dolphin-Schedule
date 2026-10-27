# 文件目录与放置规则

目录按职责划分：`app/` 放应用、`docs/` 放长期说明、`scripts/` 放维护工具、`tests/` 放工程约束、`legal/` 放许可证。界面、业务和存储都在 Android 模块内，不再维护独立网页源码。

## 完整目录概览

```text
Dolphin-Schedule/
├─ app/
│  ├─ build.gradle.kts
│  ├─ proguard-rules.pro
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ kotlin/com/dolphin/schedule/
│     │  │  ├─ NativeActivity.kt
│     │  │  ├─ notifications/        系统行程卡片、令牌与接收器
│     │  │  ├─ ui/
│     │  │  │  ├─ timetable/         课表、日期、课程详情与拖动
│     │  │  │  ├─ settings/          设置首页与设置图标
│     │  │  │  ├─ appearance/        主题、背景与安全区页面
│     │  │  │  ├─ books/             教材管理与编辑
│     │  │  │  ├─ importing/         教务与文件导入界面
│     │  │  │  ├─ notifications/     行程开关、权限与状态界面
│     │  │  │  ├─ support/           更新、关于与首次引导
│     │  │  │  ├─ navigation/        胶囊与页面过渡
│     │  │  │  ├─ common/            主题、安全区和系统栏
│     │  │  │  ├─ DolphinApp.kt      页面组装
│     │  │  │  ├─ DolphinViewModel.kt 共享页面状态
│     │  │  │  └─ MainTab.kt          稳定的一级导航状态类型
│     │  │  ├─ domain/
│     │  │  ├─ data/
│     │  │  ├─ importing/
│     │  │  ├─ academic/
│     │  │  └─ media/
│     │  ├─ assets/academic/extract.js
│     │  └─ res/
│     ├─ test/kotlin/                  JVM 单元测试
│     └─ androidTest/kotlin/           Compose 与系统交互测试
├─ docs/                            当前使用与维护文档
├─ scripts/
│  ├─ build/                        构建、隔离验收包与测试夹具生成
│  ├─ check/                        源码、历史与工程检查
│  └─ release/                      正式 APK 校验和交付
├─ tests/
│  ├─ integrity/                    原生架构、资源和版本约束
│  └─ fixtures/                     检查工具自身的合成规则样本
├─ legal/                           第三方许可证全文
├─ gradle/wrapper/                  Gradle 启动器和版本配置
├─ .github/workflows/               Android 构建与源码准入
├─ .githooks/                       可选本地 Git 钩子
├─ gradle.properties                应用版本与 Gradle 配置
├─ build.gradle.kts                 根构建插件版本
├─ settings.gradle.kts              模块和依赖仓库
├─ package.json / package-lock.json Excel 解码依赖与维护命令
├─ source-policy.json               可提交源码与资源的准入规则
├─ release-identity.json            公开包名及发行证书指纹
├─ README.md                        产品、安装和开发入口
├─ RELEASE_NOTES.md                 用户可读的版本变化
├─ THIRD_PARTY_NOTICES.md            第三方来源、用途与许可
└─ LICENSE                          项目 MIT 全文
```

Kotlin 主源码、JVM 测试和设备测试分别统一位于 `src/main/kotlin/`、`src/test/kotlin/`、`src/androidTest/kotlin/`。文件夹与 Kotlin 包名保持对应；应用根包只保留启动入口。

## Android 目录与关键文件

以下路径均相对 `app/src/main/kotlin/com/dolphin/schedule/`。

| 文件或目录 | 作用 | 放置边界 |
|---|---|---|
| `NativeActivity.kt` | 正式启动、系统结果与通知返回路由 | Activity 负责系统生命周期，不塞入课程解析规则 |
| `ui/DolphinApp.kt` | 页面框架、主题、胶囊和弹窗接线 | 组合页面，不直接操作 SQLite |
| `ui/DolphinViewModel.kt` | 一级导航、选中日期、课程编辑和功能控制器 | 跨页面状态及用户意图 |
| `ui/timetable/TimetableScreens.kt` / `TimetableBody.kt` | 列表和平铺、时间轴与课程布局 | 原生绘制、滚动和手势 |
| `ui/timetable/LayoutCourse.kt` / `LayoutController.kt` | 长按预览、把手、滚动和确认保存 | 手势状态与业务规则分离 |
| `ui/books/` / `ui/appearance/` / `ui/support/` | 教材、外观、更新、关于和引导 | 各功能页面及状态 |
| `domain/` | 课程模型、日期、布局、导入、教材关联和校验 | 尽量保持可独立 JVM 测试，不直接操作 UI 或网络 |
| `data/NativeRepository.kt` | SQLite 事务、已提交快照与冲突保护 | 一份应用持久数据，不为每个页面复制数据库 |
| `data/AppDataCodec.kt` | 备份和数据库 JSON 编解码 | 兼容输入、边界校验与有界数据 |
| `data/LegacyDataReader.kt` | 首次同包升级读取旧 IndexedDB | 本地、只读、不加载旧界面 |
| `data/*DraftStore.kt` | 导入和编辑草稿的私有文件 | 不把大图片或整份文件塞进 Activity 状态 |
| `importing/` | 文件格式识别、Excel 解码、映射、预览和草稿 | 解码单元格与生成课表分别处理 |
| `academic/` | 教务浏览器、访问白名单、会话和结果交接 | 不接管密码，不开放任意应用桥 |
| `media/` | 本地图片压缩、方向校正和有界缓存 | 图片只在本机处理 |
| `notifications/Notifications.kt` / `JourneyState.kt` | 导航后的行程展示、令牌和旧功能清理 | 不创建课前提醒或常驻桌宠 |
| `notifications/NotificationRoute.kt` | 点击通知后的原生课程路由 | 校验目标，避免错误打开过期课程 |

## 资源放在哪里

| 路径 | 用途 |
|---|---|
| `app/src/main/res/drawable-nodpi/app_icon.webp` | 唯一品牌源图和应用图标 |
| `app/src/main/res/drawable-nodpi/live_icon.webp` | 系统通知展示所需的小尺寸品牌图 |
| `app/src/main/res/drawable/` | 系统单色通知图标等 XML 图形 |
| `app/src/main/res/values/` | Android 主题等系统资源 |
| `app/src/main/res/xml/` | 文件共享及系统备份规则 |
| `app/src/main/assets/academic/extract.js` | 允许学校的课表页面提取脚本 |
| `app/build/generated/assets/spreadsheet/` | Gradle 从锁定依赖复制的 Excel 解码器，不手工提交 |
| `app/build/generated/assets/legal/` | Gradle 从许可源文件同步的离线正文 |
| `app/build/generated/test-assets/` | 测试工作簿，只进入测试 APK |

默认不分发背景图片；用户背景和教材封面属于设备私有数据。新的应用必要资源要满足 `source-policy.json`，不能用放宽整个资源目录的方式提交验收截图。

## 测试、工具和文档

- `app/src/test/`：日期、周次、布局、导入、备份、版本和并发规则的单元测试。
- `app/src/androidTest/`：Compose、SQLite、文件选择、导入、迁移和导航等设备测试。会替换合成数据的测试仅允许隔离模拟器。
- `tests/integrity/`：确认原生启动、版本来源、权限、必要资源与已删除功能等工程约束。
- `tests/fixtures/source-policy.json`：源码检查器的合成政策，不能代替实际根政策。
- `scripts/build/`：调用构建、生成隔离验收包配置和合成工作簿。
- `scripts/check/`：当前源码、暂存内容、推送历史和工程完整性检查。
- `scripts/release/`：核对包名、版本、发行证书及 APK 哈希，整理交付目录。
- `docs/`：长期维护的用户和开发说明；临时运行报告不放在这里。
- `legal/`：第三方许可全文；项目业务许可仍以根 `LICENSE` 为准。

## 生成目录与本机配置

| 路径 | 内容 | 是否提交 |
|---|---|---|
| `build/` | 工具链、模拟器、证据、日志、迁移备份及临时验收配置 | 否 |
| `app/build/` | 编译缓存、生成资源、测试报告及 APK | 否 |
| `node_modules/` / `.gradle/` | 已安装依赖及构建缓存 | 否 |
| `releases/` | 本地交付 APK 与 SHA256 清单 | 否，发布附件由 GitHub Releases 管理 |
| `local.properties` | 本机 Android SDK 路径 | 否 |
| `signing.local.properties` / `.signing/` | 本机签名配置、密钥与密码 | 否 |

## 新内容应该放在哪里

新增页面放入 `ui/` 下对应功能目录；仅全局页面框架与共享路由留在 `ui/` 根层。系统行程放 `notifications/`，版本比较放 `domain/`。可复用业务计算放 `domain/`；只有数据落盘、文件或迁移相关代码进入 `data/`。新导入格式放 `importing/`，学校适配放 `academic/` 和对应提取脚本。系统能力在 Kotlin 中接入，并同步 Manifest、权限文档及相关设备测试。

新增文件时同步源码准入、构建输入、文档链接和必要测试。删除功能时一并删除入口、实现、权限、接收器、测试和旧说明，不能只隐藏按钮。提交说明中的 `feat`、`fix`、`build`、`docs` 保持英文类型，冒号后使用中文解释目的。
