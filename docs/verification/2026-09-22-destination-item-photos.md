# 目的地条目相册与图片上限验收

2026-09-22，用户确认多图相册并指定每个景点/美食最多 3 张、每城市合计最多 100 张。基于功能分支 `codex/destination-item-photos-design` 的 `6102a6a` 实现；开发前已刷新远端并检查，无待合并 PR，`origin/main` 为 `8817acd`。

## 最终行为

- 图片位于对应景点或美食的名称下方，不再展示城市汇总图库。无图保留文字；单图无页码；2–3 张横滑并显示页码，点击打开该条目大图相册，关闭后保留当前照片。来源跟随当前图。
- 归属由来源代码赋值；同城市、类型、名称或明确括号别名唯一匹配才采用。旧图仅按完整城市/条目说明匹配，泛城市封面和失效条目的图不展示。
- 统一限制每条目 3 张、城市 100 张。去重后按正文顺序轮流分配。已有可用旧图优先保留，失败下载不占名额。现有正文仍最多 5 景点和 6 美食，因此正常介绍当前最多 33 张；100 为城市总量保护。
- 仅保留全量更新入口。先保存正文和可用旧图，再为零图条目补图；已有 1 或 2 张也不补满。文件丢失/损坏按缺图处理；新图片逐张保存，取消和失败保留已提交内容。
- 主源候选先下载；条目仍无图才调用备用搜索。每条目每次更新最多一次备用查询。食品检索排除明确标为门店、菜单或环境的图片。

## 验证

| 项目 | 结果与证据 |
| --- | --- |
| 核心测试 | 105 项通过，0 失败/错误/跳过；`core/build/test-results/test/TEST-*.xml` |
| 数量与匹配边界 | 4→3、34 个条目的 136 个候选→100、两条目各 3 张合计 6、重复 URL/资源去重、正文顺序、旧图唯一映射、错城市/歧义拒绝 |
| 主构建 | `:core:test :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain` 成功；私有日志 `build-order-final.log` |
| 最后提示修正 | 完成提示按实际缺图条目判断；Debug/Release/lint 再次通过，`build-message-final.log` |
| lint | 0 错误、16 警告；`app/build/reports/lint-results-debug.xml` |
| Android 回归 | `PhotoRecoveryTest`、`V061Test`、`OfflineManagementTest` 共 36 项通过，`ui.log` |
| 最终重点复验 | `ItemGalleryTest` + `PhotoRecoveryTest` 共 20 项通过，`ui-order-final.log`；包括三图页码、来源切换、大图关闭页码同步、横滑不串外层分页、单图、320dp/1.3 字体、旧缓存、失败/取消与损坏文件 |
| 设计稿 | 原生另存 `design/train-trip-item-photos-v2.pen` 并重新加载；149 个可编辑图层，0 裁切；最终总览及两页完成视觉复核，OCR 见 `design-ocr.txt` |
| Diff | `git diff --check` 通过；已自查来源、存储、更新和界面变更 |

Android 验证仅使用本任务的 `emulator-5582`（`trail_map_api36`，只读、无窗口、无快照）。测试内容为隔离夹具，部分离线样图只用于手势验证，不作为景点/食品真实归属的截图。该模拟器测试后关闭。没有消耗真实模型或 Tavily 查询额度，本轮未实测所有线上照片来源的实时可用性；找不到可核对图片时继续展示文字。

测试日志均位于 `.verification-private/item-photos-implementation/`。设计样图采用已核对的保定直隶总督署与保定式驴肉火烧，来源和署名见 [设计说明](../../design/train-trip-item-photos.md)。静态设计中 `1/3` 说明交互，不代表现场取得三张照片。

## 交付与回滚

功能提交 `65e94bf` 完成本地功能和测试，另行提交设计稿与验收说明；本轮没有推送、发布、修改版本号或安装到手机。提交前备份 `.verification-private/item-photos-implementation/before.tar`；如需撤回，可对本轮功能提交执行 `git revert`，设计文件可从备份恢复。此前安装的 `6102a6a` 版本未受影响。
