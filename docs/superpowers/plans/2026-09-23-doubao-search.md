# 豆包搜索替换实施计划

**Goal:** 将现有搜索和密钥设置完整切换到豆包，并保持 10/10 缓存上限。

**Architecture:** `DoubaoGuideSource` 实现 `GuideMaterialSource` 和 `GuidePhotoSource`；共用的来源与图片规则提取至 `GuideSearchPolicy`；`DestinationViewModel` 默认连接新适配器，界面仍沿用现有设置页。

**Tech Stack:** Kotlin、OkHttp、Gson、Android Keystore、JUnit、MockWebServer、Compose instrumentation。

## 约束

原文证据不改写；景点/美食各最多10项；每条目图片最多3张；密钥不打包、不输出、不跨域；保留旧缓存与 DeepSeek 设置；不修改已发布 v0.8.0 的版本号。

## 执行步骤

- [x] 备份源码，检查待合并 PR，提交已有 10/10 修改并 rebase 最新 origin/main。
- [ ] 新建 `core/src/main/kotlin/cn/traintrip/core/DoubaoGuideSource.kt` 和 `GuideSearchPolicy.kt`，替换旧适配器；验证 WebResults/Content、ImageResults/Image、ResponseMetadata/Error、可信来源及签名 CDN。
- [ ] 修改 `DeepSeekSettings.kt`、`DestinationViewModel.kt`、`SettingsScreen.kt`、`DestinationGuideScreen.kt`、`TrainTripApp.kt`；新密钥独立存储，保留布局，更新服务名和调用次数说明。
- [ ] 更新 provider 回归与 `LiveDoubaoSmoke`。运行 `./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest`，再在临时只读模拟器运行设置、存储、生成及缓存回归。
- [ ] 使用指定的两份密钥通过 stdin 运行真实城市端到端验证，保存不含凭据的统计、内容与图片下载结果，逐项核对证据。
- [ ] 更新 README 和验证/回滚记录，检查 diff，提交功能分支，发送并回读 punk-12 完成通知。
