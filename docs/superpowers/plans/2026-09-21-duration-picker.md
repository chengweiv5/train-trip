# Duration Picker Implementation Plan

> 当前隔离工作树直接逐步执行；brainstorming规格审阅独立进行。执行类子技能未安装，沿用当前会话实施与验证。

**Goal:** 最长车程预设改为3小时和5小时，自定义使用小时/分钟滚轮。

**Architecture:** 独立DurationFilterSheet负责草稿、预设与校验，复用原NumberPicker滚轮；SearchFilters和Preferences格式不变。

**Tech Stack:** Kotlin、Jetpack Compose、Android NumberPicker、离线instrumentation。

## Global Constraints

规格：docs/superpowers/specs/2026-09-21-duration-picker-design.md。版本0.6.0/10，自定义1–6000分钟，100小时分钟固定00；不改票源、密钥或收藏，不安装/推送/更新分发包。

## Task 1: 时长面板与滚轮复用

Files: 新增ui/DurationFilterSheet.kt和ui/TimeWheel.kt；修改ui/FilterSheets.kt、ui/TimeFilterSheet.kt。

- [x] 抽取原TimeWheel为internal，原出发时段调用保持一致；update同步maxValue并增加wrap参数（默认true）；时长小时传wrap=false，分钟在100小时时禁用。
- [x] FilterSheet分派duration：`if(kind=="duration") { DurationFilterSheet(s.filters,onDismiss,onApply);return }`，移除旧durationText、数字文本框和解析分支。
- [x] DurationFilterSheet使用saveable草稿：初始mode为unlimited/three/five/custom，customMinutes为原值或180；`selected = when(mode) { "unlimited"->null; "three"->180; "five"->300; else->customMinutes }`，仅完成`onApply(filters.copy(maxMinutes=selected))`。
- [x] 小时回调`customMinutes=if(hour==100)6000 else hour*60+customMinutes%60`；分钟回调`customMinutes=customMinutes/60*60+minute`。完成启用条件`(selected==null || selected in 1..6000) && scrolling.values.none { it }`；0显示至少1分钟，取消只调用onDismiss。
- [x] 四项按钮在maxWidth<340dp或fontScale>1.15时两列，其它单行；自定义滚轮两列小时/分钟，正文可滚动，底部取消/完成固定可达。

## Task 2: 验证、设计与记录

Files: 新增app/src/androidTest/kotlin/cn/traintrip/app/DurationPickerTest.kt；design/train-trip-surface-contrast.pen；design/README.md；design/surface-contrast.md；README.md；docs/verification/2026-09-21-duration-picker.md。

- [x] 新增5项instrumentation：预设与不限，旧240/480及自定义取消，0/1/6000和100小时分钟禁用，草稿恢复/大字体布局，手指滑动后完成保存且无键盘。使用已有chooseWheel辅助，断言SearchFilters.maxMinutes实际值及未应用前原值不变。
- [x] JDK21构建`:app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease`。
- [x] 专用emulator-5586跑DurationPickerTest、TimeRangePickerTest及DepartureTimeUiTest#customCancelPreservesMultiSelectAndDoneReplacesIt，确认原时段滚轮和多选未回退。内部截图和OCR复核普通/大字体。
- [x] Pencil新增09最长车程设计画板，保留B4；检查节点、裁切/重叠和渲染，原生保存后核对源文件哈希和目标节点；独立副本后台打开返回占位画布，未完成独立副本回读。用户已切换项目，停止窗口操作。
- [x] 更新说明、验收与TASK_STATE；关闭专用模拟器，本地提交、punk-12通知及回读。

自检：预设/自定义/边界/草稿→Task1；持久值和旧数据/回归/设计/回滚证据→Task2。无持久化迁移。
