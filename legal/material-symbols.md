# Material Symbols 资源来源

应用使用 Google 的 **Material Symbols Rounded** 静态 Android 矢量子集，采用 [Apache-2.0](APACHE-2.0.txt) 许可。上游：[google/material-design-icons](https://github.com/google/material-design-icons)，固定提交 `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7`。

原文件位于 `symbols/android/<名称>/materialsymbolsrounded/<名称>_24px.xml`；实心版本为 `<名称>_fill1_24px.xml`。本地文件加 `ms_` 前缀，去掉依赖 AppCompat 的 `colorControlNormal` tint 属性，由 Compose 的 Material 3 颜色角色着色；路径几何保持上游原样。不附带字体、下载服务或完整图标包。

| 用途 | 上游名称 |
|---|---|
| 胶囊导航 | `view_agenda`、`view_week`、`search`、`account_circle`；除 search 外包含选中实心版本 |
| 首页操作 | `add`、`upload_file`、`calendar_month`、`schedule`、`chevron_left`、`chevron_right`、`near_me` |
| 设置 | `event_note`、`menu_book`、`home`、`palette`、`notifications_active`、`system_update_alt`、`database`、`info`、`wallpaper`、`fullscreen` |

资源共 24 个，位于 `app/src/main/res/drawable/ms_*.xml`。保留上游许可；应用品牌图不属于此资源集。
