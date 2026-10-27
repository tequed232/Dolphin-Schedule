# 源码准入与目录规范

根目录 `source-policy.json` 定义允许提交的路径、文件类型、资源以及必需文件。应用在 `app/`，长期说明在 `docs/`，维护工具在 `scripts/`，工程约束在 `tests/`。完整目录职责见 [文件目录指南](directory-structure.md)。

## 文件放在哪里

| 内容 | 位置与规则 |
|---|---|
| Kotlin、Android 配置和必要资源 | `app/src/main/`；新增二进制资源需明确用途并同步准入 |
| Kotlin 单元与设备测试 | `app/src/test/`、`app/src/androidTest/` |
| 工程约束与检查器合成样本 | `tests/integrity/`、`tests/fixtures/` |
| 构建、验证和交付工具 | `scripts/`，按职责分目录 |
| 长期说明与许可 | 根 README、`docs/`、`legal/` 和第三方说明 |
| APK、生成资源与报告 | `app/build/`、根 `build/`、`releases/`，Git 忽略 |
| SDK、IDE 和签名配置 | 本机配置及忽略目录，不提交 |

压缩归档、`node_modules/`、缓存、私有数据库、备份和一次性验收截图不进入 Git。旧 `ui/`、网页构建与 Pages/PWA 发布已移除；不要另存旧网页副本或将编译产物当作源码提交。学校提取脚本是明确允许的应用资源，不开放任意第三方插件脚本加载。

目录迁移时同步准入规则、构建输入、引用路径、文档链接与 CI；新增长期文档需同步政策。不要为提交临时文件扩大整个资源目录的允许范围。

## 检查与钩子

```sh
npm run hooks:install
npm run check:source-files
node scripts/check/check-source-files.mjs --staged
node scripts/check/check-source-files.mjs --self-test
node scripts/check/check-source-push.mjs --self-test
npm run check
```

钩子是显式安装的，不由 `npm ci` 修改用户已有钩子。`pre-commit` 检查实际暂存内容，`pre-push` 检查推送提交与新增历史，GitHub Actions 独立执行源码准入。根 `build/` 和 `app/build/` 是输出；`scripts/build/` 是构建工具源码，两者按完整路径区分。

手动触发源码准入时，以实时默认分支的共同祖先为历史基线，并严格检查当前源码；基线后新增的违规历史仍会被拒绝。重写历史后，旧推送事件可能引用已退出分支的编号，应对新历史重新触发验证。

检查器自测使用 `tests/fixtures/source-policy.json` 中的合成政策，其中历史路径只是测试输入，不代表当前仓库仍接受这些路径。工程检查验证原生启动、版本、权限、必要资源和退役文件等不变量，不能替代业务单元测试与设备测试。

路径和 Markdown 链接的大小写必须与 Git 完全一致，避免 Windows 可用而 GitHub/Linux 失效。移动文件后更新所有引用，删除原件，减少重复资源。
