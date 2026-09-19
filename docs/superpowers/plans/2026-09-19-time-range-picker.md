# Time Range Picker Implementation Plan

**Goal:** 同一时间窗口设置出发时段两端并同步 Pencil。

**Architecture:** 新增 TimeFilterSheet，内部用 rememberSaveable 保存分钟数草稿，通过两个 TimeWheels 包装原生 NumberPicker。FilterSheet 的 time 分支直接路由；完成回调保持 SearchFilters 接口。

**Tech Stack:** Kotlin、Compose、Android NumberPicker、Pencil MCP。

## Global Constraints

- 当前 codex/ui-polish 分支，保留现有未提交改动；修改前备份。
- 分钟范围保持原有 0..1439 / 0..1440，跨午夜和全天语义不变。
- UI 修改同步 design/train-trip-v1.pen；测试仅用临时模拟器。

## 实施

- [x] 新增 app/src/main/kotlin/cn/traintrip/app/ui/TimeFilterSheet.kt：双组滚轮、草稿、跨午夜说明、取消/完成、全天、24 点锁定分钟。
- [x] FilterSheets.kt 删除文本时间输入与 parseMinute，time 路由到新弹层。
- [x] 更新 AppFlowTest.timeAndScopeApply，新增 TimeRangePickerTest 覆盖实际滚轮、取消/保存、边界、恢复和字体；离线执行这些定向测试。
- [x] 更新 Pencil HceGu 的滚轮、提示和操作；更新 gNcl5 时段说明；保存与导出参考图，检查布局/OCR。
- [x] 构建、lint、APK 版本 0.1.2；保留旧包，真机更新安装并检查偏好不变；完成记录及通知。
