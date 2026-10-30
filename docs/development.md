# 开发与验证

界面、状态和业务以 Kotlin＋Jetpack Compose 为主。先查阅 [目录指南](directory-structure.md) 和 [架构](architecture.md)，确认修改应放在页面、业务、持久化还是受限 WebView 中。

## 环境

- Node.js 24 与 npm：安装锁定的本地 Excel 解码器、运行源码检查和生成合成工作簿。
- JDK 17，设置 `JAVA_HOME`；Gradle Wrapper 固定 8.13。
- Android SDK Platform 36、Build Tools 36.0.0；设备测试另需 Platform Tools / adb 及隔离模拟器。
- 最低 Android 8.0（API 26），目标与编译 SDK 为 36。

在根目录配置被忽略的 `local.properties`：

```properties
sdk.dir=C\:/Users/你的用户名/AppData/Local/Android/Sdk
```

也可通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 配置 SDK。SDK、IDE 私有配置、签名与工具链不提交。旧 React/Vite/TypeScript 和浏览器测试依赖已删除，无需启动网页开发服务器。

## 构建与本地检查

所有命令从仓库根目录执行：

```sh
npm ci --ignore-scripts
npm run check
```

Windows：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
```

macOS / Linux：

```sh
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --console=plain
```

`npm run android` 是 Windows 的 debug 构建、JVM 测试与 lint 入口。`npm run check` 检查当前源码及工具自测，不运行 Gradle 或设备测试；它不能代替业务回归。`npm run deliver` 只校验并整理已有正式签名包。

Gradle 在构建时同步 Excel 解码器和离线许可；品牌图直接来自 Android 资源。合成 XLS/XLSX 只生成到测试资源，不进入主 APK。

| 输出 | 路径 |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 测试 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| JVM 报告 | `app/build/reports/tests/testDebugUnitTest/` |
| lint 与其他 Android 报告 | `app/build/reports/` |
| 本机截图、日志与验证记录 | 根 `build/evidence/` |

## 隔离模拟器回归

会写入合成课表的测试只能在明确指定的隔离模拟器运行，必须传 `allowNativeDataTests=true`；代码还会检查模拟器身份。不要将整个 instrumentation 包直接用于私人手机。测试保存并恢复模拟器原生快照，但不应因此把真实用户设备当作隔离环境。

以下以 `emulator-5562` 为例；先通过 `adb devices -l` 核对当前序列号，再安装主包和测试包：

```powershell
adb -s emulator-5562 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5562 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5562 shell am instrument -w -e allowNativeDataTests true -e notClass com.dolphin.schedule.ui.DevicePreviewTest com.dolphin.schedule.debug.test/androidx.test.runner.AndroidJUnitRunner
```

| 测试组 | 验证范围 |
|---|---|
| 胶囊与流程 | 选中项、至少 48dp 触摸区域、快速切换、编辑和页面重建 |
| 平铺布局 | 普通横滑、边缘翻周、反向与取消、长按移动、上下把手、冲突和旧手势保护 |
| 作息与主页 | 午休收起、当前时间线、空闲节次删除和实际时间保留 |
| 教务导入 | 合成学校结果、日期优先、动态节次、草稿、预览、替换、撤销和教材保留 |
| 文件导入 | 内容 URI、多工作表、映射、错误行、仅时间更新、草稿和确认保护 |
| 教材与外观 | 字段编辑、系统选图、相机约定、EXIF、私有草稿、背景压缩和取消保护 |
| 导航行程 | 权限与地图结果、结束、期限、旧操作令牌和已删除功能清理 |
| 地图与系统交互 | 高德客户端入口、网页回退、通知目标校验、预测式返回与取消、旧 WebView 独立进程 |
| 更新与关于 | 正式版本内容、离线许可、精简引导和失败重试 |
| 数据事务 | SQLite 回滚、并发、原文迁移备份和过期预览保护 |
| 教务更新 | `AcademicCheckTest` 验证候选、失败保留、登录失效、关闭竞争、任务约束；`NativeAcademicImportTest` 验证无 SavedState 恢复、合并冲突和撤销。两组仅接受明确授权的隔离模拟器 |

测试工作簿由 `scripts/build/native-import-fixtures.mjs` 生成，图片在测试时生成。系统选图测试只向隔离模拟器写入临时图片，结束后清理自身记录。学校测试使用合成结果，不代表真实学校网络、验证码和会话已经验证；组件更新测试也不代表 GitHub 当时可用。结果必须以本次实际执行日志为准，不沿用历史通过数。

文件导入限制为源文件 5 MB，每表 3000 行/100 列；Excel 另限制解压总量 64 MB、总单元格 30 万。业务限制和错误保护应结合 JVM 测试验证，不能只凭界面显示成功判断数据完整。

上面的模拟器命令明确排除 `DevicePreviewTest`：该组只接受下一节的独立验收包，直接在普通 debug 包中执行会触发保护条件。不要删除保护或给私人手机传入合成数据测试参数。

## 独立真机验收包

为保留日用包和既有数据，可生成独立包：

```powershell
node scripts/build/device-preview.mjs
.\gradlew.bat -I build/device-preview/init.gradle :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

此构建应用 ID 为 `com.dolphin.schedule.devicepreview.debug`，名称为“Dolphin Schedule 原生验收”。脚本只生成被忽略的临时 Gradle/标签覆盖配置，正式入口本身已是 `NativeActivity`。构建仍写入标准 APK 路径，安装或交付前必须核对包名；恢复普通构建时去掉 `-I` 后重新构建。

`DevicePreviewTest` 仅接受此独立包及 `allowDevicePreview=true`，不填合成课表、不自动确认教务导入或删除课程。它检查已有数据编码往返、导航、取消选图/时间编辑、长按后退出和可用时的时间线，并记录胶囊切换的 `FrameMetrics`。

```powershell
adb -s <真机序列号> install -r <已核对包名的验收APK>
adb -s <真机序列号> install -r <验收测试APK>
adb -s <真机序列号> shell am instrument -w -e allowDevicePreview true -e class com.dolphin.schedule.ui.DevicePreviewTest com.dolphin.schedule.devicepreview.debug.test/androidx.test.runner.AndroidJUnitRunner
```

不要为解决签名不同而卸载用户安装包或清除数据。独立包与日用包数据不自动同步；教务会话也不从桌面浏览器或其他包复制。设备需要解锁并允许安装时由用户处理系统提示。Android 17 的验收路径使用公开系统触摸注入，避免依赖旧输入隐藏 API。

真机帧耗时只适合前后对比，调试包和单台设备不能证明所有手机帧率。系统灵动岛、相机和真实学校登录应独立记录设备结果。私人课表、封面、截图、数据库和登录信息不得随源码上传。

## 持续集成和资源

`verify.yml` 安装 Node 24、JDK 17 和 SDK，运行 npm 工程检查、debug 主包/测试包构建、JUnit 和 lint。它不自动启动设备测试、不使用发行私钥、不发布 APK 或 Pages。源码与新增历史由独立 `source-admission.yml` 检查。

品牌图位于 `app/src/main/res/drawable-nodpi/app_icon.webp`，通知图与 XML 位于同级资源目录。替换必要资源时同步准入与第三方说明；不要提交设备截图、测试图或第二份品牌副本。

## 中文提交说明

GitHub 文件列表中间一列来自最近一次修改该文件或目录的提交标题。采用 `类型(范围): 中文说明`，范围可省略；保留 `feat`、`fix`、`docs`、`refactor`、`build`、`test`、`ci`、`chore`、`release` 等类型与路径名称，冒号后用中文解释变更目的。

例如 `fix(timetable): 避免边缘翻周时横滑抖动`、`docs: 解释原生目录与数据备份范围`。标题说明改动，正文补充原因、影响与实际验证；目录职责放长期文档，避免依赖提交历史才能理解文件用途。
