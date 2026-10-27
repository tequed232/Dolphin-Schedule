# Android 签名与发布

应用以 Kotlin＋Jetpack Compose 原生入口发行，不再构建或发布独立网页。删除 Pages 工作流不会自动撤销历史站点部署；线上 Pages 的实际状态需另行核对仓库设置。

## 版本与身份

应用版本唯一来源是根 `gradle.properties` 的 `dolphinVersion`。当前源码版本为 **1.6.0**，对应 `versionCode=10600`。Gradle 编码规则为 `major × 10000 + minor × 100 + patch`，minor / patch 不超过 99；维护版本时同时更新 `RELEASE_NOTES.md` 和用户文档中的版本说明。`package.json` 仅管理工具依赖，不承载第二个应用版本。

正式包名为 `com.dolphin.schedule`，debug 增加 `.debug`。更名前的旧包需手动迁移，同包升级必须保持发行签名并递增版本码。`release-identity.json` 记录公开包名与发行证书 SHA-256，不含私钥。

## 签名配置

在 Git 忽略的 `signing.local.properties` 中配置：

```properties
storeFile=.signing/dolphin-release.jks
storePassword=填写本地密码
keyAlias=填写实际别名
keyPassword=填写本地密码
```

密钥和密码不进入源码、CI、日志或附件。无发行密钥时只构建 debug 包，不自动创建替代身份。首次构建先按 [开发说明](development.md) 安装锁定的 Excel 解码依赖。

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease --console=plain
powershell -NoProfile -File scripts/release/check-release.ps1
npm run deliver
```

校验和交付脚本需要 Windows、JDK 17、Android Build Tools 36.0.0，以及 `ANDROID_HOME` / `ANDROID_SDK_ROOT`；可通过 `-SdkRoot` 显式指定 SDK。`-PreviousApk` 用于核对同包升级身份，不能传入更名前的不同包名。

交付目录默认为 `releases/Dolphin-Schedule-版本号/`，包含 `dolphin-schedule-版本号.apk` 与 `SHA256SUMS.txt`。已有目录会拒绝覆盖，避免混入旧产物；校验记录位于忽略的 `build/evidence/`。

## 发布前核对

1. 当前源码准入与工程约束、JUnit、设备回归及相应 lint 均有实际结果。检查原生启动、导入取消/确认、迁移与备份、手势和导航行程；厂商灵动岛展示以目标设备证据为准。
2. 正式构建启用代码和资源压缩。核对包名、版本名、版本码、发行证书，确认 APK 不含旧业务网页、合成工作簿或私人数据。
3. 记录源码提交、构建环境与附件 SHA-256。恢复普通构建配置后再交付，避免误上传独立验收包。
4. 创建对应 `v版本号` 的正式 Release，附签名 APK、哈希清单和中文变化说明。验证远端标签指向预期源码、附件可下载且哈希一致。

交付脚本只整理本地产物，不自动上传、创建 Release 或关闭历史 Pages。发行是否完成以 [GitHub Releases](https://github.com/tequed232/Dolphin-Schedule/releases) 的真实状态为准，不能将开发 APK 或通过构建描述为已发布。

应用更新页只读取本仓库正式 Release，跳过草稿和预发布，不接受名称含 debug 的 APK。只有唯一合适附件时直接打开浏览器下载，多个附件无法确定时打开版本页；应用不后台更新或自动安装。

测试报告、截图、工具链和 APK 不进入 Git。源码提交范围见 [源码准入](source-policy.md)，安装身份与迁移见 [数据说明](data-and-migration.md)。
