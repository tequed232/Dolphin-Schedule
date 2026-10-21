# 构建与发布

版本来源为 `web/src/meta.ts`，Android 版本码由版本号生成。正式包名固定为 `com.dolphin.calendar`，覆盖升级须沿用原发行签名并递增版本码；签名密钥和本机配置由所有者本地保管。

## 网页与 Pages

```powershell
npm ci
npm run build:pages
node scripts/check-pages.mjs
```

发布目录是 `build/pages/`，包含内联应用与样式的 `index.html`、本地资源、离线缓存文件和公开导入教程。发布整个目录，不直接上传开发入口 `web/index.html`。网页支持 `/Dolphin-Calendar/` 子路径，首页联网优先、离线回退，更新时保留当前域名下的本机课表。

`.github/workflows/static.yml` 在 main 更新或手动运行时构建并检查 Pages，然后上传专用网页部署附件。部署条件为手动运行、公开仓库，或配置 `PAGES_ENABLED=true`。Settings → Pages 的 Source 使用 GitHub Actions。仓库可见性与站点访问范围分别配置。

公开导入教程源码与媒体位于 `docs/import-guide/`，只由 Pages 构建发布，不进入 APK 内置资源。网页数据与 Android 数据各自独立；迁移站点前导出课表 JSON。

## Android 与发行包

先安装 JDK 17、Android SDK Platform 36 和 Build Tools，配置被忽略的 `local.properties`。使用 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 构建和检查调试包。

正式构建使用本地原发行签名。运行 `scripts/check-release.ps1` 核对包身份；需要本地分发目录时运行 `npm run deliver`。打包脚本从已提交且干净的 Git 版本生成源码 ZIP，并整理正式 APK、网页 ZIP、README 和校验清单；不打包测试报告或证明附件。

打包工具不推送 Git、不创建 Release，也不部署 Pages。APK Release 需要明确的发行操作，不能将调试包当作正式覆盖升级包。

## 源码仓库

[source-policy.json](../source-policy.json) 和 [源码准入说明](SOURCE_POLICY.md) 定义可提交的源码、工程配置、可复用测试、长期文档与产品资源。文档和展示图片按具体路径准入；新增文件先审阅用途。

一次性审查表、验收记录、执行报告、截图、日志和设备备份只保存在本地忽略目录。README 介绍产品与开发入口，RELEASE_NOTES 记录用户可感知的版本变化。运行状态查看 GitHub Actions，不在源码中堆积验证证明。

提交前检查暂存区和源文件准入；推送前检查提交树及新增历史。CI 使用 `.github/workflows/verify.yml` 和独立的文件准入工作流。普通验证工作流不上传构建附件；Pages 专用附件只包含可部署网页。
