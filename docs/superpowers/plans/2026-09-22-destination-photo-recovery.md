# 目的地独立补图 Implementation Plan

> **For agentic workers:** 按本计划在当前任务内逐项实施和验证；独立规格评审由 brainstorming 要求的子代理完成。

**Goal:** 旧介绍可独立补图，新介绍不再仅依赖文本搜索附带图片。

**Architecture:** 核心层封装 CtripPhotoSource + TavilyGuideSource 的定向图源，Android 原子保存图集，ViewModel 与 Compose 展示进度和手动入口。

**Tech Stack:** Kotlin、OkHttp、Gson、coroutines、Jetpack Compose；不增加依赖。

## Global Constraints

- 基线 `8817acd`；功能分支开发，版本 0.7.0 / code12 不变，不安装用户手机或发布版本。
- 原始来源及下载证据保存在 `.verification-private/photo-recovery/`，不得公开服务密钥或个人缓存。
- 图片最多 5 张，候选最多 12 张，取消必须传播，正文与旧相册按规格保留。

## Task 1: 独立图源

**Files:** 新建 `core/src/main/kotlin/cn/traintrip/core/GuidePhotoSource.kt`、`CtripPhotoSource.kt` 与同名测试；修改 `TavilyGuideSource.kt`。

**Interfaces:** `GuidePhotoSource.fetch(city: City, guide: DestinationGuide, stage: (String)->Unit): PhotoCandidates`；`PhotoCandidates(photos: List<DestinationPhoto>, failed: Boolean)`；`TavilyGuideSource` 实现备用图源。

- [ ] 先构造三城城市页/景点页夹具，断言完整归属图像无 description 也可采用；同名异地、附近景点、错误 ID 被丢弃。
- [ ] 执行 `./gradlew :core:test --tests '*PhotoSourceTest' --offline`，确认新增能力缺失时失败。
- [ ] 实现目录缓存、城市匹配、名称括号别名匹配、景点详情核验、城市封面兜底及来源标注。
- [ ] 实现最多两次 Tavily 备用查询，复用现有解析和图片筛选规则；没有 key 时只读免费公开图源。
- [ ] 同样命令测试通过，并用只读 live smoke 对三个城市下载图片、ImageIO 解码和记录结果。

## Task 2: 图片持久化与状态

**Files:** `AndroidGuideStore.kt`、`DestinationViewModel.kt`；新建 `app/src/androidTest/kotlin/cn/traintrip/app/PhotoRecoveryTest.kt`。

**Interfaces:** `refreshPhotos(guide,candidates,onCommitted): PhotoSaveResult` 仅改变相册；`DestinationViewModel.refreshPhotos()` 为单独操作；状态增加 `photoLoading`、`photoStage`、`photoMessage`。

- [ ] 测试完整正文值相等、全部失败无写、部分成功逐张提交、取消保留首图、共享文件保留。
- [ ] 保存方法将成功新图置前、原图补足，去重上限5，成功保存后清理无引用图片；错误不覆盖原介绍。
- [ ] ViewModel 从现有介绍调用图源，不读 DeepSeek key；使用现有 requestId/contentLock，离开/删除取消，阻止过期结果。
- [ ] 手动生成完成正文后调用同一补图过程；无图给明确状态，错误不破坏正文。
- [ ] Android 测试覆盖无 DeepSeek key、退出城市、重新读取和无图重试；回归既有 V061Test/DeepSeekDestinationTest。

## Task 3: 手动入口与交付

**Files:** `DestinationGuideScreen.kt`、`TrainTripApp.kt`、可编辑设计补充、`docs/verification/2026-09-22-destination-photo-recovery.md`。

- [ ] 相册下方增加48dp文本按钮及紧凑状态：补充图片、只更新图片、取消补图；旧四分页与顶部保留。
- [ ] 验证320dp/1.3字体可见/可点；保存并回读Pencil补充状态，运行截图比对。
- [ ] `./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`。
- [ ] 专用模拟器安装测试包，执行 PhotoRecoveryTest、V061Test、DeepSeekDestinationTest；不操作用户真机。
- [ ] 检查 diff、提交代码，记录基线/验证/回滚，发送 punk-12 完成通知并读回。
