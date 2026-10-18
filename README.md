<p align="center">
  <img src="docs/images/app-icon.png" width="76" height="76" alt="Dolphin Calendar 图标" />
</p>

# Dolphin Calendar

**把课程、教材和上课提醒放在一起。**

Dolphin Calendar 是面向校园日常的本地课表应用。它用同一套 React 界面提供网页版和 Android 安装版：主页看课程，点击课程看教师、教室和教材，需要时再开启提醒、导航或系统日历。

无需账号，没有自建业务服务器，也不自动上传课表、封面或背景。Android 的基础课表界面和资源均随 APK 内置；地图导航使用用户选择的外部地图应用。当前源码版本为 **1.5.1**，正式安装包见 [GitHub Releases](https://github.com/tequed232/Dolphin-Calendar/releases)。版本变更见 [更新记录](RELEASE_NOTES.md)。源码仓库：[tequed232/Dolphin-Calendar](https://github.com/tequed232/Dolphin-Calendar)。

在线使用：[Dolphin Calendar 网页版](https://tequed232.github.io/Dolphin-Calendar/)。网页版发布运行 `npm run build:pages`，入口为 `build/pages/index.html`。该入口内置编译后的界面代码和样式，图片与离线缓存文件随整个目录发布，支持 `/Dolphin-Calendar/` 子路径。不要将开发用的 `web/index.html` 单独上传。发布工作流和 Pages 发布条件见 [发布说明](docs/PUBLISHING.md)。

[功能](#功能概览) · [JSON 格式](#json-课表格式) · [本地开发](#本地开发) · [架构](#工程架构) · [验证](#验证与测试) · [许可](#许可证与美术资产)

## 近期改进

- 学校名称与台词采用即时草稿和合并保存，保存失败时保留输入并提供重试。
- 多窗口保存使用数据库事务；导入预览、课程与教材编辑会检查旧草稿，并明确提示重新读取。
- 导入模块按需加载；文件重新预览保留工作表、手动映射和已确认的日期。
- 提醒同步保留待触发批次，系统日历复原保留课程的隐藏状态。
- 插件通过独立管理入口运行，权限、配置与主题接口见 [插件开发文档](docs/PLUGINS.md)。

## 功能概览

### 课表与导航

设置中的“课表管理”和主页的同名入口打开同一页面，集中课程、学期、每日节数、上下课时间和导入；页面内的“课表设置”区域复用现有配置组件，导出与恢复集中到“数据与备份”。新安装默认进入列表，已有显示模式沿用保存值。平铺默认处于浏览状态，长按或“调整布局”进入单门课程布局编辑；点击空白、返回和切换页面/模式可退出。导航顺序固定为“列表 / 平铺 / 搜索 / 设置”，竖屏置底、横屏或宽屏置于侧边，顶部不再重复切换。列表和平铺的顶部工具栏进入文档流，随内容滚动；说明换行时课程网格按真实高度占位。列表与平铺共享课程/日期/节次焦点，切换后保持阅读位置；二级任务、详情和弹层隐藏导航；返回任务时保留阅读位置。月历可左右连续拖动翻月并跨年；点年月标题后，同一套年份控件支持输入、左右箭头和左右拖动，月份选择继续保留。

平铺课表、临时课程、统一 CSV/Excel 导入、动态节次及焦点切换的格式、迁移和验证说明见 [开发升级说明](docs/DEVELOPMENT_UPGRADE.md)；本轮问题、修改与验收映射见 [交互验收清单](docs/NAVIGATION_INTERACTION.md)。

### 应用更新

设置 → 应用更新可开启每日自动检查，也可立即检查。发现比当前更新的正式 Release 后，主页右上角显示黄色下载入口。自动检查默认开启，只检查版本；应用内下载为独立开关，默认关闭。开启后仍需手动下载，完成后到系统下载列表打开 APK，由系统确认安装。也可直接前往作者 GitHub Release 页面。

检查只访问作者仓库的公开发布信息，不上传课表、封面或背景。Android 每日检查采用非精确调度，可能受省电影响；重启后重新打开 App 会恢复并补查。网页也支持手动检查；自动检查在网页打开期间执行，距上次成功检查满 24 小时后检查本仓库的正式 Release，缓存上次验证的结果并提供 Release 跳转。网页代码通过 Pages 发布更新，应用内 APK 下载仅限 Android。关闭自动检查后仍可手动检查；离线使用课表不要求版本检查成功。

### 实时通知与导航

课前提醒包含课程、教材、完整教室与用户台词。点击“去教室”后检查学校和楼栋，再打开地图；**地图定位楼栋，教室号仍在应用和通知中保留**。

导航行程由用户主动开启。允许通知且开启相关选项时，应用发布可结束的持续行程通知；点击“我到了”通过后台广播直接收起当前行程，不把用户带回 App。无行程时，通知栏小伙伴可按偏好显示台词与互动按钮。

Android 16 及以上会为符合条件的导航行程请求系统实时更新。系统版本、通知权限、渠道与设备策略共同决定它能否提升为流体云等形态；普通课前提醒不会被伪装成实时活动。较早系统使用标准通知。**ColorOS 的标准实时更新适配不等于接入 OPPO 专用服务卡片，MIUI / HyperOS 也没有专用模板的统一保证。** 详见 [通知流程与接入边界](docs/NOTIFICATION_INTEGRATION.md)。

Android 权限用于 **日历读写、系统通知及实时更新请求、版本检查与下载联网**。日历和通知按使用时申请，联网为普通权限。不请求自启动、后台弹出、悬浮窗或精确闹钟权限；不读取剪贴板。复制按钮只在明确点击时写入文本。提醒采用系统非精确调度，可能延迟，设备重启后需重新打开应用恢复安排。详见 [权限范围](docs/PERMISSIONS.md)。

### 背景与外观

- 自定义背景接受 JPG、PNG、WebP，单张不超过 **10 MB**；推荐 **1080×1920** 或 **1440×2560** 竖图，主体放在中央。
- 图片固定在底层，自动居中铺满，不跟随页面滚动。不同屏幕可能裁去边缘；大图在本机缩小处理，不改动原文件。
- 背景毛玻璃可在 **0–30 px** 间调节：拖动实时预览，松手保存；0 表示清晰原图。模糊只作用于背景。
- 导航采用清晰的实体表面，旧 Dock 的光泽、散射和扭曲选项已移除。原玻璃偏好仍保存在本地，背景毛玻璃、主题、缩放、安全区和性能模式继续可用。
- 本机另有 `codex/liquidglass` 实验工作树，探索完整界面的折射实现；**本次未上传该实验工作树或其分支**。仓库中的 `main` 不包含其新组件，旧版的半透明模糊测试不能证明实验实现的折射效果。

### 系统日历与节假日

在“课表管理 → 导入到系统日历”中，Android 安装版可以把实际上课日期写入独立的 Dolphin 课程日历。日程注明 **由 Dolphin Calendar 创建**，分别记录教师与规范教室位置；“复原”恢复上一次导入前的 Dolphin 课程日历状态。不会修改其他来源的系统日历。

在 **设置 → 主页显示 → 节假日标记** 开启功能、选择系统日历来源，再按年份读取。日期条和月历可显示 **休 / 补 / 节** 及不同颜色，**只加标记，不自动删课、调课或改变提醒**。如果 ROM 没有通过 Calendar Provider 公开节假日事项，应用无法读取它内部显示的假日数据。

### 网页版与 APK 的区别

| 能力 | 网页版 | Android APK |
|---|---|---|
| 列表/平铺课表、搜索、月历、JSON/CSV/Excel 导入及 JSON 导出 | 支持 | 支持 |
| 教材信息、背景和外观设置 | 支持 | 支持 |
| 封面选图 | 浏览器文件选择器 | 系统文件选择器 |
| 相机采集封面 | 取决于浏览器和设备的 `capture` 支持 | 系统相机 + FileProvider，无需应用 CAMERA 权限 |
| 打开地图 | 外部地图网页或应用链接 | 外部地图应用或浏览器 |
| 应用关闭后的课前提醒 | 不支持 | 原生闹钟与广播调度 |
| 系统持续通知与实时更新请求 | 不支持 | 支持，展示形态取决于系统 |
| 系统日历导入、复原与假日读取 | 不支持 | 获准后使用 Calendar Provider |
| 正式 Release 检查 | 手动及网页打开期间的每日检查；跳转 Release | 手动及系统每日调度；可选择应用内下载 |
| 离线使用基础课表 | 可双击离线版；网站版首次缓存后可用 | 网页、图标与默认图片随 APK 内置 |

网页、正式 APK 与调试 APK 的数据彼此独立，不会自动跨端同步。导出的 JSON 包含课表、学期和节次，**不包含教材封面、背景图片或全部偏好**。卸载应用、清除应用数据或浏览器站点数据会删除本地内容，请按需要导出课表。

## JSON 课表格式

主页和课表管理中的“导入课表”进入同一页面，选择文件后自动识别格式、校验、预览并确认；仅识别失败或扩展名与内容不符时需要指定格式。导入页分别提供 CSV、JSON 完整课表模板，每份同时包含课程、逐节作息与休息，内容与应用的统一模板同源。JSON 和 CSV 提供的自定义上/下课时间会同步到内置课时，并自动识别午休行、独立 `lunchBreak` 及 `08:00–08:45` 时间段；午休显示在课间，不占课程节数。旧课程表与单独时间表仍由原解析器兼容；预览列出识别结果。连堂课程只提供起止边界时保留中间时间，冲突或不完整时间会具体报错，不能据此声称未知逐节作息已确认。粘贴入口接受 JSON；文件入口支持 `.json`、`.csv`、`.xlsx` 和 `.xls`，均先预览再确认替换。正常单工作表文件直接进入预览；返回修改保留文件及解析结果，预览确认按钮固定在底部。Excel 支持工作表选择、纵向合并单元格和简单行列映射。截图、教务网页与 HTML 请先在外部工具中转换为 JSON；导入页提供统一 AI 提示词和外部图文视频教程。应用不会自动把文件上传给第三方工具。

以下是可直接导入的标准示例：

```json
{
  "term": {
    "name": "2026–2027 秋季学期",
    "startDate": "2026-09-01",
    "weeks": 20
  },
  "courses": [
    {
      "name": "线性代数",
      "teacher": "示例教师",
      "room": "16栋203号教室",
      "day": 1,
      "start": 1,
      "end": 2,
      "weeks": [1, 2, 3, 4, 5, 6],
      "color": "sage",
      "notes": "请携带教材"
    }
  ]
}
```

| 字段 | 含义 |
|---|---|
| `term.startDate` | 开学日期，`YYYY-MM-DD`；第 1 周从该日期所在周的周一计算 |
| `term.weeks` | 学期或学年的总周数，可为 1–999；导入不会用默认 20 周截断显式课程周次 |
| `courses[].day` | 周一为 1，周日为 7 |
| `courses[].start` / `end` | 连续的开始、结束节次，以节次表为准，不是小时 |
| `courses[].weeks` | 实际上课周次数组；单双周请明确保留对应周次 |
| `courses[].room` | 尽量写成 `16栋203号教室` 或 `博学楼A203号教室`，不带学校名 |
| `courses[].teacher` / `notes` | 教师与备注；不确定的信息可以留空 |
| `courses[].color` | 可选：`sage`、`lavender`、`peach`、`blue`、`rose` |

未提供开学日期、学期周数或节次时间时保留当前设置；显式课程周次超出学期时扩展周数。没有提供 `periods` 时沿用当前节次表；新安装默认 12 节。需要自定义时可添加 `periods` 数组，例如 `[{"start":"08:00","end":"08:45"},{"start":"08:55","end":"09:40"}]`，课程节次不能超过数组长度。时间采用 24 小时制，同一节开始早于结束，节次之间不重叠；最多 24 个节次。

### 整学年与不完整数据

未提供开学日期时保留当前开学日期，未提供总周数时保留当前周数；课程包含更晚周次时会扩展学期长度并在预览中提示。缺少周次的课程按最终学期长度每周上课处理，不会替用户猜单双周。

支持一次导入整个学年，同时保留设备保护上限：

- JSON 文件不超过 **5 MB**，文本解析内容不超过 **5,000,000 字符**。
- 最多 **2,000 条课程**、**999 周**，单次合计不超过 **20,000 次上课**。
- 无效日期、空课表、倒序节次、超出 **1–999** 范围的周次或过大文件会明确报错，并保留原课表。
- 预览只展示前 8 门时会标明总数，确认后导入全部课程；预览不是截断。

## 本地开发

### 环境

| 工具 | 开发基准 |
|---|---|
| Node.js | 24（当前验证环境）；依赖支持 `^20.19.0` 或 `>=22.12.0` |
| npm | 使用随 Node.js 提供的版本，依赖由 `package-lock.json` 锁定 |
| JDK | 17 |
| Gradle / Android Gradle Plugin | 8.13 / 8.13.0 |
| Kotlin | 2.0.21，JVM target 17 |
| Android | 最低 Android 8.0 / API 26；compileSdk 与 targetSdk 为 36 |

### 网页开发与构建

```powershell
npm ci
npm run dev
```

访问 `http://127.0.0.1:5173`。构建和预览：

```powershell
npm run build
npm run preview
```

`web/dist/index.html` 用于 HTTP(S) 托管。`web/dist/Dolphin-Calendar-offline.html` 可以双击打开；请保留同目录的 `assets/`。两者使用同一份编译后的 JS / CSS。网站的 Service Worker 在 HTTPS 或 localhost 注册；APK 不启用网页缓存，避免覆盖安装后仍读取旧资源。

### Android 调试包

安装 Android SDK Platform 36、Build Tools 和 Platform Tools，为项目创建自己的 `local.properties`：

```properties
sdk.dir=C\:/Users/你的用户名/AppData/Local/Android/Sdk
```

Windows 路径推荐使用上述正斜杠写法；若使用反斜杠，须按 Java Properties 格式写成双反斜杠，避免 SDK 路径被错误解析。本机 SDK 路径不提交到仓库。

把 `JAVA_HOME` 指向 JDK 17，然后构建：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
```

macOS / Linux 使用 `./gradlew` 替换 `.\gradlew.bat`。Windows 也可以运行 `npm run android`；辅助脚本在本机发现 `D:\Android\gradle-8.13` 时优先使用它，否则使用 Wrapper。

构建会自动执行网页构建并同步资源。调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，包名为 `com.dolphin.calendar.debug`；可与正式版同时安装。

### 正式更新与签名

正式包名固定为 **`com.dolphin.calendar`**。`web/src/meta.ts` 是版本来源，Gradle 将 `主版本 × 10000 + 次版本 × 100 + 修订版本` 编码为 `versionCode`；次版本与修订版本各不超过 99。

覆盖已安装正式版必须同时保持原包名、原发行签名，并递增版本码。原密钥位于项目所有者保管的 `.signing/`，本地配置为 `signing.local.properties`，均排除在版本控制与源码包外。**没有原密钥时请构建调试包，不要生成新密钥冒充可覆盖升级的正式包。**

发行者恢复原签名配置后可构建和校验：

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
pwsh -File scripts/check-release.ps1 -PreviousApk "旧版正式 APK 的路径"
```

公开身份与证书指纹保存在 [release-identity.json](release-identity.json)，不含私钥。校验脚本需要 Android SDK Build Tools **36.0.0**；可通过 `-SdkRoot` 指定 SDK 路径。`npm run deliver` 是 Windows 本地交付辅助脚本，需要先构建已签名的 release APK；它不执行 GitHub 发布。打包流程见 [发布说明](docs/PUBLISHING.md)，源码从已提交的 Git 版本归档。

## 工程架构

界面使用 **React 19 + TypeScript + Vite 7**，Material Symbols 为本地 SVG 子集。`md-card` 和 `md-dialog` 是项目自己的 Web 自定义元素，不是原生 Compose 组件。

Android 宿主使用 **Kotlin + WebViewAssetLoader + AndroidX Core / WebKit**。网页由安全的本地 HTTPS 资源域加载，通过 `window.Dolphin.postMessage()` 请求原生能力，再由 `window.dolphinNative` 接收结果。

### 插件开发者的技术入口

| 层次 | 当前技术与源码入口 | 插件接入方式 |
| --- | --- | --- |
| 前端 | React 19.1.1、TypeScript 5.9.2、Vite 7.3.6；`web/src/screens`、`components` | 可使用HTML／CSS／JS或自行打包框架，工具在管理页的独立iframe运行 |
| 课表模型 | [`model.ts`](web/src/lib/model.ts)、[`scheduleTime.ts`](web/src/lib/scheduleTime.ts) | `getSchedule`读取快照、`getCourses`复用日期和周次规则 |
| 状态与存储 | [`AppState.tsx`](web/src/state/AppState.tsx)、IndexedDB；成功事务后统一提交 | 插件配置用独立数据库，不给原课表结构增加插件字段 |
| 导入 | [`Import.tsx`](web/src/screens/Import.tsx)、JSON／CSV／Excel解析器 | `requestImport`只打开原预览与确认流程 |
| 主题 | [`tokens.css`](web/src/theme/tokens.css)、明暗模式与动态取色 | SDK提供颜色上下文；用户主动选择的主题声明只覆盖白名单颜色 |
| Android | Kotlin、AndroidX WebKit 1.12.1／Core 1.15.0；min SDK 26、target 36 | 同一Web构建；原生桥限定本地HTTPS来源与主frame，插件没有原生调用权 |
| 插件宿主 | [`PluginFrame.tsx`](web/src/components/PluginFrame.tsx)、[`plugins.ts`](web/src/lib/plugins.ts) | `window.DolphinPlugin`异步SDK，API v1、manifest权限、版本与参数校验 |

完整约定、开发目录与打包示例以 [插件文档](docs/PLUGINS.md)为准。本版提供插件管理与受控接口，不在课表页添加插件入口，不设置自动下载的插件仓库，也不兼容其他应用的插件包。

```mermaid
flowchart TD
  User[用户操作] --> UI[React 页面与交互]
  UI --> Validate[解析和验证]
  Validate --> IDB[(本机 IndexedDB)]
  IDB -->|提交成功后| State[发布 React 状态]
  State --> UI
  State -->|课表和设置镜像| Bridge[Kotlin 原生桥]
  Bridge --> Prefs[(原生 SharedPreferences)]
  Prefs --> Scheduler[AlarmManager 与广播接收器]
  Scheduler --> Notices[课前提醒和持续行程通知]
  UI -->|用户主动使用并授权| Bridge
  Bridge --> Calendar[系统 Calendar Provider]
  Bridge --> Media[系统文件选择器和相机]
  Bridge --> Maps[外部地图应用]
  Build[Vite 同一份网页构建] --> Browser[浏览器和双击离线版]
  Build --> Assets[APK 内置网页资源]
  Assets --> UI
```

IndexedDB 事务提交成功后才广播新的 React 状态，避免导入页和主页持有不同课表。Android 保存提醒所需的原生镜像，按下一批课程安排非精确闹钟；打开应用、应用更新、时间或时区变化后重排。已安排的提醒不依赖 WebView 保持打开；重启设备后需先重新打开应用。

### 目录

```text
web/src/
  components/     共享月历、课程弹层、引导和实体导航
  screens/        主页、搜索、设置、导入与背景等页面
  lib/            数据模型、JSON 解析、图片处理和原生桥
  state/          IndexedDB 提交、状态与异步读取
  nav/            返回与手势导航
  theme/          设计令牌、页面材质和动效
  assets/         本地图标、品牌图与默认背景
app/src/main/
  java/com/dolphin/calendar/   Kotlin 宿主、提醒、通知和日历
  res/                        Android 资源与图标
scripts/          构建、验证、设备检查与交付
docs/             用户指南、开发接口、维护与发布文档
legal/            第三方许可证全文
```

## 验证与测试

基础检查与完整浏览器回归：

```powershell
npm run build
npm run check
node scripts/ci.mjs
```

完整浏览器回归自行启动 5173 端口的服务；运行前停止占用该端口的开发服务。测试可使用本机 Chrome / Edge，或通过 `CHROME_PATH` 指定浏览器。Android 测试使用明确指定的独立模拟器，操作前阅读脚本的备份与恢复逻辑。

Pages 使用 `npm run build:pages` 和 `node scripts/check-pages.mjs` 检查子路径资源、缓存升级、导入持久化与离线回退。[GitHub Actions](.github/workflows/verify.yml) 执行网页、浏览器和 Android 检查。日志、截图、设备备份与运行报告只保存在被忽略的本地输出目录中。

### 已知边界

- 持久化是本地存储，没有账户同步或云端恢复；课表 JSON 不是全部用户数据备份。
- 提醒准时性取决于通知权限和 ROM 的电池策略；本版只使用非精确调度，不请求精确闹钟或自启动，设备重启后需重新打开应用恢复安排。
- 系统假日来源可能不可读取；假日标记不会自动修正学校调课。
- 玻璃、返回动画和实际刷新率受 WebView、硬件和系统策略影响，不承诺所有设备稳定 120 FPS。
- 流体云与超级岛的最终排版由系统决定；标准实时通知请求不代表已获得厂商专用服务卡片授权。

## 参与维护

仓库按 [源码准入规则](docs/SOURCE_POLICY.md) 接收源码、构建配置、文档和项目资源。APK、ZIP、缓存、签名密钥、本机配置及用户数据不准入；新增目录或类型需先修改 `source-policy.json` 并审阅。首次克隆后安装本地钩子：

```powershell
npm run hooks:install
npm run check:source-files
```

提交前检查 Git 暂存区，推送前检查提交树，GitHub Actions 再检查收到的完整树。本地钩子需要安装且可被绕过，CI 发生在推送之后；这些检查不等同于 GitHub 服务端拒绝上传。

提交问题时请说明版本、Android / WebView 或浏览器版本、入口、重现步骤和实际结果。导入问题可附最小 JSON 示例；发布截图与样本前请去除真实学校、教师、行程和其他个人信息。

修改前先运行基础检查，修改后补充能重现问题的验证。重点维护约定：

- 网页版和 APK 共用同一份构建；不要建立两套互相漂移的课程业务逻辑。
- 保存失败和空导入应明确报错，不能作为“空表成功”；写入成功后再更新界面。
- 返回或取消保护未保存的编辑，重复点击不得重复导入或重复创建课程。
- 原生能力按使用时申请权限；系统日历操作只管理应用自己的课程日历。
- 导航占用独立的内容边界，横屏可改为侧栏；弹层和键盘避让不应遮挡输入与操作。背景固定在独立底层。

作者 GitHub：[tequed232](https://github.com/tequed232/)。发布前文档、资源与签名检查见 [发布检查清单](docs/PUBLISHING.md)。

## 许可证与美术资产

仓库初始化时由作者选择 **GNU GPL v3.0**，完整条款保留在仓库根目录 `LICENSE`。本项目业务源码沿用该许可证；私有仓库的可见性与访问权限仍由 GitHub 设置控制。

React、AndroidX、Kotlin、Vite、TypeScript、Material Symbols 等组件遵循各自许可证，完整列表见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 `legal/`。旧 Dock 的分层与折射参考保留历史归属；当前原生导航使用平台 View，没有直接链接 Miuix 或 AndroidLiquidGlass Compose 库。

品牌图、默认背景插画和包含这些图像的截图是作者提供的项目资源，**不自动受第三方组件许可证或未来的业务代码许可证覆盖**。仓库公开展示这些素材不代表授予下游再分发、再许可或商业使用权；美术资产的授权范围需另行说明。资源入口与替换要求见 [BRAND_ASSETS.md](docs/BRAND_ASSETS.md)。
