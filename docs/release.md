# Android 签名与 GitHub Pages 发布

Android APK 和 GitHub Pages 独立发布。网页共用 `ui/` 源码，由 Actions 构建并部署；APK 需使用作者保管的发行密钥签名。项目不生成单文件离线 HTML、PWA 缓存或网页 ZIP，GitHub 源码归档由 Git 自身提供。

## GitHub Pages

1. 在仓库 Settings → Pages → Build and deployment 选择 **GitHub Actions**。
2. 将验证后的修改合并到 `main`，触发 `.github/workflows/pages.yml`；也可在 Actions 中选择该工作流并手动运行。
3. 工作流检查源码、运行单元测试、构建 `ui/dist/` 并验证部署路径、导入与刷新持久化，再上传 Pages 产物并部署。
4. 等待 build 和 deploy 均成功，从 `github-pages` 环境打开实际访问地址。

部署路径来自 `actions/configure-pages`，通过 `DOLPHIN_BASE_PATH` 传给 Vite，支持项目子路径或自定义域名根路径。默认项目地址为 [Dolphin Schedule 网页版](https://tequed232.github.io/Dolphin-Schedule/)，最终以环境 URL 为准。无需将 `ui/dist/` 提交到分支，也无需维护第二份网页源码。

网页数据保存在当前浏览器当前站点的 IndexedDB，不与 Android 自动同步。浏览器可浏览、编辑和导入课表；后台提醒、系统日历和原生更新安装仅在 Android 宿主可用。工作流文件就绪不代表线上部署已执行。

如果部署失败，先检查 Pages 发布源、环境审批与权限，再查看 build/deploy 的失败步骤。子路径资源 404 时按 [开发说明](development.md) 使用相同 `DOLPHIN_BASE_PATH` 重建和运行 `check:pages`。

## 版本与身份

`ui/src/meta.ts` 是版本来源，`package.json` 的版本应保持一致。Gradle 编码规则为 `major × 10000 + minor × 100 + patch`，minor / patch 不超过 99。`RELEASE_NOTES.md` 记录相应版本变化。

发布前先确定实际版本，保持 `ui/src/meta.ts`、`package.json`、`package-lock.json` 和发布说明一致。当前源码版本为 1.5.5，对应 versionCode 10505；未发布修改保留在版本记录的“未发布”部分，不把开发构建描述为新的正式 Release。

正式包名固定为 `com.dolphin.schedule`，debug 增加 `.debug`。它作为新 App 安装；不能覆盖更名前的旧包。后续同包更新仍须保持原签名并递增版本码。

`release-identity.json` 保存新包名和沿用的发行证书 SHA-256，不含私钥。签名密钥由作者保管；如未来明确更换发行证书，需要同步更新公开身份记录，不能跳过校验。

## 签名配置

在未跟踪的 `signing.local.properties` 中配置以下字段：

```properties
storeFile=.signing/dolphin-release.jks
storePassword=填写本地密码
keyAlias=填写实际别名
keyPassword=填写本地密码
```

密钥和密码不进入 Git、CI 或源码归档。没有发行密钥时构建 debug 包，不自动生成替代发行身份。

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease --console=plain
powershell -NoProfile -File scripts/release/check-release.ps1
npm run deliver
```

校验和交付脚本需要 Windows、Android Build Tools 36.0.0，以及 `ANDROID_HOME` / `ANDROID_SDK_ROOT`。可通过校验脚本 `-SdkRoot` 显式指定路径。`-PreviousApk` 用于后续同包覆盖升级检查，不能传更名前的旧包。

交付目录默认为 `releases/Dolphin-Schedule-版本号/`；已有目录会拒绝覆盖，避免混入旧文件。产物为 `dolphin-schedule-版本号.apk` 与 `SHA256SUMS.txt`，正式校验记录位于 `build/evidence/`。

验证完成后，在 [GitHub Releases](https://github.com/tequed232/Dolphin-Schedule/releases) 创建相应版本标签的正式 Release，附上签名 APK、哈希清单与用户可读的变化说明。交付脚本只整理本地产物，不自动创建 Release 或上传附件。应用更新检查使用正式 Release 元数据，草稿、预发布版本和文件名含 debug 的 APK 不作为正式安装更新。

## 发布检查

构建、工程检查、单元和浏览器测试应通过，并阅读 Android lint 结果。发布前验证真实设备上的提醒、系统日历、导航、键盘和深浅色；CI 的浏览器原生回调模拟不能替代实机证据。截图与样本应清除个人信息。

构建、校验和测试产物不提交到源码仓库。debug 包用于开发验证，不能替代发行签名 APK。安装身份变更和课表迁移见 [数据与迁移](data-and-migration.md)，可提交文件范围见 [源码准入](source-policy.md)。
