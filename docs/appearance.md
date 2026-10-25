# 外观与资源

本轮保留最新 1.5.1 的列表/平铺布局、实体导航、深浅模式和显示设置，移除 App 内置背景插画。未选择图片时使用主题底色，不请求任何背景图像。

| 资源 | 用途 |
|---|---|
| ui/src/assets/brand/app-icon.webp | 1084×1084 透明品牌源图，界面和 Android 启动器共用 |
| app/build/generated/res/brand/drawable-nodpi/app_icon.webp | Gradle 生成的启动器资源，不提交 |
| app/src/main/res/drawable-nodpi/live_icon.webp | 256×256 通知彩图 |
| app/src/main/res/drawable/ic_notification.xml | 系统着色的单色通知图标 |
| ui/src/assets/icons/ | 本地 SVG 图标子集和许可证 |

品牌 PNG 转为无损 WebP，尺寸、RGBA 像素与透明度经过一致性验证，ui/src/assets/manifest.json 保存哈希。背景原图及其转换模块已经删除。

自定义背景接受 JPG、PNG、WebP，单张不超过 10 MB；图片在本机缩小和保存，不上传。选图后可调节 0–30 px 模糊。使用主题底色时禁用模糊控件；关闭自定义图仍保留已选文件，移除后从本机业务数据删除。

导航、课程、文本和图片的布局应在窄屏/宽屏、横屏/竖屏以及深浅模式下可用。品牌美术由作者提供，其授权范围与 GPL 业务代码及第三方图标许可分别说明。
