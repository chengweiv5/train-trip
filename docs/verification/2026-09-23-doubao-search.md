# 豆包搜索替换验证

默认网页和图片搜索已切换为豆包 Custom API，DeepSeek 继续整理原文。设置页、密钥校验、加密存储、调用进度和说明均已更新。图片首选豆包，缺图后备仍为携程。主代码不再包含 Tavily 适配器、端点或配置读取路径。

开发基于 `origin/main` 的 v0.8.0 提交 `d859747`，已保留 `889ce10` 的景点/美食单次及累计 10/10 限制。功能提交 `3c22de8` 已使用 `git push origin HEAD:main` 快进推送，并回读确认 `origin/main` 与该提交一致。本轮不发布 APK，不安装到手机；公开 v0.8.0 尚不包含此替换。

## 实际链路

2026-09-23，使用用户指定的两份密钥经 stdin 执行应用使用的 Kotlin 适配器和生成器：

| 项目 | 结果 |
|---|---|
| 城市 | 承德 |
| 豆包网页搜索 | 2 次，HTTP 200；8 篇通过来源/正文校验 |
| DeepSeek 整理 | `deepseek-flash`，HTTP 200；10 个景点、7 种美食 |
| 原文证据 | 所有保留条目的 evidence 均能在对应来源正文中定位，未放宽连续引用校验 |
| 豆包图片搜索 | 17 次，HTTP 200；5 张通过归属和来源筛选，覆盖 3 个景点 |
| 抽样下载 | 3 张均 HTTP 200，可解码；900×1200、3872×2592、1000×563 |

下载的图片分别为园林湖景、长城和“塞罕坝国家森林公园”入口。此样本未取得可信美食图片，不能据此声称所有景点/美食都有图；图片缺失不会使正文保存失败。

首次搜索命中景区等级名录，模型使用无句号的名称/地址表格作为 quote，导致无有效景点。已在检索阶段排除不含完整原文句子的文档，并明确要求模型先找完整证据、再选择条目；随后真实链路通过。两次失败整理加最终成功整理合计调用 DeepSeek 3 次；网页搜索合计 4 次；图片搜索 17 次、下载 3 次。名录过滤为通用规则，未针对承德硬编码。

接口依据：[豆包搜索 Custom API](https://docs.volcengine.com/docs/Networkedsearch/Networkedsearch-2?lang=zh)。接口探针位于 `/tmp/train-trip-doubao-search-probe.nbIqZh/`，本次证据位于 `.verification-private/doubao-search/`，包括 `live-final/summary.json`、`material.json`、`guide.json` 及图片。真实密钥未进入这些产物、源码或 APK。

## 自动验证

- `:core:test`：125 项通过，0 失败、0 跳过；覆盖两次网页查询、正文与名录筛选、错误码、跨域重定向、取消、图片归属/CDN、单次及累计条目限制。
- Debug 与 Android test APK 构建通过；`:app:lintDebug` 通过。
- 临时只读模拟器 `emulator-5590`：SettingsMenuTest、DeepSeekDestinationTest、GuideRefreshRetentionTest、PhotoRecoveryTest 共 35 个独立用例，34 个首轮通过；旧图片样本缺少条目归属，将其更新为带明确 subject 的豆包 CDN 样本后，该用例定向复测通过。此结果为合并验证记录，并非 35 项同轮全绿。
- Android 密钥隔离回归确认：旧 Tavily 加密记录不迁移、不覆盖、不作为豆包密钥读取；豆包密钥独立加密，DeepSeek 设置与缓存保留。保存设置不触发搜索。
- `git diff --check` 通过；生产路径无 Tavily 搜索端点。未触碰 Anthropic 服务。

## 使用与回滚

安装包含本次代码的版本后，在“设置 → 搜索引擎”填写豆包搜索 Custom 版 Key。既有城市可继续离线查看；只有手动整理或更新才使用搜索/模型额度，缺图条目另发图片查询。

修改前备份 `.verification-private/doubao-search/before.tar`。回滚优先反向提交本次豆包替换功能提交，保留独立的 `889ce10` 数量限制提交；旧 Tavily 存储与缓存格式未被删除或改写。不要直接整仓覆盖备份，以免覆盖其它任务后续改动。
