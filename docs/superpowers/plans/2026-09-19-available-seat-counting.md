# “有”直接计入有票城市 Implementation Plan

> 在当前任务内顺序实施；用户已明确授权本行为和真机安装。

**Goal:** “有”对 1–20 人直接计入有票城市，页面和重新核验语义一致。

**Architecture:** SeatAvailability 统一余票判断，Trip 保留发售及筛选条件；移除旧数量待核验分流。

**Tech Stack:** Kotlin / JUnit / Compose Android instrumentation / Gradle。

## Global Constraints

- 不虚构张数，不跨车次或席别合并数字余票。
- 不改变未起售解析、失败隔离、查询调度或五城资料范围。
- 功能分支开发；源码已备份；同签名覆盖安装并保留设备数据。

## Task 1: 规则与回归

文件：核心 Models、ProvinceResults；AppViewModel；ResultsScreen、ProvinceResultGroup、DetailScreen、FilterSheets；AvailableSeatTest、AvailableSeatFlowTest 及现有相关测试。

- [x] 新增真实解析器到聚合测试：1/2/20 人均计入天津，count=null；数字不足及发售/筛选条件不符均排除。
- [x] `./gradlew :core:test --tests cn.traintrip.core.AvailableSeatTest --offline` 确认多人断言先失败。
- [x] `confirmedFor` 对 AVAILABLE 返回 true；label() 统一“有票”；删除 Trip/aggregate/groupResults/UI 中的 uncertain API 和旧文案，重新核验共用正常判断。
- [x] Android 双人离线交互验证城市统计、省份卡片、详情席别、“有”重新核验成功以及最新1张/无票时阻止跳转。
- [x] 更新 0.2.3 / code 7；运行 `./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`；专用模拟器运行新交互和未售/失败/省份回归。

## Task 2: 真机交付

- [x] 备份真机安装当时 APK 和偏好；确认签名一致后 `adb -s FMR0224725012307 install -r`。
- [x] 启动 MainActivity，拉回已安装包核对哈希，核对版本、前台活动和偏好未变。
- [x] 保存 APK、验收报告，关闭本轮专用模拟器。
- [ ] 提交功能分支并发送 punk-12 完成通知、回读验证。

源码备份 `/private/tmp/train-trip-available-wgv8qrxi/source.tar`；设备备份放在同目录。回滚优先使用基线代码构建更高版本号且相同签名的 APK 覆盖安装。
