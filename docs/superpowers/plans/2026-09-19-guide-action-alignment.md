# 目的地更新入口左对齐实施计划

在当前会话直接执行已批准的单项布局修复。

**Goal:** 「更新目的地介绍」与整理日期、资料来源保持同一左边线。
**Architecture:** 仅调整 `GuideRuntimeStatus` 中现有 TextButton 的尺寸与内容内边距，不引入组件或业务状态。
**Tech Stack:** Kotlin / Jetpack Compose / Gradle / ADB。

## Global Constraints

保留文案、颜色、回调、缓存及加载行为。按钮至少 48dp 高；版本 0.4.0 / 8。功能分支本地提交。真机覆盖安装，不卸载或清数据，验证不触发内容生成。

### 1. 修改、验证与交付

- [x] 检查待合并 PR（无），备份源码，确认规格并完成独立审查。
- [x] 修改 `app/src/main/kotlin/cn/traintrip/app/ui/DestinationGuideScreen.kt` 的 `refresh-guide` 按钮：

```kotlin
TextButton(onRefresh, Modifier.heightIn(min = 48.dp).testTag("refresh-guide"),
    contentPadding = PaddingValues(vertical = 12.dp)) {
    Text(if(guide!=null) "更新目的地介绍" else if(state.error!=null) "重试整理" else "整理目的地介绍")
}
```

- [x] 使用 JDK 21 执行 `./gradlew :app:assembleDebug :app:lintDebug`，检查构建成功及 lint 错误数；此纯布局修改使用实际 UI 测量验证，不新增镜像实现的测试。
- [x] 将 APK 保存为 `artifacts/train-trip-guide-alignment-personal-debug.apk`，使用 `adb -s FMR0224725012307 install -r` 更新真机。
- [x] 真机缓存及两项密文配置校验值保持一致；最终 UI 树与截图改在专用模拟器完成，日期、更新文字、来源文字 x 坐标均为 55px，按钮区域高 48dp。
- [x] 写入验收证据和回滚路径，本地提交；上轮状态通知已回读，最终完成通知合并本次排序修复发送。
