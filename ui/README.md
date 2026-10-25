# Android 与浏览器共用界面

这个目录保存 React、TypeScript 和 CSS 界面源码。Android 安装包与 GitHub Pages 从同一套源码构建。

| 路径 | 作用 |
|---|---|
| `index.html`、`src/main.tsx` | HTML 与 React 启动入口 |
| `src/App.tsx`、`src/screens/` | 应用页面和整体交互 |
| `src/components/` | 可复用的课表、编辑器、弹层等组件 |
| `src/lib/` | 数据模型、导入解析、本地存储和内部原生通信 |
| `src/state/`、`src/nav/` | 状态生命周期、保存同步、页面导航与返回 |
| `src/theme/`、`src/assets/` | 主题样式、必要品牌资源与图标 |
| `src/meta.ts` | 应用名称、版本与作者链接 |
| `vite.config.ts`、`tsconfig.json` | 界面打包与 TypeScript 检查配置 |

在仓库根目录执行 `npm run dev` 开发，执行 `npm run build` 检查并构建。输出位于 `ui/dist/`，不提交构建产物。

浏览器保存自己的本地数据；后台提醒和系统日历由 Android 宿主提供。分层与数据流见 [架构说明](../docs/architecture.md)，新增代码的位置见 [文件目录指南](../docs/directory-structure.md#共用界面的职责划分)。
