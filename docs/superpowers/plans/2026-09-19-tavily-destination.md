# Tavily 目的地检索实施计划

> 在当前会话逐项执行并验证。当前环境没有 executing-plans 技能，沿用本计划检查点；spec reviewer 由 brainstorming 要求独立执行。

**Goal:** 新城市通过 Tavily 获取国内资料，交给 DeepSeek 整理并保存到手机。

**Architecture:** TavilyGuideSource 输出可追溯文档，SearchGuideDecoder 检查模型选择的原文引用，现有 Repository 保持缓存/取消/失败规则。两把密钥在 Android Keystore 下独立保存。

**Tech Stack:** Kotlin、OkHttp、Gson、Coroutines、Compose、JUnit/MockWebServer。

## Global Constraints

- 保留 v0.4.0 / versionCode 8，功能分支本地提交，个人素材不公开推送。
- 无自建服务器，不新增依赖，不把密钥存入仓库、APK、日志。
- 真机只覆盖安装与界面操作，测试使用独立模拟器。
- 保留旧 DeepSeek alias/prefs、缓存和 attempt，资料不足允许空美食/路线/照片。

## Task 1: 可追溯搜索资料和解码

**Files:** core/src/main/kotlin/cn/traintrip/core/{CtripGuideSource,TavilyGuideSource,SearchGuideDecoder,DeepSeekGuideGenerator,GuideNetwork,DestinationGuide}.kt；core/src/test/kotlin/cn/traintrip/core/TavilyGuideTest.kt。

**Interfaces:** `SourceDocument(id,title,url,content,kind,images)`、`SourceImage(url,description)`；`GuideMaterial.documents: List<SourceDocument> = emptyList()`；`TavilyGuideSource(key: () -> String?) : GuideMaterialSource`；`SearchGuideDecoder.decode(content,material): DestinationGuide`。

- [x] 加入请求/域名/城市/引用伪造测试，断言无景点时失败、两个顺序 basic 请求、Auth 只在 header、模型不能引入未知条目。
- [x] 源请求最大 2 MiB，最多 6 结果/次、10 文档/城、2400 字/篇；HTTP 非 2xx 映射固定中文消息。
- [x] quote 要求去除空白后精确属于正文且包含 name；景点 20–260 字、美食 10–220 字。模型 name、sourceId、quote 不一致时丢弃该条目，没有有效景点则拒收；位置未匹配原文时使用通用说明。
- [x] 景点/美食新增可选 sourceUrl/evidence，旧缓存兼容。照片只来自该条目原始文档，说明包含景点名，网络和重定向受批准域名限制。
- [x] `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test --offline` 全部通过。

## Task 2: 两项安全配置与 UI

**Files:** app/src/main/kotlin/cn/traintrip/app/{DeepSeekSettings,DestinationViewModel}.kt、ui/{DeepSeekSettingsDialog,TrainTripApp,DestinationGuideScreen}.kt；app/src/androidTest/kotlin/cn/traintrip/app/DeepSeekDestinationTest.kt。

**Interfaces:** `TavilySettings: GuideCredentials`；state 增加 `tavilyConfigured`，`configured` 保持 DeepSeek 状态；`saveKeys(deepSeek,tavily,complete)`、`removeTavilyKey(complete)`，旧 `saveKey` 兼容。

- [x] 双输入先校验再存，各自 KeyStore alias，空白保留；保存/移除取消旧请求并回读状态。
- [x] ViewModel 缺任一 key 直接显示设置入口，不产生搜索或 attempt；默认 source 使用 Tavily。
- [x] 更新弹窗用途/计费说明，独立移除，配置前入口同时提及两家服务。
- [x] 现有 8 个 Android 回归注入 Tavily 假凭据；新增独立删除、缺 Tavily 不发请求、旧配置保留测试。
- [x] 构建 debug APK、AndroidTest APK、lint，通过后在独立模拟器运行此测试类和目的地相关 UI 测试。

## Task 3: 真实验证与本地交付

**Files:** core/src/test/kotlin/cn/traintrip/core/LiveTavilySmoke.kt；docs/verification/2026-09-19-tavily-destination.md。

- [x] 手动 smoke 仅从 stdin 收 key，记录非敏感用量/来源/结果，可用已抓取资料减少重复搜索。
- [ ] 备份已装 APK，覆盖安装到 FMR0224725012307，经遮罩设置输入 Tavily Key，明确重试泰安。
- [ ] 真机验证城市内容和来源、缓存文件、重新打开不改缓存时间；原有筛选条件和 DeepSeek 配置保留。
- [x] 扫描变更与 APK 无真实密钥，记录测试/图片缺口/回滚并本地提交。
- [ ] punk-12 通知及回读：自动审批拒绝，待用户明确批准。
