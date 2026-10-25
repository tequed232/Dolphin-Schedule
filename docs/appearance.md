# 外观与资源

Android 与网页版共用列表/平铺布局、实体导航、深浅模式和显示设置。默认使用主题底色，不附带背景图片；只有用户选择并启用图片后才显示自定义背景。

## 使用外观设置

在“设置 → 外观”调整深浅模式、文字尺寸和布局。深浅模式可跟随系统，竖屏使用底部导航，横屏或宽屏使用侧栏。Android 的动态取色依赖受支持的系统环境，浏览器不会读取 Android 原生主题。

在“背景”页面选择 JPG、PNG 或 WebP。单张不超过 10 MB，图片在本机处理、缩小和保存，不上传；推荐使用 1080×1920 或 1440×2560 的竖图。居中铺满会按屏幕裁去部分边缘。

选图后可调节 0–30 px 模糊：拖动时预览，松手后保存。“使用主题底色”关闭图片显示并保留已选文件；“启用这张图片”重新显示；“移除图片”删除已保存的背景。未启用图片时，模糊控件禁用。

## 资源与维护

| 资源 | 用途 |
|---|---|
| ui/src/assets/brand/app-icon.webp | 1084×1084 透明品牌源图，界面和 Android 启动器共用 |
| app/build/generated/res/brand/drawable-nodpi/app_icon.webp | Gradle 生成的启动器资源，不提交 |
| app/src/main/res/drawable-nodpi/live_icon.webp | 256×256 通知彩图 |
| app/src/main/res/drawable/ic_notification.xml | 系统着色的单色通知图标 |
| ui/src/assets/icons/ | 本地 SVG 图标子集和许可证 |

品牌资源使用无损 WebP，`ui/src/assets/manifest.json` 保存尺寸、大小、哈希与资源来源。Gradle 从统一源文件生成启动器资源，不手动维护第二份品牌原图。替换资源后按 [开发说明](development.md) 更新清单并运行完整性检查。

导航、课程、文本和图片的布局应在窄屏/宽屏、横屏/竖屏以及深浅模式下可用。项目业务代码采用 [MIT 许可证](../LICENSE)；品牌美术由作者提供，其授权范围与业务代码及第三方图标许可分别说明。第三方资源的归属见 [第三方说明](../THIRD_PARTY_NOTICES.md)。

自定义背景不包含在课表 JSON 导出中，换设备后需重新选择，见 [数据与迁移](data-and-migration.md)。
