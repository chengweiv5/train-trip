# Not-yet-sale parsing Implementation Plan

> **For agentic workers:** Execute this focused fix inline against the reviewed spec, preserving failure isolation from 0.2.1.

**Goal:** 起售前 `*` 不再导致整项解析失败，也不被误报为有票。

**Architecture:** 在 TicketParser 的已解析 SaleState 上下文中，单独将 `*` 转为 AvailabilityKind.NOT_YET。其他席别值沿原解析路径；保留原始起售文案和完整 trips。

**Tech Stack:** Kotlin、JUnit、kotlinx.coroutines、Android Compose。

## Global Constraints

- 只接受 NOT_YET 车次的 `*`；无上下文、OPEN、SUSPENDED、CLOSED 均不放宽。
- 新状态不能 confirmedFor 或 uncertainFor；未知协议错误继续保留。
- 无实时票务查询、无新依赖、无请求频率变化。
- APK 0.2.2 / code 6，同签名覆盖安装，保留真机设置。

### Task 1: 解析与回归

**Files:**
- Modify: `core/src/main/kotlin/cn/traintrip/core/TicketParser.kt`, `Models.kt`
- Modify: `core/src/test/kotlin/cn/traintrip/core/SearchEngineTest.kt`
- Create: `core/src/test/kotlin/cn/traintrip/core/NotYetSaleTest.kt`
- Create: `core/src/test/resources/official-not-yet-sale-sanitized.json`

**Interfaces:** 保留 `TicketParser.availability(String)` 与 `parse` 签名；AvailabilityKind 添加 NOT_YET，label 返回“尚未起售”。

- [x] 添加原始 310 条脱敏 fixture 测试、单条 C2551 最小复现、混合已售/未售过滤、其他发售状态及未知值拒绝、全部席别占位处理。固定查询时刻，运行 `:core:test --offline`，确认原症状失败。
- [x] 实现以下条件映射，保留原始字段：

```kotlin
val raw = f[it.field]
if (sale == SaleState.NOT_YET && raw == "*")
    SeatAvailability(raw, AvailabilityKind.NOT_YET)
else availability(raw)
```

- [x] 将上一轮解析失败测试的 `*` 改为真正未知的 `未知状态`，继续验证错误隔离。
- [x] 回放原始 fixture，断言 310 条完整保留、291 条未售、6 条已售、C2551 起售文案保持、UNKNOWN 数量为 0；验证只有 OPEN 车次参与现票/待核验汇总。

### Task 2: Android 与真机交付

**Files:**
- Create: `app/src/androidTest/kotlin/cn/traintrip/app/NotYetSaleFlowTest.kt`
- Modify: `app/build.gradle.kts`, `README.md`
- Create: `docs/verification/2026-09-19-not-yet-sale-parsing.md` 及证据目录。

**Interfaces:** 注入 TicketSource.query 使用真实 TicketParser 处理离线混合记录，AppViewModel 和页面使用正式路径。

- [x] 模拟器验证一个未售星号车次和一个已售有票车次同项成功；只显示后者，不显示失败原因按钮；同跑 SearchFailureTest 验证失败隔离未退化。
- [x] versionCode=6，versionName=0.2.2；执行 `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`。
- [x] 保存当前设备 APK、偏好，核对签名；`adb install -r` 覆盖安装，验证版本、启动、已安装 APK 哈希和原偏好完全一致。
- [x] 记录测试结果、回滚位置和已验证边界，提交到功能分支；通过 punk-12 发送并回读完成通知。
