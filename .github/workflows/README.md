# GitHub 自动验证

YAML 文件由 GitHub Actions 验证原生 Android 工程和源码。

| 文件 | 作用 |
|---|---|
| `source-admission.yml` | 独立检查可提交文件与新增历史，拦截产物、私钥和临时材料 |
| `verify.yml` | 安装锁定依赖、执行工程检查、构建 debug 主包/测试包、运行 JUnit 和 Android lint |

设备测试 APK 的构建不代表测试已经在模拟器或真机运行，实际设备回归需按 [开发说明](../../docs/development.md) 单独执行。工作流不使用发行私钥，不自动发布正式 APK；网页构建和 Pages 发布已移除。

正式签名、版本和 Release 附件见 [发布说明](../../docs/release.md)。
