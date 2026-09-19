# Query failure isolation Implementation Plan

> **For agentic workers:** Execute this focused task in the current session using the reviewed spec. Steps use checkbox syntax for tracking.

**Goal:** 单项失败不阻塞后续查询，保留失败原因、成功结果和手动取消。

**Architecture:** SearchEngine 在单次 source.query 边界捕获普通异常，独立记录结果并推进。删除 Failure.stopSearch 与连续失败停止规则。初始化和协程取消仍沿原有上层路径处理。

**Tech Stack:** Kotlin/JVM、kotlinx.coroutines、JUnit、Android Compose。

## Global Constraints

- 默认间隔 1100 毫秒，每项一次，无自动重试或新增并发。
- CancellationException 必须原样抛出。
- 有失败时 complete=false；尝试完毕后 remainingCount=0、running=false、stopped=false。
- 保留既有 `*` 解析错误，避免扩大为票态修复。
- 同签名升级为 0.2.1 / code 5；不卸载或清除真机数据。

### Task 1: 隔离查询失败并交付

**Files:**
- Modify: `core/src/main/kotlin/cn/traintrip/core/SearchEngine.kt`
- Modify: `core/src/main/kotlin/cn/traintrip/core/Models.kt`
- Modify: `core/src/main/kotlin/cn/traintrip/core/TicketParser.kt`
- Modify: `core/src/main/kotlin/cn/traintrip/core/OfficialTicketSource.kt`
- Modify: `core/src/test/kotlin/cn/traintrip/core/CoreTest.kt`
- Create: `core/src/test/kotlin/cn/traintrip/core/SearchEngineTest.kt`
- Create: `app/src/androidTest/kotlin/cn/traintrip/app/SearchFailureTest.kt`
- Modify: `app/build.gradle.kts`, `README.md`
- Create: `docs/verification/2026-09-19-query-failure-isolation.md`

**Interfaces:**
- Consumes: `TicketSource.query(QueryUnit): QueryResult`、`search(plan, existing): Flow<SearchProgress>`。
- Produces: 每项成功、失败或未起售结果独立写入 outcomes；保留现有 API，Failure 改为只携带 message。

- [ ] **Step 1: 写并运行失败测试。**
  - 三项计划第一项经真实 TicketParser 产生未知席别错误，断言三项全部调用，failureCount=1、successCount=2、remainingCount=0、stopped=false。
  - 四项普通 Failure 后一项 Success，断言最后一项仍被调用。
  - query 抛出 IOException 后下一项 Success，断言异常保存为 Failure。
  - 第二项挂起后取消，断言只保留第一项结果且第三项未被调用。
  - 使用 runTest 虚拟时钟，失败前后两次调用时间为 1100、2200 毫秒。
  - 原失败重试测试改为首轮全部尝试完、第二轮只重试失败项，调用总数为 4。
  - Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test --offline`；预期新继续查询断言失败。

- [ ] **Step 2: 最小实现。**

```kotlin
val result = try {
    source.query(unit)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    QueryResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "查询失败，请稍后重试")
}
currentCoroutineContext().ensureActive()
results[unit.key] = result
emit(SearchProgress(plan, results.toMap(), running = true))
```

删除 `consecutiveFailures`、停止条件及提前 return；循环后保留 `emit(SearchProgress(plan,results.toMap()))`。将 Failure 定义改为 `data class Failure(val message: String): QueryResult`，同步删掉构造时的 true 参数。核心回归应全部通过。

- [ ] **Step 3: UI 与取消验证。** 使用注入的离线 TicketSource 在模拟器上完成三项查询；第一项失败、后两项成功。断言待查为 0、显示两城和重试按钮，错误弹窗包含原错误；点击重试后只重新请求失败项并恢复完整结果。运行新 SearchFailureTest。
- [ ] **Step 4: 构建与安装。** bump 版本；执行 `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`。先备份真机 APK 与 travel-filters.xml；核对签名后 adb install -r，回读版本、安装包哈希、启动状态与偏好哈希。
- [ ] **Step 5: 记录与提交。** 保存测试/构建/安装摘要、更新 README、检查 diff，提交到 codex/query-failure-isolation。通过 punk-12 发送简短完成通知并确认发送结果。
