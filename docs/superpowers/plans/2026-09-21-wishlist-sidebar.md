# 想去省份双栏 Implementation Plan

**Goal:** 实现用户已确认的左省份、右收藏城市方案。

**Architecture:** `WishlistScreen` 继续接收现有不可变状态与回调，利用 `wishlistGroups` 获取分组。独立 `WishlistBrowserState` 保存省份选择、回退位置及每省 LazyListState；借助现有 `SaveableStateProvider` 恢复离页状态。目的地省份导航行对同 UI 包开放，避免两处样式分叉。

**Tech Stack:** Kotlin、Jetpack Compose、JUnit、离线 Android instrumentation。

## Global Constraints

- 沿用 B4 配色、已有收藏身份和时间戳；版本 0.6.1 / 11，无数据迁移。
- 宽度 <360dp 或字体 >1.15 时采用下拉；操作区域至少 48dp。
- 不调用真实票源或付费服务；使用独立模拟器，不操作真机。

## Task 1: 浏览状态与双栏展示

Files: `app/src/main/kotlin/cn/traintrip/app/ui/WishlistScreen.kt`、新增 `WishlistBrowserState.kt`、`DestinationSelector.kt`。

- [x] 在 `WishlistSidebarTest.kt` 添加两省独立切换、最近收藏在前、删除当前省最后城市回退、撤销恢复顺序的交互测试。
- [x] 用 `rememberSaveable(saver=WishlistBrowserState.Saver)` 保存 `provinceName`、省份索引和每省 `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`；选择缺失时使用原位置的下一省或最后一省。
- [x] 把 `DestinationNavigationRow` 可见性从 private 改为 internal，并在想去省份栏使用同组件。
- [x] 把原多省连续列表改为当前省列表；卡片使用 12dp 内边距、紧凑标题和 48dp 操作，日期格式 `MM-dd 收藏` 或 `yyyy-MM-dd 收藏`。
- [x] loading/error/empty 保持现有区分。布局依据 `BoxWithConstraints` 选择左栏或下拉，且大字体按钮必要时换行。

## Task 2: 恢复、回归与交付

Files: `WishlistSidebarTest.kt`、`WishlistFlowTest.kt`、`WishlistPersistenceTest.kt`、`B4UiTest.kt`，Pencil 与设计说明，README 截图来源及想去截图。

- [x] 用 `StateRestorationTester` 验证两省各自滚动位置和选中省份恢复；现有流程测试显式切换到包含目标城市的省份。
- [x] 验证 320dp/1.3 字体，检查按钮触控尺寸与文字无截断；检查未知/无站城市禁用查票及仍可浏览介绍。
- [x] 执行 `./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease`，安装测试包到本任务专用模拟器运行上述场景与 `DestinationWishlistTest`。
- [x] 对普通双栏与紧凑下拉截图做视觉/OCR检查，更新 README 想去截图并同步已确认设计状态；保留截图为演示数据的标记。
- [x] 自审改动、`git diff --check`，本地提交代码/测试/设计；记录验证证据与回滚方式。是否安装真机、推送或发布按后续指令处理。

在当前会话逐项实施；未安装额外工作流技能。所有状态和异常都有明确处理，无需再次确认设计。

## 实施中发现并处理

收藏持久化回归中，第二个 Activity 关闭前原页面仍处于 STARTED，返回只触发 ON_RESUME，原 ON_START 重读会遗漏。已将重读触发点改为 ON_RESUME，并让原回归用例等待第二个 Activity 真正进入前台后再写入。没有修改收藏数据格式或存储。

## 验证结果（2026-09-22）

- 核心测试 86 项通过；Debug、测试包、Release 构建和 Release lint 通过（0 error，19 个现有 warning）。
- 独立模拟器 `emulator-5586` 的离线回归 27 项通过，涵盖双栏 6 项、收藏流程 8 项、持久化 4 项、B4 UI 4 项及目的地想去分组 5 项。
- 390dp 标准字体及 320dp / 1.3 倍字体实测；文本排版边界、48dp 操作区域和人工视觉检查通过。三张最终截图 OCR 共 103 个文本区块；滚动列表超出视窗的后续卡片按正常滚动显示。
- Pencil 12A/12B 省份顺序与应用目录一致；原生保存后回读确认，201 个节点无裁切问题。README 想去截图替换为河北状态，来源标记为离线演示。
- 测试日志与截图：`.verification-private/wishlist-sidebar/implementation/`，不提交设备数据或运行日志。
- 回滚：在功能分支对本次提交执行 `git revert <提交号>`；本次未更改收藏存储格式，回滚无需数据迁移。
