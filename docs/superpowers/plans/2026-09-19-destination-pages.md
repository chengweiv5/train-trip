# 目的地分页 Implementation Plan

> **For agentic workers:** Implement the following tasks inline in the existing isolated worktree. The optional superpowers execution skills are not installed; use the repository's build and test tools directly.

**Goal:** 实现已确认的四主题分页和统一景点 / 美食卡片，保留阅读状态。

**Architecture:** DestinationGuideScreen 管理可保存分页、每页 LazyListState 和天数。DestinationGuideContent 提供 LazyListScope 内容与专用卡片。沿用现有导航和资料来源弹窗。

**Tech Stack:** Kotlin、Jetpack Compose Foundation Pager / Material3、Android 仪器测试。

## Global Constraints

- 在 codex/destination-guide-pages-design 分支实现；修改前备份。
- 颜色、圆角、标题与确认样稿一致；景点与美食复用同一组件。
- 每页状态独立、城市隔离；48dp 点击区域；320dp / 1.3 字体可读。
- 不新增资料或网络依赖，不操作个人手机，不改变票务路径。

### Task 1: 分页与卡片

**Files:** `DestinationGuideScreen.kt`、新建 `DestinationGuideContent.kt`、`TrainTripApp.kt`。

**Interfaces:** `DestinationGuideScreen(cityName, guide, onBack, onTrains, onSource, provinceLabel = "")`；`GuideSection` 包含 PLACES、FOOD、PLANS、TIPS；`LazyListScope.guideSectionContent(section, guide, days, onDays)` 只负责分页内容。

- [x] 给现有目的地测试加入点击“玩法”前置动作，新增分页点击/横滑和保留测试；旧代码应找不到分页标签。
- [x] 用 `key(guide?.cityId ?: cityName)` 包裹页面状态；`rememberPagerState(pageCount = { 4 })` 及四个 `rememberLazyListState()` 保存页面与位置。
- [x] `HorizontalPager` 放在 Column 的 `Modifier.weight(1f)` 区域，选中标签调用 `animateScrollToPage(index)`；每页独立 LazyColumn。
- [x] 顶部返回、主题标签和底部车次固定；`maxHeight < 640.dp || fontScale > 1.15f` 时把摘要移至每页列表首项。
- [x] `GuideContentCard` 使用白底、18dp 圆角、1dp 浅边、16dp padding；景点与美食共用标题、编号、描述；景点使用可选 meta 区域。
- [x] 补充玩法按日卡片、贴士卡片与每页来源入口，保持原文与 URL；无资料分支保留。

### Task 2: 验证与交付

**Files:** `DestinationGuideTest.kt`、`docs/verification/2026-09-19-destination-pages.md`、`README.md`、`design/README.md`。

- [x] 执行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ANDROID_HOME=/Users/bytedance/Library/Android/sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`。
- [x] 仅在隔离模拟器安装主包和测试包，执行 `am instrument -w -e class cn.traintrip.app.DestinationGuideTest,cn.traintrip.app.DetailFlowTest cn.traintrip.app.test/androidx.test.runner.AndroidJUnitRunner`。
- [x] 验证四页、1/2 日、来源 URL、无资料、返回恢复、分页独立滚动、跨城市、首尾、320dp / 1.3 字体及短高度；回读语义和实际文字行边界。
- [x] 保存 JPEG 截图并目视检查；对标准和窄屏执行 Vision OCR，记录覆盖边界。
- [x] 复制 APK 为 `artifacts/train-trip-destination-pages-debug.apk`，记录 SHA-256 和签名；更新文档、任务状态、提交代码并通过 punk-12 通知。

## 自检

规格中四分页、统一卡片、适配、状态恢复、来源、空态、原路径均有实现和验收任务。不引入新版数据、全局组件变化或自动购票。
