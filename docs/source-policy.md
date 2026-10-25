# 源码准入与目录规范

source-policy.json 是当前源码目录、文件类型和必需文件的允许列表。目录迁移时必须同时修改源码准入规则、构建入口、导入路径与 CI。应用源码在 app/ 和 ui/，测试在 tests/，维护工具在 scripts/，长期说明在 docs/。

APK、归档、编译输出、node_modules、缓存、IDE 私有配置、签名密钥、本机路径、数据库、备份、日志和一次性验收材料不进入 Git。测试证据与体积报告写入根 build/evidence/。旧教程演示媒体、插件示例和独立网页源码副本均已删除，不在当前允许列表中。GitHub Pages 工作流与测试保留，直接构建 ui/。测试规则引擎使用 tests/fixtures/source-policy.json 中的合成政策，不能替代实际仓库准入规则。

```sh
npm run hooks:install
npm run check:source-files
node scripts/check/check-source-files.mjs --staged
node scripts/check/check-source-files.mjs --self-test
node scripts/check/check-source-push.mjs --self-test
```

钩子安装是显式操作，不由 npm ci 修改用户已有钩子。pre-commit 检查实际 Git 暂存内容，pre-push 检查推送提交与新增历史，GitHub Actions 执行独立源码准入。根 build/ 和 app/build/ 属于输出；scripts/build/ 属于构建源码工具，两者按路径区分。
