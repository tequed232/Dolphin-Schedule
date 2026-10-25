# 第三方组件说明

项目业务代码采用根目录的 [MIT 许可证](LICENSE)，Copyright (c) 2026 tequed232。第三方组件遵循下表各自的许可，项目许可证调整不会替换这些组件的版权声明或许可证文本。

| 组件 | 用途 | 许可证 |
|---|---|---|
| SheetJS CE（@e965/xlsx 0.20.3 分发） | XLSX / XLS 本机解析 | Apache-2.0，完整文本见 `legal/APACHE-2.0.txt` |
| React / React DOM 19.1.1 | Web 界面 | MIT，完整文本见 `legal/REACT-LICENSE.txt` |
| Material Symbols（Google），通过 @material-symbols/svg-400 0.47.5 分发 | 27 个本地 Rounded SVG | Apache-2.0，完整文本见 `ui/src/assets/icons/LICENSE` |
| AndroidX WebKit 1.12.1 / Core 1.15.0 | 安全本地 WebView、FileProvider、系统兼容 | Apache-2.0 |
| Kotlin 2.0.21 | Android 宿主 | Apache-2.0 |
| Vite 7.3.7 | 开发与构建工具 | MIT |
| TypeScript 5.9.2 | 类型检查 | Apache-2.0 |
| Playwright 1.55.1 | 本地验证工具 | Apache-2.0 |
| Miuix 文档 | 共享 Web 主题的语义色参考，未链接 Compose 组件依赖 | Apache-2.0 |

完整依赖版本与来源由 [package-lock.json](package-lock.json) 和 [Gradle 配置](app/build.gradle.kts) 锁定。Apache-2.0 正文见 [许可证文本](legal/APACHE-2.0.txt)。Material Symbols 图标上游为 [Google Material Design Icons](https://github.com/google/material-design-icons)，分发工具为 [Material Symbols](https://github.com/marella/material-symbols)。图标经过子集选择，图形路径未修改。

应用品牌图由项目作者提供，不属于 Material Symbols。启动器、应用内和通知彩图使用此图的无损 WebP 或等比例缩放版本；项目自己的列表和平铺 SVG 也不计入 Material Symbols 子集。资源清单与维护方式见 [外观说明](docs/appearance.md)。Android 和 Pages 共用这些源码与资源，产物生成位置见 [架构说明](docs/architecture.md)。
