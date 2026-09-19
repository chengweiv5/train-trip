# 结果页排序与刷新对称实施计划

在当前会话完成已授权的单项布局修正。

**Goal:** 两端文字距操作行外边界的留白相等，左侧适当缩进。

**Architecture:** 在 ResultsScreen 的现有 Row 内定义共享按钮内边距，应用到两个 TextButton；排序保留 weight 和 Start 对齐。无状态和数据改动。

**Tech Stack:** Kotlin / Compose / Gradle / ADB。

## Global Constraints

版本 0.4.0 / 8；个人版本仅功能分支本地提交；不修改目的地页；无内容生成调用；真机仅保留数据更新，不打断前台应用。

### 1. 对称留白修正与交付

- [x] 检查待合并 PR（无），备份 ResultsScreen.kt、旧 APK 和任务状态。
- [x] 规格独立审查通过并提交设计。
- [x] 修改 `app/src/main/kotlin/cn/traintrip/app/ui/ResultsScreen.kt`：

```kotlin
val actionPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)
TextButton(onSort,Modifier.weight(1f).heightIn(min=48.dp),contentPadding=actionPadding) {
    Text(if(s.citySortByCount) "省内 · 车次数最多 ↓" else "省内 · 车程最短 ↓",
        Modifier.fillMaxWidth(),textAlign=TextAlign.Start)
}
TextButton(onRefresh,Modifier.heightIn(min=48.dp),enabled=!s.loading && progress?.running!=true,contentPadding=actionPadding) { Text("刷新") }
```

- [x] JDK 21 执行 `./gradlew :app:assembleDebug :app:lintDebug`，构建成功且 lint 无错误。低影响布局修正不新增自动化测试。
- [x] 专用模拟器 390dp 和 320dp 下实际点击两种排序状态，读 UI bounds，检查两端留白差最多 1px、文字垂直居中、按钮高度至少 48dp，检查截图与 OCR。
- [x] APK 扫描密钥格式，保存为 `artifacts/train-trip-action-symmetry-personal-debug.apk`；后台覆盖安装 Mate 60 Pro，安装前后核对配置与缓存哈希、安装包哈希，保留前台应用。
- [x] 保存验收记录、本地提交和任务状态，关闭本次启动的模拟器，发送并回读完成通知。

回滚：`.verification-private/v0.4.0/results-action-symmetry/before.apk` 可用 `adb install -r` 保留数据覆盖安装；源码备份为同目录 ResultsScreen.before.kt。
