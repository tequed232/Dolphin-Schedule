# 源码准入与目录规范

根目录 `source-policy.json` 是源码目录、文件类型和必需文件的允许列表。目录迁移时同时修改准入规则、构建入口、导入路径、文档链接与 CI。应用源码在 `app/` 和 `ui/`，测试在 `tests/`，维护工具在 `scripts/`，长期说明在 `docs/`。

各目录和关键文件的具体用途、生成输出及新增内容的放置位置见 [文件目录指南](directory-structure.md)。

## 文件放在哪里

| 内容 | 位置与规则 |
|---|---|
| 运行时代码和必要资源 | `app/`、`ui/`，资源替换时同步哈希清单 |
| 测试代码与合成数据 | `tests/`，运行时不引用测试夹具 |
| 构建、验证和交付工具 | `scripts/`，按职责分目录 |
| 长期说明与依赖许可 | `README.md`、`docs/`、`legal/`、第三方说明 |
| APK、编译资源和网页产物 | `app/build/`、`ui/dist/`、`releases/`，Git 忽略 |
| 截图、日志、临时报告和工具链 | 根 `build/`，Git 忽略 |
| SDK 路径、IDE 私有配置与签名材料 | 本机配置及忽略目录，不提交 |

归档、node_modules、缓存、私有数据库、备份和一次性验收材料不进入 Git。网页由 Pages 工作流直接构建 `ui/`，不另存网页源码副本或打包发布 ZIP。新增长期文档需同步 `source-policy.json`；不要为了提交临时文件扩大允许范围。

## 检查与钩子

```sh
npm run hooks:install
npm run check:source-files
node scripts/check/check-source-files.mjs --staged
node scripts/check/check-source-files.mjs --self-test
node scripts/check/check-source-push.mjs --self-test
```

钩子安装是显式操作，不由 npm ci 修改用户已有钩子。pre-commit 检查实际 Git 暂存内容，pre-push 检查推送提交与新增历史，GitHub Actions 执行独立源码准入。根 build/ 和 app/build/ 属于输出；scripts/build/ 属于构建源码工具，两者按路径区分。

路径和 Markdown 链接应与 Git 中的大小写完全一致，避免 Windows 正常而 GitHub/Linux 失效。文件移动后同时更新引用，替换旧文件时从 Git 移除原件，避免保留重复资源。

规则引擎自测使用 `tests/fixtures/source-policy.json` 中的合成政策，它不能替代实际仓库的准入规则。完整目录边界见 [架构说明](architecture.md)，验证步骤见 [开发说明](development.md)。
