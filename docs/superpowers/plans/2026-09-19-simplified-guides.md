# 目的地简体资料修复计划

**Goal:** 拦截繁体资源并修复保定旧缓存展示。

**Architecture:** core 中集中维护 SimplifiedGuidePolicy；搜索、模型解析、生成结果、缓存复用四个边界使用同一规则。Android 缓存保留旧文件，新规则尝试标记单独区分，确保至多自动重试一次。

**Tech Stack:** Kotlin、Gson、OpenCC 离线字形数据、JUnit、Android instrumentation。

- [x] 检查主线/PR（无待合并 PR）、rebase，备份源码和保定缓存。
- [x] `SimplifiedGuidePolicy.kt` 定义文本、URL、材料及全部 guide 字段校验；派生离线字表并记录许可证及固定 commit。
- [x] `TavilyGuideSource.kt` 过滤完整标题/正文与图片说明并明确检索简体资料；`DeepSeekGuideGenerator.kt` 和 `SearchGuideDecoder.kt` 加入输入/输出门禁及提示词约束。
- [x] `DestinationGuide.kt`、`GuideRepository.kt` 与 `AndroidGuideStore.kt` 统一校验缓存；旧繁体缓存不可展示，新尝试标记阻止失败重启循环。
- [x] 用保定 BIG5/野三坡样例增加 core 回归，覆盖无版本标识的繁体、标题、混合字形、图片、模型字段及无合格来源；Android 测试旧缓存和一次重试。
- [x] 运行 core 全量、受影响 Android 测试、assemble/lint；专用模拟器回归目的地展示及缓存读取。
- [x] 真机备份、保留数据安装和回读；验收记录、功能分支提交、快进推送及 punk-12 完成通知。

回滚使用反向提交及安装前 APK。旧缓存原始文件不删除，语言不合格时仅拒绝读取。
