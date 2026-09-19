# 国内目的地图文替换实施计划

> **For agentic workers:** 在当前任务内按步骤执行；用户已授权继续实现，不新增执行选择确认。

**Goal:** 五城正文和照片使用国内来源，完成个人离线版构建与保留设置的真机安装。

**Architecture:** 沿用静态 JSON、APK 图片资源和现有四分页。仅调整内容、来源元数据与弹窗，许可改为可选成对字段。

**Tech Stack:** Kotlin、Gson、Jetpack Compose、JUnit、Android instrumentation、Pillow。

## Global Constraints

- 五城 cityId、分页/导航/车票行为不变，版本 0.4.0 / code 8。
- 不伪造摄影者或开放许可；原图仅缩放，保留画面中已有署名。
- 本轮本地提交与安装，不公开推送个人用照片、截图、设计和 APK。
- 基线 d457523，备份 .verification-private/v0.4.0/domestic-sources/source-before.tar。

### Task 1: 国内资料与模型

**Files:** core/src/main/resources/destination_guides.json；core/src/main/kotlin/cn/traintrip/core/DestinationGuide.kt；app/src/main/assets/destinations/*.jpg；core/src/test/kotlin/cn/traintrip/core/DestinationGuideTest.kt。

**Interfaces:** GuideSource 不变；DestinationPhoto(assetName, description, credit, sourceUrl, license: String? = null, licenseUrl: String? = null)。

- [ ] 根据实际读取的网页重新提炼五城景点、美食，保存标题、URL、核对日期与照片摘要清单。
- [ ] 原图 EXIF 转正、RGB JPEG、最长边 1200，保留完整原图画面；既有 Image ContentScale.Crop 保持展示规则。
- [ ] 使用 java.net.URI 校验外链：scheme in setOf("http", "https") && !host.isNullOrBlank() && userInfo == null。拒绝 javascript/file 与无 host 链接。
- [ ] 验证可选许可：两字段同为 null，或名称非空且 URL 有效；其余均使损坏目录降级。
- [ ] 更新既有核心测试，添加无许可成功、成对许可成功、半对许可与非法 URL 失败的行为用例；确认五城图像均可解码且正文来源为国内域名。

### Task 2: 来源弹窗

**Files:** app/src/main/kotlin/cn/traintrip/app/ui/DestinationGuideScreen.kt；app/src/androidTest/kotlin/cn/traintrip/app/DestinationGuideTest.kt。

**Interfaces:** 继续通过 onSource(String) 打开外部原文，无 App 内网络下载。

- [ ] 移除固定正文 CC 入口，说明国内资料整理和玩法建议。
- [ ] 图片展示 credit、个人离线浏览说明及原图页面入口；只有 licenseUrl != null 时显示许可按钮。
- [ ] 调整来源点击测试，验证国内原文/图片出处 URL、无许可时按钮不存在，有许可样例仍正确打开。
- [ ] 构建并在本任务隔离模拟器执行 DestinationGuideTest；保留四分页、返回、320dp/1.3 字体和短屏用例。

### Task 3: 验证与个人交付

**Files:** docs/verification/2026-09-19-domestic-destination-sources.md；artifacts/train-trip-domestic-personal-debug.apk；.verification-private/v0.4.0/domestic-sources/。

- [ ] JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ANDROID_HOME=/Users/bytedance/Library/Android/sdk ./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline。
- [ ] 新配图、来源弹窗、常规与大字体截图结构检查及目视/OCR，验证真实来源不混入旧许可。
- [ ] 真机 FMR0224725012307 先备份 base.apk 与 travel-filters.xml，再 adb install -r；启动后回读 APK SHA256、包版本、筛选设置字节一致。
- [ ] 写验收与回滚记录，本地提交并发送 punk-12 完成通知；不推送公开仓库。
