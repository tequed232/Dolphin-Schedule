# 原生架构

Dolphin Schedule 的界面与关键业务由 Kotlin＋Jetpack Compose 实现。`NativeActivity` 是正式启动入口；应用不加载旧业务网页，不包含独立 Web 产品或网页发布流程。

## 分层与数据流

```text
用户操作
  → Compose 页面与 DolphinViewModel / 各功能 Controller
  → domain 业务规则、导入解析与校验
  → NativeRepository 的 SQLite 事务
  → StateFlow 发布已提交快照
  → Compose 重新显示；同步当前导航行程所需的小型镜像
```

| 层 | 主要位置 | 职责 |
|---|---|---|
| 页面 | `app/src/main/java/com/dolphin/schedule/ui/` | 原生绘制、触摸、无障碍、输入和系统结果入口 |
| 状态 | `DolphinViewModel` 及功能 Controller | 页面路由、选中日期、草稿、异步任务、错误和确认状态 |
| 业务 | `domain/` | 课程、周次、布局、冲突、节次、教材关联及纯转换规则 |
| 持久化 | `data/` | SQLite、编码、事务、迁移及应用私有草稿文件 |
| 文件导入 | `importing/` | JSON / CSV / Excel 单元格处理、工作表、映射和预览 |
| 教务 | `academic/` 与 `assets/academic/extract.js` | 学校访问限制、用户登录、页面识别与结果回传 |
| 系统能力 | Kotlin 根包与 `media/` | 主动导航、行程通知、文件共享、图片处理 |

## 一份已提交数据

`NativeRepository` 将 `AppData` 编码为有版本号的 SQLite 快照，数据库为 `dolphin-native.db`，通过 WAL 和事务提交。课程、教材、偏好、自选背景及最近一次教务撤销快照属于同一应用数据模型。

更新先读取当前快照，检查业务条件，再编码并提交。事务成功后才发布界面状态；失败保留当前数据。完全相同的更新不增加版本。需要防止旧内容覆盖时，使用课表基线或课程/教材等目标的预期值检查，不能只靠页面打开时的快照保存。

导航行程使用 SharedPreferences 小型镜像，包含所需的设置、教材名称和导航目标。它不是第二份课程数据库，也不承担课前提醒。通知副作用失败应与课程保存结果区分。

## 三种受限 WebView

| 用途 | 读取内容 | 边界 |
|---|---|---|
| 学校教务 | 用户允许学校的 HTTPS 页面 | 独立教务 Profile；用户自己登录；只提取课表与同源学期信息，外部页面没有应用业务能力桥 |
| Excel 解码 | 构建内置的 SheetJS 与所选文件字节 | 无界面、本地来源、禁止网络和外部文件访问；只返回单元格，课程规则和保存仍由 Kotlin 处理 |
| 首次升级迁移 | 旧本地来源的 IndexedDB `dolphin-schedule` | 仅 SQLite 尚无数据时读取；返回本地状态后销毁；不加载旧业务 HTML，不请求网络，不删除旧库 |

Android 系统 WebView 是这些能力的运行环境，不是 Compose 的页面渲染器。教务隔离配置不可用时应明确报错并保留课表；更新系统 WebView 后再重试。不能通过降低学校访问或证书限制来绕开错误。

## 导入与草稿

文件和教务导入都经过“读取 → 转换校验 → 日期/作息核对 → 差异预览 → 用户确认 → 事务提交”。空课表、损坏或超限数据不能静默替换当前课表。文件导入的错误明细需要用户核对；手动映射和工作表选择应保留到重新预览。

教务导入前后课表在同一事务记录，以便撤销。草稿保存在应用私有目录，页面状态只保留标识和有界输入；恢复草稿后重新核对，不自动补交上次未确认的写入。

## 导航与行程

地图地址只包含学校和识别出的楼栋，完整教室号保留在 App。地图由用户点击后打开；成功打开后，根据通知设置和权限创建最长 30 分钟的行程。行程令牌隔离到达、删除和超时操作，旧操作不能结束后来启动的行程。课程或目标已变化时，撤下失效行程，不后台恢复。

通知仅保留这个导航行程链路。应用不再调度课前提醒、桌宠、台词、系统日历同步或节假日查询。

## 资源和构建

- 唯一应用版本来自根 `gradle.properties` 的 `dolphinVersion`，由 Gradle 生成应用版本与版本码。
- 品牌源图位于 `app/src/main/res/drawable-nodpi/app_icon.webp`，学校提取脚本位于 `app/src/main/assets/academic/extract.js`。
- npm 仅锁定 Excel 解码器并运行维护工具。Gradle 将其复制为 `generated/assets/spreadsheet/xlsx.js`。
- Gradle 将根 MIT、第三方说明与 Apache-2.0 正文复制到 `generated/assets/legal/`，供“关于”离线阅读。
- 合成 Excel 工作簿只生成到测试资源；设备截图、日志、工具链和 APK 放在忽略目录。

目录规则见 [目录指南](directory-structure.md)，数据范围见 [数据与迁移](data-and-migration.md)，验证入口见 [开发说明](development.md)。
