# 本地 Git 检查

这里保存开发者可选安装的 Git 钩子，用于在代码进入仓库前检查提交内容。

| 文件 | 作用 |
|---|---|
| `pre-commit` | 检查实际暂存的文件，拦截构建产物、密钥、本机配置和未准入资源 |
| `pre-push` | 检查将推送的提交及其新增历史，防止禁入文件通过历史提交进入远端 |

在仓库根目录执行 `npm run hooks:install` 启用。安装会设置当前仓库的 `core.hooksPath`；已有自定义钩子时先核对配置。`npm ci` 不自动安装钩子。

规则来自根目录 `source-policy.json`，实现位于 `scripts/check/`。完整规则见 [源码准入](../docs/source-policy.md)。
