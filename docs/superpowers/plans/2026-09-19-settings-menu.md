# 设置菜单实施计划

在当前会话按任务执行。规格已独立审查通过；保持本地个人版本范围。

**Goal:** 通过独立设置菜单管理 DeepSeek 模型/API Key 与 Tavily Key。
**Architecture:** 业务导航与设置导航分开，SettingsScreen 管理无持久化的草稿，DestinationViewModel 管理保存状态，模型配置供给生成器。
**Tech Stack:** Kotlin / Compose / Keystore / OkHttp / JUnit。

## Global Constraints

版本 0.4.0/8；固定官方 API；旧密钥存储兼容；保存设置不自动收费；缓存不失效；本地提交不公开推送。

### 1. 模型配置与保存行为

- [x] core DeepSeekGuideGenerator 增加 `model: () -> String`，生成时快照并用于请求和结果 model 字段，默认常量不变；SearchGuideDecoder 接收模型参数。
- [x] app DeepSeekSettings 新增 `GuideModelPreference { read():String; save(value:String) }` 及 Android 实现，验证 ID 后写入原 prefs 的独立字段。
- [x] DestinationViewModel 注入配置，state 增加 modelName/settingsBusy/settingsMessage；新增 saveModelSettings / saveSearchSettings；保存及删除防重复、回读，去除保存后 retry。
- [x] 核心测试断言自定义模型用于请求和缓存，原测试兼容；Android 回归保存不触发生成、取消旧请求、另一项不受影响。

### 2. 导航与可编辑设计

- [x] 四页设计已原生保存到 .pen；MCP 回读、布局检查、预览及 OCR 复核通过。
- [x] SettingsScreen 提供 MENU/MODEL/SEARCH 三页；Key 草稿 remember，模型输入/状态/独立删除；IME padding+滚动；敏感页 FLAG_SECURE 恢复。
- [x] TrainTripApp 覆盖原业务页面展示设置，进入取消生成，返回保持 saveable 原页面状态；FiltersScreen 入口移至右上角，移除底部入口和旧双 Key 弹窗。
- [x] Android 测试：菜单导航、分项保存、取消草稿、系统返回、短屏大字、忙状态、原筛选保留。

### 3. 验收和交付

- [x] core:test / assembleDebug / assembleDebugAndroidTest / lintDebug 通过。
- [x] 专用模拟器 Settings/DeepSeek/目的地相关用例通过；视觉检查非敏感菜单，敏感页通过语义树与 FLAG_SECURE 测试验证。
- [x] Mate 60 Pro 已保留数据覆盖安装；菜单、两项配置、泰安真实生成及重启缓存恢复通过。
- [x] 扫描代码/产物无真实密钥，更新验证文档、备份 APK、本地提交。用户明确批准后，punk-12 完成通知已发送并回读确认。
