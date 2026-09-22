# Guide Refresh Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 全量刷新保留全部旧景点/美食，合并新增，保留对应图片。

**Architecture:** repository 在调用 Android prepare 前合并核心条目；单次模型输出与累计缓存分别校验；Android 继续检查并保留旧图、原子保存。

**Tech Stack:** Kotlin、Gson、JUnit、Android instrumentation。

## Global Constraints

- 用户已选择保留全部旧条目，累计允许超过 5 景点/6 美食；单次生成上限保持 5/6。
- 每条目 3 图、每城市 100 图；已有可用图不重复补。
- 不改布局，不推送、不发布，不消耗真实服务额度。已丢失的历史内容不能声称自动恢复。
- 分支 `codex/preserve-cached-guide-items`，备份 `.verification-private/handan-refresh/before.tar`。

## Task 1: 复现与核心合并

Files: 新 `core/src/main/kotlin/cn/traintrip/core/GuideRefreshPolicy.kt`，新 `core/src/test/kotlin/cn/traintrip/core/GuideRefreshPolicyTest.kt`；修改 `GuideRepository.kt`、`DestinationGuide.kt`。

Interface: `GuideRefreshPolicy.merge(previous: DestinationGuide?, fresh: DestinationGuide): DestinationGuide`；`DestinationGuides.validateCached(guide: DestinationGuide, cityId: String): DestinationGuide`。

- [x] Android `GuideRefreshRetentionTest.fullRefreshKeepsCachedGuangfuWhenNewSearchOmitsIt` 已红：新列表仅博物馆，缓存广府古城丢失；证据 `repro-red.log`。
- [x] 以旧条目列表为起点，唯一名称/括号别名匹配原位更新并保留旧 ID/name；未命中新条目追加并分配唯一 ID；维护 freshId→mergedId 改写新计划。
- [x] 美食同规则合并，来源按 URL 去重，新日期优先。按天数合并计划，无新计划则保留旧计划。
- [x] repository 在 prepare 前调用：`val guide = GuideRefreshPolicy.merge(cached(city.id), generator.generate(material, key))`。
- [x] `validateGenerated` 先检查 `1..5`、`<=6`、`1..10` 再委托 `validateCached`；后者只要求非空景点/来源并保留字段、ID、引用和3/100检查。
- [x] 核心用例：遗漏、超过旧数量上限、ID重用/计划、同名去重/别名、无关名称、跨城拒绝、反复合并不增长。
- [x] Run `./gradlew :core:test --offline --console=plain`，预期全部通过。

## Task 2: 持久化与集成回归

Files: `app/src/main/kotlin/cn/traintrip/app/AndroidGuideStore.kt`、`app/src/androidTest/kotlin/cn/traintrip/app/GuideRefreshRetentionTest.kt`。

- [x] store read/save/refreshPhotos 使用 `validateCached`；读写统一 `MAX_CACHE_BYTES = 16 * 1024 * 1024`，save 序列化后在 atomic 前检查字节数。
- [x] 原始 Android 回归转绿：广府古城及原图保留、博物馆正文更新；追加超过5景点及6美食、ID不重复、计划引用有效、重读/重启仍可用。
- [x] Run `./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`。
- [x] 在本任务 emulator-5582 运行 `GuideRefreshRetentionTest,PhotoRecoveryTest,OfflineManagementTest,ItemGalleryTest`。所有测试需通过，不操作手机数据来复现。
- [x] 自查 diff，记录证据、远端不可用和历史内容恢复边界，提交功能分支；关闭本任务模拟器，更新任务状态，通过 punk-12 通知并回读。

验收结果：核心 114 项、Android 33 项通过；构建和 lint 通过。修复 `2a48abe` 已覆盖安装到 Mate 60 Pro，未清数据；`emulator-5582` 已关闭。完成通知 `om_x100b641d1c7984a8c10a642f73b1636` 已由 punk-12 发送并回读。详见[验收记录](../../verification/2026-09-22-guide-refresh-retention.md)。
