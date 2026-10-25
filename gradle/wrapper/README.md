# 固定 Gradle 构建环境

Gradle Wrapper 让开发者和 CI 使用仓库指定的 Gradle 版本，减少本机构建工具版本不同带来的差异。

| 文件 | 作用 |
|---|---|
| `gradle-wrapper.properties` | 固定 Gradle 8.13 的下载地址、SHA-256 校验值、缓存位置和超时时间 |
| `gradle-wrapper.jar` | 下载并启动指定 Gradle 版本的引导程序 |
| 根目录 `gradlew` | macOS / Linux 等环境的启动入口 |
| 根目录 `gradlew.bat` | Windows 启动入口 |

从仓库根目录调用 Wrapper。Windows 示例：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

升级时统一更新 Wrapper 文件、版本和校验值，启动脚本与 JAR 保持生成工具提供的内容。环境要求见 [开发说明](../../docs/development.md)。
