# 构建、检查与发行工具

这里保存可重复执行的仓库工具，命令从仓库根目录运行。

| 路径 | 作用 | 常用入口 |
|---|---|---|
| `build/android.ps1` | Windows debug 构建、JVM 测试和 lint | `npm run android` |
| `build/device-preview.mjs` | 生成独立真机验收包的临时配置 | 按 [开发说明](../docs/development.md) 使用 `-I` 构建 |
| `build/native-import-fixtures.mjs` | 生成仅供测试的合成 XLS/XLSX | 由 Gradle 测试资源任务调用 |
| `check/` | 当前源码、暂存、推送历史和工程约束检查 | `npm run check`、`npm run check:source-files` |
| `release/` | 核对正式 APK 包名、版本、发行证书，整理附件和哈希 | `npm run deliver` |

`scripts/build/` 是工具源码；根 `build/` 是 Git 忽略的本机输出。APK、日志、截图、测试工作簿和报告不随源码提交。Node 不构建应用界面，也不启动网页服务器。

正式签名材料只在本机保存，交付步骤见 [发布说明](../docs/release.md)，准入规则见 [源码准入](../docs/source-policy.md)。
