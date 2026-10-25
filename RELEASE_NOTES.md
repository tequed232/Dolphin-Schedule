# 版本记录

## 未发布

### 使用变化

- 产品统一为 Dolphin Schedule；正式包名为 `com.dolphin.schedule`，debug 包增加 `.debug`，本地数据库为 `dolphin-schedule`。更名版本作为新 App 安装，旧课表可通过 JSON 导出/导入迁移。
- 默认背景改为主题底色，不再附带背景图片；保留自选图片与背景模糊。
- 移除第三方插件扩展、管理与示例。课表、JSON/CSV/Excel 导入、临时课程、教材、提醒、系统日历、更新检查、多窗口同步与编辑冲突保护继续提供。
- 保留 GitHub Pages 浏览器访问，使用与 Android 相同的界面源码；移除 PWA 和单文件离线 HTML 发布。

### 维护变化

- 目录统一为 `app/`、`ui/`、`tests/`、`scripts/`、`docs/`，同步构建入口、源码准入、Git 钩子与 Actions 工作流。
- 清理演示媒体、重复品牌资源、过期文档、重复脚本及未引用样式；品牌源图使用无损 WebP，原生启动器资源由构建生成。
- 更新 README 和维护文档，补齐技术栈、目录职责、平台差异、数据迁移、开发验证与 Pages 部署说明。

## 1.5.1

当前重构沿用的源码版本。已发布版本的内容与附件见 [v1.5.1 Release](https://github.com/tequed232/Dolphin-Schedule/releases/tag/v1.5.1)；“未发布”部分仅描述后续修改，不表示已有新的正式 APK 发行。更早版本记录可从 [Releases](https://github.com/tequed232/Dolphin-Schedule/releases) 和 Git 历史查阅。
