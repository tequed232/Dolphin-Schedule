# GitHub 自动验证与网页部署

这里的 YAML 文件由 GitHub Actions 执行，用于验证源码和发布网页版。

| 文件 | 作用 |
|---|---|
| `source-admission.yml` | 独立检查可提交文件与新增历史，拦截产物、私钥和临时材料 |
| `verify.yml` | 验证共用界面、业务逻辑、浏览器流程和 Android 构建 |
| `pages.yml` | 在 `main` 更新后构建共用界面并部署 GitHub Pages，支持手动触发 |

Pages 发布 `ui/dist/` 构建结果；仓库不保存另一套网页代码。正式 APK 的签名与交付由作者按 [发布说明](../../docs/release.md) 完成，验证工作流不使用发行私钥。

运行环境与本地验证入口见 [开发说明](../../docs/development.md)。
