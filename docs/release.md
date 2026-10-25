# Android 签名与 GitHub Pages 发布

当前提供 Android APK 与 GitHub Pages 网页。网页共用 ui/ 源码，由 Actions 构建并部署；离线 HTML、PWA 缓存与网页 ZIP 不再生成。GitHub 源码归档由 Git 自身提供，APK 交付脚本只整理校验后的 APK 和哈希清单。

## GitHub Pages

仓库 Settings → Pages → Build and deployment 选择 **GitHub Actions**。`.github/workflows/pages.yml` 在推送 main 或手动触发时，检查源码、构建界面、验证部署子路径与刷新持久化，然后部署 `ui/dist/`。部署路径来自 `actions/configure-pages`，支持项目子路径或自定义域名根路径。最终访问地址以工作流的 github-pages 环境 URL 为准；默认项目地址为 `https://tequed232.github.io/Dolphin-Schedule/`。

网页数据保存在当前浏览器当前站点的 IndexedDB，不与 Android 自动同步。浏览器可浏览、编辑和导入课表；后台提醒、系统日历和原生更新安装仅在 Android 宿主可用。工作流文件就绪不代表线上部署已执行。

## 版本与身份

`ui/src/meta.ts` 是版本来源，`package.json` 的版本应保持一致。Gradle 编码规则为 `major × 10000 + minor × 100 + patch`，minor / patch 不超过 99。`RELEASE_NOTES.md` 记录相应版本变化。

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

## 发布检查

构建、工程检查、单元和浏览器测试应通过，并阅读 Android lint 结果。发布前验证真实设备上的提醒、系统日历、导航、键盘和深浅色；CI 的浏览器原生回调模拟不能替代实机证据。截图与样本应清除个人信息。

当前仓库为 tequed232/Dolphin-Schedule，默认分支 main。本轮只重构和重新构建，不自动发布 Release 或上传 APK。正式更新检查保留，指向该仓库的 Release。
