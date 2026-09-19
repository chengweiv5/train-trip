# 目的地分页与统一卡片验收

2026-09-19。基线 `6ec8b27`，分支 `codex/destination-guide-pages-design`。用户已确认交互样稿；规格评审 Approved。版本沿用 0.4.0 / code 8。

## 实现

- 目的地页改为景点、美食、玩法、贴士四个原生分页，支持点击和横滑，首尾停止；标签始终可见。
- 景点和美食共用白色独立卡片：18dp 圆角、16dp 内边距、相同编号和标题。景点附加地点与耗时，美食保持原有资料。
- 紧凑城市摘要显示照片、省级地区、建议天数、游玩强度、推荐语和标签。常规竖屏固定摘要；安全区内高度低于 640dp 或字体大于 1.15 时摘要随分页内容滚动。窄屏隐藏装饰图标，保留全部标签文字。
- 底部固定查看车次。每页独立滚动，分页与 1/2 日选择可恢复；沿用城市和查询会话隔离。返回行为保持原有路径。
- 玩法按日分卡，选中使用浅绿；季节与到站建议使用相同卡片。每页都有来源与署名入口；资料、许可证和 URL 保留。无资料城市保留提示与车次入口。

## 验证证据

| 检查 | 结果 |
| --- | --- |
| 修复前用例 | 新增分页用例在旧界面因缺少 guide-tab-places 失败；[日志](destination-pages/before-test.log) |
| 构建 / 测试包 / lint | 全部通过；0 错误、12 条原有警告；[构建](destination-pages/build.log)、[lint](destination-pages/lint.txt) |
| 目的地与车次回归 | 9 项目的地 + 6 项 DetailFlow，`OK (15 tests)`；[日志](destination-pages/offline-regression.log) |
| 最终目的地复验 | 最终配色、图标、安全区用例调整后 `OK (9 tests)`；[日志](destination-pages/final-guide-tests.log) |
| 状态与导航 | 点击 / 横滑同步、首尾不循环、各页滚动位置独立、配置恢复、1/2 日恢复、目的地→车次→返回、不同城市及同城新查询会话隔离、直接查看车次、无资料、正确来源 URL |
| 布局 | 320dp / 1.3 字体、320dp × 430dp 短屏；可见文字行无省略或越界；分页与主操作至少 48dp；底部主操作在安全区内，末尾来源可达 |
| 视觉 | 12 张运行截图，标准景点、美食、大字体玩法及短屏已目视复核；4 屏 Vision OCR 见 [记录](destination-pages/ocr.json) |
| 安装包 | APK v2 签名验证通过；[摘要](destination-pages/summary.json) |

截图：[景点](destination-pages/runtime/08-places.jpeg)、[美食](destination-pages/runtime/09-food.jpeg)、[大字体玩法](destination-pages/runtime/07-large-font-plan.jpeg)、[短屏末尾](destination-pages/runtime/12-short-viewport.jpeg)。分页列表上下边缘对离屏内容的裁切属于正常滚动视口，不是卡片文本溢出。

测试在本任务专用 API 36 只读模拟器 `emulator-5592` 执行；仅使用离线可控票务和现有资料，未查询实时余票。最终复验之前有一次 Compose 作用域编译错误，修正后构建通过；误启动的旧包测试已停止，其结果不计入最终验收。独立组件测试原未包含系统安全区，已与 App 容器保持一致后重新验证。

## 交付与回滚

安装包：`artifacts/train-trip-destination-pages-debug.apk`，SHA-256 `b776ff51d3c91348c5e5e5788b3686124d6e3e758a0387b5507caf6148c18f0f`。本轮未覆盖安装个人手机、未推送远端。

确认样稿可编辑源：[HTML](../../design/reference/destination-pages/approved-preview.html)，为 Codex 内联样稿片段，保留设计选项与交互。旧 Pencil 设计文件不改。

修改前源码归档 `.verification-private/destination-pages/source-before.tar`，基线 `6ec8b27`。回滚采用本次实现提交的反向提交，不清空仓库或用户设置；旧 APK 保留，新增 APK 可独立删除。
