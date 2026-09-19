# 目的地省市分组 Implementation Plan

> **For agentic workers:** Execute inline in this session. The user approved the UI and requested development; no additional execution confirmation is needed.

**Goal:** 将已确认的省市选择和连续容器式结果折叠设计落实到 Android 应用。

**Architecture:** 行政目录独立于铁路字典，使用行政区代码作为稳定目的地 ID；铁路字典通过有来源的映射归并到目录，Preferences 兼容旧 ID。Compose 选择页保存草稿，结果页使用可恢复的独立省份开合状态与查询会话 ID。

**Tech Stack:** Kotlin/JVM 17、JDK 21、Jetpack Compose、Android SDK 36。

## Global Constraints

- 以 design/destination-ui-proposal.md 的已确认方案为准；直辖市仅一项。
- 保留现有出票判断、待核验和失败/未查/未开售区别。
- 默认仅首个出现结果的省份展开；可独立开合；排序、刷新、详情返回不重置。
- 数据目录来源及年代显式记录；未覆盖地区可见并禁选。
- 重要文件备份：/private/tmp/train-trip-provinces-backup-f3dhu6v8/。不推送、不操作用户手机。

### Task 1: 行政目录及偏好迁移

**Files:** core/src/main/kotlin/cn/traintrip/core/{Models,StationCatalog,AdministrativeCatalog}.kt；core/src/main/resources/{destinations,station-city-mapping}.json；app/src/main/kotlin/cn/traintrip/app/Preferences.kt；core/src/test/kotlin/cn/traintrip/core/DestinationCatalogTest.kt。

**Interfaces:** `Province(id,name,shortName,pinyin)`；`City(id,name,stations,province,pinyin)`；`StationCatalog.resolveCityId(id):String?`；`StationCatalog.searchDestinations(query):List<City>`；`StationCatalog.normalize(filters):SearchFilters`。

- [x] 建立有版本和许可说明的行政目录；以六位代码为目的地 ID，直辖市使用省级代码补零，省直辖单位展开到实际行政单位。
- [x] 将车站字典旧城市 ID 映射到目录。保留每一车站的名称/代码；无归属的记录隔离而非猜测。
- [x] 迁移偏好并测试合并去重、出发站保留和跨省搜索。

```kotlin
@Test fun municipalitiesHaveNoCountyDestinations() {
    val c=StationCatalog.bundled()
    assertEquals(listOf("500000"),c.cities.filter { it.province.id=="50" }.map { it.id })
    assertEquals("500000",c.resolveCityId("3102")) // 旧万州
    assertEquals("110000",c.resolveCityId("0357"))
}
```

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test`；预期全部通过。

### Task 2: 全屏省市选择

**Files:** app/src/main/kotlin/cn/traintrip/app/ui/{DestinationSelector,FilterSheets,FiltersScreen}.kt。
**Interfaces:** `DestinationSelector(s,onDismiss,onApply,browserState)`；`onApply(SearchFilters)` 仅完成时触发。

- [x] 左省右城市、搜索中文和拼音、按省匹配全城市、跨省草稿、当前省份全选、已选分组移除。
- [x] 取消/返回丢弃草稿，完成禁用零选择；当前出发地和无车站城市提供禁选原因。
- [x] 浏览省份/滚动位置可恢复；窄屏和大字体改省份下拉及单列。

```kotlin
compose.onNodeWithText("搜索省份或城市").performTextInput("hebei")
compose.onNodeWithTag("destination-130100").assertExists()
compose.onNodeWithText("清空选择").performClick()
compose.onNodeWithTag("apply-destinations").assertIsNotEnabled()
```

### Task 3: 省内结果卡片与查询状态

**Files:** app/src/main/kotlin/cn/traintrip/app/ui/{ResultsScreen,ProvinceResultGroup,DetailScreen,TrainTripApp}.kt；app/src/main/kotlin/cn/traintrip/app/AppViewModel.kt；core/src/main/kotlin/cn/traintrip/core/ProvinceResults.kt。
**Interfaces:** `groupResults(catalog,filters,progress,byCount,uncertain)` 生成按省份和省内排序的组；`UiState.searchSession:Long` 仅全新查询递增。

- [x] 连续浅绿圆角省份容器、白色内卡、显式展开收起和底部收起；收起保留三城预览。
- [x] 会话内独立开合、刷新和详情返回保持，首次有票仅开首省；省内排序稳定。
- [x] 对无已确认城市的查询中/失败/未开售/无票状态提供准确省份摘要；待核验继续单列。
- [x] 卡片与详情添加省级地区信息；200ms 动画服从系统动态效果设置。

```kotlin
compose.onNodeWithTag("province-toggle-13").performClick()
compose.onNodeWithTag("city-card-130100").assertDoesNotExist()
compose.onNodeWithTag("province-toggle-37").performClick()
compose.onNodeWithTag("city-card-370100").assertExists()
```

### Task 4: 验收与交付

**Files:** app/src/androidTest/kotlin/cn/traintrip/app/{DestinationUiTest,AppFlowTest}.kt；docs/verification/destination-provinces.md；app/build.gradle.kts。

- [x] 核心测试覆盖目录/迁移/省份搜索/排序/不完整查询状态。
- [x] 模拟器测试草稿、键盘、全选、搜索、独立开合、状态恢复、大字体布局和省级显示。
- [x] 执行 `:core:test :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest`，检查真实截图及 APK。
- [x] 版本更新为 0.2.0 / 4；同步设计确认状态、验证报告和任务记录；发送并回读完成通知。
