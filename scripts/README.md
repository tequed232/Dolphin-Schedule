# 构建、检查与发行工具

这里保存可重复执行的仓库工具，命令从仓库根目录运行。

| 路径 | 作用 | 常用入口 |
|---|---|---|
| `build/` | Windows Android 构建、原生测试和 lint | `npm run android` |
| `check/` | 源码及历史准入、工程检查和浏览器回归编排 | `npm run check`、`npm run check:ui` |
| `dev/` | 开发期图标子集等资源生成 | 按 [开发说明](../docs/development.md) 执行 |
| `release/` | 校验正式 APK 的包名、版本和证书，整理交付文件 | `npm run deliver` |

这里的 `scripts/build/` 是脚本源码；根目录 `build/` 是被 Git 忽略的输出目录。工具生成的 APK、日志、截图和报告不随源码提交。

正式发行使用作者保管的本机签名材料。交付前置条件与附件说明见 [发布说明](../docs/release.md)。
