# 工程约束与合成规则样本

本目录保存不依赖 Android 运行时的源码检查与检查器样本。Kotlin 业务测试和 Compose 设备测试分别位于 `app/src/test/` 与 `app/src/androidTest/`，旧 TypeScript、浏览器及旧宿主设备脚本已移除。

| 路径 | 作用 | 入口 |
|---|---|---|
| `integrity/check-native.mjs` | 核对原生启动、版本来源、权限、必要资源、受限 WebView 和退役文件 | `npm run check` |
| `fixtures/source-policy.json` | 源码准入工具自身的合成政策，含允许/拒绝路径样本 | 检查器 `--self-test` |
| `../app/src/test/` | 日期、周次、布局、文件/教务转换、备份和版本规则 | `:app:testDebugUnitTest` |
| `../app/src/androidTest/` | Compose 手势、事务、导入、迁移、行程及系统结果 | 指定隔离设备运行，见开发说明 |

工程不变量不能代替业务与设备验证。合成工作簿由构建生成，只进入测试 APK；不作为默认课表、不放入主 APK。使用合成课表的测试拒绝在真实用户手机执行。

截图、日志和结果放忽略的 `build/evidence/`。完整命令、设备保护和真实账号测试边界见 [开发说明](../docs/development.md)。
