# 车次页日期单行与卡片高度验证

本记录保留初次布局验收时的版本、复制入口和 APK 信息；随后主线整合、文案清理及最终真机安装以 [UI 清理验收](2026-09-19-ui-copy-cleanup.md) 为准。

三个出发日期现在与“全部日期”在同一行，日期较多或放大字体时可以横向滑动。去掉日期文案中的多余空格，日期间距 6dp、水平内边距 8dp，保留完整月日及原字号。

卡片内边距由 18dp 改为横向 12dp、纵向 10dp，行间距从 12dp 减为 6dp；列表间距由 16dp 减为 10dp。出发日期移至顶部，查询时间与“更多席别”合并一行，大字体时查询日期与时间分两行。时刻保持 26sp，席别仍至少 48dp 高；选中图标、站名、耗时和跨日到达信息保留。

## 实测

以 Mate 60 Pro 相同的 1260×2720、520dpi，三个日期和两种席别数据在独立模拟器比较：

| 项目 | 改前 | 改后 |
| --- | --- | --- |
| 全部日期 + 三日期 | 第三个日期在第二行 | 同一行完整显示，右侧余约 34dp |
| 两席别卡片高度 | 264.3dp | 201.5dp，减少 23.7% |
| 卡片与下一张的间距 | 16dp | 10dp |

8 项布局和既有详情交互回归通过；最终查询时间分行调整后，2 项布局再次通过。覆盖日期筛选与“全部日期”恢复、31 日期末尾滑动选择、清除被筛选隐藏的选择、刷新/部分失败/重试、复制与打开 App、状态恢复、320dp / 1.3 倍字体、文字尺寸和 48dp 席别点击高度。Debug APK、测试 APK 和 lint 通过；lint 0 错误、12 条既有建议。

[原三日期页面](detail-compact-runtime/before-three-dates.jpg) · [改后三日期页面](detail-compact-runtime/three-dates.jpg) · [大字体卡片底部](detail-compact-runtime/large-card-footer.jpg) · [长列表末尾日期](detail-compact-runtime/large-last-date.jpg)。截图与本机 OCR 已复核。[结构化记录](detail-compact-runtime/summary.json)。

## 真机与版本

基于真机已安装的 v0.4.0 开发，将其本地实现整合至 origin/main 1385019。复制、主动刷新和打开铁路12306 App 的功能保留，同时包含主线首页时段单行修复。

最终 APK 已覆盖安装到 Mate 60 Pro，版本仍为 0.4.0 / code 8。安装返回 Success，冷启动 Status: ok；安装包回读 SHA-256 与新包一致：`58ca96957b57ad91ff2a20730b74717c0d4d72f080eb5319781ef134708846ea`。安装及启动前后筛选设置逐字节一致。

布局验收使用离线模拟数据；真机本轮验证安装、启动和设置保留，未在用户查询条件下重新请求真实余票。可编辑 `.pen` 没有修改，共享 Pencil 的既有设计任务保持原状。

## 回滚

本轮开发分支为 `codex/detail-compact-dates`。布局源码备份在 `.verification-private/detail-compact/original/`，真机原 APK、设置及回读证据在 `.verification-private/detail-compact/install/`。如需回退已安装版本，可使用同目录 `previous-installed.apk` 进行 `adb install -r`；恢复前确认没有后续版本覆盖。初次验收时尚未提交，后续与 UI 文案清理一起交付。
