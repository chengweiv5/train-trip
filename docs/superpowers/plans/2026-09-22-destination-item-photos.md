# 目的地条目相册 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 景点与美食独立相册，每条目 3 张、每城市 100 张，只为缺图条目补图。

**Architecture:** 新建核心照片策略统一归属和上限；现有来源赋 subject、store 原子保存、ViewModel 执行先正文后缺图检索，Compose 复用相册到每条卡片。

**Tech Stack:** Kotlin、Gson、Coroutines、Jetpack Compose、JUnit、Android instrumentation。

## Global Constraints

- 用户已确认交互；每景点/美食最多 3 张，每城市总计最多 100 张；不扩大正文 5 景点/6 美食上限。
- 同名唯一归属、URL 去重、旧图优先；已有 1–3 张不补满，只零张才检索。
- 无独立补图按钮，无汇总图库，不新增依赖、版本号、发布或手机安装。
- 开发直接在当前功能分支；本机验证用独立模拟器及夹具。

## Task 1: 统一照片归属和限额

Files: `core/src/main/kotlin/cn/traintrip/core/{DestinationGuide,GuidePhotoPolicy,SimplifiedGuidePolicy}.kt`; `core/src/test/kotlin/cn/traintrip/core/GuidePhotoPolicyTest.kt`。

Interfaces:

```kotlin
data class PhotoSubject(val kind: String, val name: String)
object GuidePhotoPolicy {
    const val PER_ITEM = 3
    const val PER_CITY = 100
    fun subjects(guide: DestinationGuide): List<PhotoSubject>
    fun subject(guide: DestinationGuide, photo: DestinationPhoto): PhotoSubject?
    fun select(guide: DestinationGuide, photos: List<DestinationPhoto>): List<DestinationPhoto>
    fun bounded(photos: List<DestinationPhoto>, perItem: Int = PER_ITEM, perCity: Int = PER_CITY): List<DestinationPhoto>
    fun missing(guide: DestinationGuide): List<PhotoSubject>
    fun forItem(guide: DestinationGuide, kind: String, name: String): List<DestinationPhoto>
}
```

- [ ] 增加限额回归：34 个 subject 每个 4 图输入，断言 bounded 结果 100，任意 subject 至多 3；只两条目各 3 时返回 6。
- [ ] subject 添加到 DestinationPhoto 尾部，null 兼容老 Gson；匹配当前城市完整说明、同类型精确名称/括号别名，唯一匹配才采用。
- [ ] bounded 轮询每条目的第 1/2/3 张去重；select 先确定归属再 bounded。validateGenerated 允许 100 总量并对解析成功的每条目要求最多 3；未知旧图可读取但不显示、不保留进新正文。
- [ ] `:core:test --tests '*GuidePhotoPolicyTest'` 验证边界、旧图、同名歧义、错误城市。

## Task 2: 来源与下载更新

Files: core 的 `GuidePhotoSource.kt`, `CtripPhotoSource.kt`, `TavilyGuideSource.kt`, `SearchGuideDecoder.kt`, `DeepSeekGuideGenerator.kt`; app 的 `AndroidGuideStore.kt`, `DestinationViewModel.kt`。

Interfaces: 继续使用 `GuidePhotoSource.fetch/fetchFallback`, `PhotoCandidates`; Android store 增加 `usablePhotos(guide)` 和基于 GuidePhotoPolicy 的保留/下载流程。

- [ ] 来源针对 missing subject；Ctrip 仅匹配景点，去掉城市封面；Tavily 同时支持 food，图片说明核对城市和完整条目，标记 subject。
- [ ] FallbackPhotoSource 先返回 primary 并设置 fallbackAvailable；ViewModel 在 primary/initial 下载完后仅对仍缺图条目做一次 fallback。
- [ ] store 先筛选可解码旧图并映射新条目，再保存正文；refreshPhotos 只接收本批开始时零图 subject，轮询候选，下载前检查3/100，成功后立即保存、回调和清理。
- [ ] prepareUpdate 的首次生成也使用统一归属上限；离线统计及共享资源清理继续依赖完整 gallery 引用。
- [ ] 修订旧5张测试，增加新条目有图/无图混合、食品来源、损坏文件和失败备用场景。

## Task 3: 条目相册界面及设计同步

Files: `app/src/main/kotlin/cn/traintrip/app/ui/{DestinationGallery,DestinationGuideContent,DestinationGuideScreen}.kt`; `design/train-trip-item-photos.pen`。

- [ ] 去掉外层 gallery item，在每条 GuideContentCard 标题下调用 `DestinationGallery(GuidePhotoPolicy.forItem(...), onSource)`，以城市/类型/名称作为状态 key。
- [ ] 多张右上角页码、单张隐藏页码，内层滑动消耗横向手势；大图来源和页码随图切换，关闭回写当前小图页。
- [ ] 图加载失败时移除图并保持文字；大图/来源的可访问标签跟当前图一致。
- [ ] 设计稿加入 1/3 及 3/100 说明并原生保存，回读与 OCR。
- [ ] Android 测试：条目不同相册、切换来源、点击大图、关图页码、单图、无图、320dp/1.3 字体。

## Task 4: 验证和交付

- [ ] `./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`。
- [ ] 只读无窗口模拟器运行 PhotoRecoveryTest / 新相册测试 / 相关 V061Test 与 OfflineManagementTest；新覆盖100边界在core策略测试中验证。
- [ ] 自查最终 diff，记录真实结果、已知网站限制造成的无图可能性；提交功能分支，更新任务状态，punk-12 通知并回读。不推送。
