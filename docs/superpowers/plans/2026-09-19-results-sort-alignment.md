# 结果页排序文字左对齐实施计划

在当前会话执行用户指定的单项布局调整。

**Goal:** 结果页排序文字贴齐左边线，刷新保持右侧。
**Architecture:** 现有按钮宽度和回调不变，将 Text 内容区域填满并设置 Start 对齐。
**Tech Stack:** Kotlin / Compose / Gradle / ADB。

## Global Constraints

版本 0.4.0 / 8；功能分支本地提交；两种排序文案均左对齐；刷新行为不变；无模型调用；不打断手机上其他应用。

### 1. 修改与验证

- [x] 确认无待合并 PR，备份源码和旧 APK；规格独立审查通过。
- [x] `app/src/main/kotlin/cn/traintrip/app/ui/ResultsScreen.kt` 引入 `androidx.compose.ui.text.style.TextAlign`，将排序 Text 改为：

```kotlin
Text(if(s.citySortByCount) "省内 · 车次数最多 ↓" else "省内 · 车程最短 ↓",
    Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
```

- [x] 使用 JDK 21 执行 `./gradlew :app:assembleDebug :app:lintDebug`，要求构建成功、lint 无错误。
- [x] 专用模拟器安装 APK，实际核对两种排序文字与标题左边界一致、刷新位于右端且按钮至少 48dp；点击排序确认文案切换，截图复核。低影响布局修改不新增镜像实现的测试。
- [x] 保存 APK 为 `artifacts/train-trip-sort-alignment-personal-debug.apk`，保留数据更新真机；核对安装包和配置/缓存哈希，不切换当前前台应用。
- [x] 写验收文档、本地提交；通知及状态回读保存在私有验收记录。旧 APK 位于 `.verification-private/v0.4.0/results-sort-alignment/before.apk`，可覆盖安装回滚。
