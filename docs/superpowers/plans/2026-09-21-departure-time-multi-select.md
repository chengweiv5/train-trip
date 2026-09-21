# Departure Time Multi-select Implementation Plan

> 执行方式：当前隔离工作树直接逐步执行。已按brainstorming要求安排独立规格审阅；执行类子技能未安装，沿用本仓库当前会话执行方式。

**Goal:** 出发时段快捷多选、正确并集过滤及保存恢复，自定义仍是一个连续区间。

**Architecture:** 核心层统一快捷时段语义与切换规则；SearchFilters保留旧单段字段并新增枚举集合；Preferences负责兼容读取；Compose复用现有筛选与滚轮。

**Tech Stack:** Kotlin/JVM、Jetpack Compose、SharedPreferences JSON、离线JUnit与Android instrumentation。

## Global Constraints

规格：`docs/superpowers/specs/2026-09-21-departure-time-multi-select.md`。版本0.6.0/10、无新增依赖、不改票源/密钥/收藏、不自动安装或推送。保留B4配色与按钮行；所有时段按北京时间逐日、左闭右开。

## Task 1: 核心时段规则与并集

Files: `core/src/main/kotlin/cn/traintrip/core/DepartureTime.kt`、`Models.kt`、`core/src/test/kotlin/cn/traintrip/core/DepartureTimeTest.kt`。

Interfaces: `DeparturePeriod(label,startMinute,endMinute)`；SearchFilters追加`departurePeriods:Set<DeparturePeriod>`；扩展属性`isAllDay`、`selectedDeparturePeriods`，方法`withDeparturePeriods`、`toggleDeparturePeriod`、`withCustomTime`。

- [x] 写失败测试：`SearchFilters().toggleDeparturePeriod(MORNING).toggleDeparturePeriod(EVENING)`匹配360/719/1080/1439，不匹配0/359/720/1079；`aggregate`仅返回早晚且不重复。穷举15种非空组合的每分钟结果，四项为全天。
- [x] `./gradlew :core:test --tests '*DepartureTimeTest'`确认新接口缺失导致失败。
- [x] 新枚举四段；`withDeparturePeriods`空/全选四项归一全天，其余保存集合并把from/to设为最早项；`toggleDeparturePeriod`以当前回显集合增删；`withCustomTime`清空集合。`acceptsTime`非空按any判断，否则沿用单段/跨午夜，拒绝0..1439外输入。
- [x] 测试最后取消恢复全天、全天开始仅选一段、精确旧单段回显、自定义覆盖、跨午夜、copy保留及validate按实际模式判断；运行核心全量离线测试。

## Task 2: 保存恢复与筛选UI

Files: `Preferences.kt`、`ui/Components.kt`、`ui/FiltersScreen.kt`、`ui/TimeFilterSheet.kt`、`app/src/androidTest/kotlin/cn/traintrip/app/DepartureTimeUiTest.kt`。

- [x] Preferences读取旧SearchFilters后解析periods数组；只有全量有效且非空时调用withDeparturePeriods，否则保留from/to。写入按开始时间排序的稳定枚举名数组，保留from/to和其它字段。
- [x] Choice末尾新增`toggle:Boolean=false`，为时段项使用`Modifier.toggleable(selected,role=Role.Checkbox,onValueChange={onClick()})`，其它调用保持clickable。
- [x] 首页时间行使用核心枚举；全天回调withCustomTime(0,1440)，各段回调toggleDeparturePeriod；增添可多选提示。`timeIntervalsText`输出多个实际区间，`timeRange`输出按时间排序的中文label或旧单段时刻。
- [x] 自定义完成使用withCustomTime(start,end)；多选进入时明确“完成后将替换已选快捷时段”，草稿初始为最早段。取消不写回。
- [x] Android新增测试：多选/取消/全天、保存后重新实例恢复、旧偏好及损坏字段回退不改变其余字段、自定义取消和完成、单城条件隔离；在320dp/1.3倍字体检查文本布局和动作。复用4项TimeRangePickerTest、2项首页B4与首页设置回归。

## Task 3: 设计同步和交付

Files: `design/train-trip-surface-contrast.pen`、`design/surface-contrast.md`、`README.md`、`docs/verification/2026-09-21-departure-time-multi-select.md`。

- [x] Pencil目标文档添加早上+晚上多选示例与交互说明；不改变已确认颜色/顶部，编辑仅针对目标文档；保存时临时切换并恢复原窗口，验证可编辑节点、布局、保存及内部截图。
- [x] JDK21宿主运行 `./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease`。
- [x] 专用emulator-5586运行指定离线测试；拉取截图复核，结束关闭本任务模拟器。
- [x] 更新验收及TASK_STATE，本地提交、通知punk-12并回读，不自动安装、推送或发布。

自检：规格核心并集/边界→Task1；持久化与UI/自定义→Task2；设计、构建、回滚证据→Task3。
