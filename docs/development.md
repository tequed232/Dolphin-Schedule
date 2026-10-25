# 开发与验证

## 环境

- Node.js 24 与 npm，使用 `npm ci` 安装锁定依赖。
- JDK 17，设置 `JAVA_HOME`；Gradle Wrapper 固定为 8.13。
- Android SDK Platform 36、Build Tools 36.0.0；设备检查另需 Platform Tools / adb。
- 浏览器测试使用本机 Chrome / Edge 或 Playwright Chromium，可用 `CHROME_PATH` 指定可执行文件。

先执行 `npm ci`。如果没有可用的本机浏览器，可执行 `npx playwright install chromium` 安装测试浏览器；Linux CI 的系统依赖由 Pages 工作流中的 `--with-deps` 一并安装。

项目根目录创建未跟踪的 `local.properties`：

```properties
sdk.dir=C\:/Users/你的用户名/AppData/Local/Android/Sdk
```

也可通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 配置 SDK。个人 IDE 配置、SDK 路径、签名文件和构建产物不进入版本控制。

## 常用命令

所有命令从仓库根目录执行。

| 命令 | 作用 |
|---|---|
| `npm ci` | 安装锁定依赖 |
| `npm run dev` | 启动界面开发服务器，端口 5173 |
| `npm run build` | TypeScript 检查与共用界面构建，输出 ui/dist/ |
| `npm run preview` | 本地预览构建产物，端口 4173，仅供开发 |
| `npm run check` | 工程约束与资源完整性检查 |
| `npm test` | 单元测试 |
| `npm run check:ui` | 自动启动独立服务器并运行浏览器回归，结束后关闭服务器 |
| `npm run check:pages` | 验证构建后网页的部署路径、导入与刷新持久化 |
| `npm run android` | Windows：通过 Wrapper 构建 debug APK、运行原生单元测试和 lint |
| `npm run deliver` | Windows：校验并交付已有签名 release APK |

完整浏览器回归使用端口 5183，自动启动和关闭测试服务器。单独运行使用开发服务器的测试脚本时，先启动 `npm run dev`，可用 `TEST_URL` 指定地址；`check-pages.mjs` 和 `check-import-loading.mjs` 则自行提供构建产物的静态服务器。浏览器不具备 Android 后台通知或系统日历能力，相关回调在测试中模拟。回归运行期间保持源码和 Vite 配置不变，避免热更新或服务重启干扰状态与布局断言。

可用 `npm run check:ui -- --only=check-ui.mjs,check-background.mjs` 只重跑指定测试组，`--fail-fast` 在首个失败组后停止。完整与指定组的结果分别写入 `build/evidence/browser-ci-results.json` 和 `browser-ci-rerun-results.json`。

## 验证 GitHub Pages

先按目标部署路径构建，再用相同环境验证。Windows PowerShell：

```powershell
$env:DOLPHIN_BASE_PATH = '/Dolphin-Schedule/'
npm run build
npm run check:pages
Remove-Item Env:DOLPHIN_BASE_PATH
```

macOS / Linux：

```sh
DOLPHIN_BASE_PATH=/Dolphin-Schedule/ npm run build
DOLPHIN_BASE_PATH=/Dolphin-Schedule/ npm run check:pages
```

域名根路径改为 `/`。检查覆盖子路径静态资源、延迟导入、课表持久化、新名称、版本和无内置背景状态。自动部署从 GitHub 配置读取实际路径，见 [发布说明](release.md)。Android 构建由 Gradle 固定为相对路径 `./`。

## Android

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

macOS / Linux 使用 `bash ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain`。Gradle 自动执行界面构建、资源同步与启动器品牌资源生成。需要先完成 `npm ci`。

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Lint：`app/build/reports/`
- 浏览器/工程证据：`build/evidence/`
- 模拟器数据恢复备份：`build/device-backups/`

`tests/android/` 保存需要显式指定设备的原生导航、通知、系统日历和更新检查；运行前阅读各脚本的设备与数据前置条件。仅在隔离测试设备上运行，浏览器回调模拟不代替实机验证。

## 持续集成

`.github/workflows/verify.yml` 安装 Node 24、JDK 17 和 Android SDK，依次运行界面构建、工程检查、单元测试、浏览器回归、debug APK 构建与 Android lint。工作流不需要发行私钥，也不发布正式 APK。

## 资源维护

品牌资源使用无损 WebP，尺寸、透明度和像素保持原 PNG 一致。替换资源后更新 `ui/src/assets/manifest.json` 中的尺寸、大小和 SHA-256，并运行 `npm run check` 与外观回归。启动器图标由 Gradle 自动复制统一源文件。

`scripts/dev/vendor-icons.cjs` 可从锁定的 Material Symbols 包重新生成图标子集；图标清单与许可证随源码保存。不要引入网络字体作为离线 App 的必需依赖。

源码准入工具位于 `scripts/check/`，安装和规则说明见 [源码准入](source-policy.md)。测试输出、截图、体积比较和构建来源记录应写入忽略的 `build/evidence/`；这些文件由实际执行的验证任务生成，不随源码提交。
