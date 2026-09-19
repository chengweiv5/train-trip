# 蓝白 UI 实施计划

> 在当前任务逐项实施和验收。用户于2026-09-19确认全部蓝白设计稿。

**Goal:** 将现有Android界面改为已确认的蓝白出行工具风格，保留全部查询与内容能力。

**Architecture:** 继续使用现有Compose页面和ViewModel。先统一语义颜色、字体、按钮和导航，再改页面结构；网络、缓存、密钥和查询模型不变。弹层共享全屏面板容器以保持窄屏可用。

**Tech Stack:** Kotlin、Jetpack Compose Material3、Gradle、Android仪器测试、ADB。

## Global Constraints

- 版本0.4.0 / 8。依据design/train-trip-blue-white.pen的17张画板。
- 主蓝#1769E8、页面灰#F5F7FA、白卡、圆角12dp、主按钮48dp/8dp；导航18sp、正文14–15sp、时刻28sp。
- 16dp页面边距；排序/刷新内部各16dp；所有交互热区至少48dp。
- 整卡临时标记，席别只读，更多席别独立打开，未选择也能打开12306 App。
- 景点/美食/玩法/贴士及各页来源/更新入口保留。DeepSeek和Tavily配置独立，密钥遮罩及FLAG_SECURE保留。
- 测试使用离线数据，不调用付费服务；真机保留数据覆盖安装，不切换用户前台应用。

### 1. 通用样式和首页

文件：app/src/main/kotlin/cn/traintrip/app/ui/{Theme,Components,FiltersScreen}.kt；app/src/main/res/values/styles.xml。

- [x] 检查远端和待合并PR（无），rebase origin/main，备份app/src。
- [x] 颜色改用Primary、PrimaryTint、PageBackground、Ink、Muted、AvailableGreen等语义变量，更新全UI引用；绿色只用于有票/成功。
- [x] 通用按钮和卡片应用尺寸：

```kotlin
Button(onClick, modifier.fillMaxWidth().heightIn(min=48.dp),
    shape=RoundedCornerShape(8.dp), contentPadding=PaddingValues(12.dp)) { Text(text) }
Surface(shape=RoundedCornerShape(12.dp),color=Color.White) { Column(Modifier.padding(14.dp),content=content) }
```

- [x] 增加AppTopBar(title,onBack,action)，使用Canvas矢量返回/设置/刷新/折叠图标。首页导航“有票就出发”，将出发地/日期/时段合为白色表单，席别/人数/车程/目的地另组列表。
- [x] 保留所有回调与testTag，编译后验证首页/设置/查询入口可达。

### 2. 城市结果和车次列表

文件：ui/{ResultsScreen,ProvinceResultGroup,DetailScreen,DetailActions,Components}.kt。

- [x] 结果页Scaffold固定中文导航，顶部查询摘要只出现一次。省份改为轻量标题；城市小图可选，车程/建议时长/余票分层，两个入口左右排列。
- [x] 保留独立分组状态、停止/继续/重试/未开售和失败说明；保持以下对称按钮内边距：

```kotlin
val actionPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)
```

- [x] 车次导航使用起终城市，日期横滑，选中为蓝边白卡；时刻28sp，绿色余票只读。窄屏/大字体将历时和底部查询时间换行，更多席别热区独立。
- [x] 底部仅显示可选标记摘要、打开12306 App、购票说明；按钮不依赖选中。
- [x] 运行DetailFlowTest、DetailLayoutTest、SearchFailureTest、NotYetSaleFlowTest及分组回归。

### 3. 目的地和设置

文件：ui/{DestinationGuideScreen,DestinationGuideContent,SettingsScreen}.kt。

- [x] 目的地使用白色导航和紧凑摘要，下划线四分页；照片缩小、编号弱化。保留pager/列表状态、来源、左对齐更新和固定查看车次。
- [x] 玩法按时间顺序展示路线，内容仍读取现有guide结构，不生成新数据。
- [x] 设置菜单使用白色标准列表、右侧状态。二级输入区统一8dp圆角和留白，保持空Key保留旧值、独立移除、保存期间禁用返回。
- [x] 运行DestinationUiTest、DestinationGuideTest、SettingsMenuTest及DeepSeekDestinationTest。

### 4. 筛选面板、适配及交付

文件：ui/{FilterSheets,DateFilterSheet,TimeFilterSheet,DestinationSelector}.kt；新增ui/FilterPanel.kt；app/src/androidTest/kotlin/cn/traintrip/app/BlueWhiteUiTest.kt。

- [x] 增加全屏FilterPanel(title,onDismiss,footer,content)，使用DialogProperties(usePlatformDefaultWidth=false)，顶部取消、滚动主体、固定完成按钮；日期与席别接入，草稿只在完成后提交。
- [x] 保留连续日期范围/最多31天/未来月份、全部席别、双端时间滚轮和按省目的地筛选。
- [x] 增加离线界面验收测试，覆盖390dp和320dp/1.3字号，以及31天跨月/2.0字号；检查主要操作可见、文字布局无溢出、排序/刷新两边对称，导出主要页面截图。
- [x] JDK21运行 ./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug；专用模拟器运行受影响仪器测试，核对截图与OCR。
- [x] 扫描产物密钥格式，备份真机安装包与配置指纹，adb install -r，回读APK哈希/配置指纹/前台应用。
- [x] 保存docs/verification/2026-09-19-blue-white-ui.md；本地提交实现，更新任务状态，发送并回读完成通知。推送前刷新并确认可快进。

## 回滚

源码在功能分支以反向提交回滚；原始文件位于.verification-private/v0.4.0/blue-white-implementation/src-before。真机旧APK备份后可adb install -r保留数据回滚。不得卸载或清数据。

## 计划复核

覆盖17张设计中的全部页面和状态；实际字号跟随系统；不引入账户/订单/社区、不改查询和生成接口。低影响颜色不写镜像测试，实际页面结构和交互用离线仪器测试验证。
