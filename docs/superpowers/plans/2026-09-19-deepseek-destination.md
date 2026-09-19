# DeepSeek 目的地内容实施计划

> **For agentic workers:** 用户已选择 DeepSeek 并要求接入，在当前任务按以下顺序执行，不重复询问执行方式。

**Goal:** 手机直接取携程公开资料，由 DeepSeek V4.1 Flash 整理新城市，保留四分页并缓存本地。

**Architecture:** 采集、生成、缓存分别独立；Android 负责私有存储、Keystore 与界面状态。DeepSeek 请求只走官方固定域名，网络采集没有模型密钥。

**Tech Stack:** Kotlin、OkHttp、Gson、Compose、Android Keystore、JUnit、instrumentation。

## Global Constraints

- 默认 API 模型名 `deepseek-flash`，保持 0.4.0 / code 8；无自建服务器。
- 不将 API Key 写入文件、源码、APK、日志或测试参数；个人真机通过掩码输入配置。
- 基线 46205e3，功能分支；本次仅本地提交与安装，不公开推送个人配图历史。
- 只把实际下载内容提交给 DeepSeek；不信任模型返回的来源／图 URL；失败保留缓存，不自动重试。

### Task 1: 国内采集与单城数据模型

**Files:** core/src/main/kotlin/cn/traintrip/core/{DestinationGuide,GuideNetwork,CtripGuideSource}.kt；core/src/test/kotlin/cn/traintrip/core/CtripGuideSourceTest.kt。

**Interfaces:**
```kotlin
data class GuideMaterial(val cityId: String, val name: String, val province: String,
    val places: List<SourcePlace>, val foods: List<SourceFood>, val sources: List<GuideSource>)
interface GuideMaterialSource { suspend fun fetch(city: City, stage: (String) -> Unit): GuideMaterial }
interface GuideGenerator { suspend fun generate(material: GuideMaterial, apiKey: String): DestinationGuide }
```

- [x] 写解析测试：目录按汉字名匹配、苏州／宿州不混同；城市省份不符失败；过滤境外和结构缺失；只保留实际介绍字段。
- [x] 采集解析 HTML 中 `__NEXT_DATA__` 和城市链接，不引入页面执行。最多 5 个景点和 2 个餐馆，响应限制 5MiB，超时、取消、重定向域名受控。
- [x] 模型增加 nullable photo、generatedAt/model；独立运行时单城校验允许空栏目，静态五城完整验证保留。图片以摘要命名，禁止路径穿越。
- [x] 执行 `./gradlew :core:test --offline`，验证采集模型和既有静态用例。

### Task 2: DeepSeek 与本地缓存流程

**Files:** core/src/main/kotlin/cn/traintrip/core/{DeepSeekGuideGenerator,GuideRepository}.kt；core/src/test/kotlin/cn/traintrip/core/DeepSeekGuideTest.kt。

**Interfaces:**
```kotlin
interface GuideStore {
    fun read(cityId: String): DestinationGuide?
    fun attempted(cityId: String): Boolean
    fun markAttempted(cityId: String)
    fun save(guide: DestinationGuide)
}
class GuideRepository(val source: GuideMaterialSource, val generator: GuideGenerator, val store: GuideStore)
```

- [x] 模型请求 JSON 输出、关闭思考、限定输出；从已采集景点／美食 ID 组装最终实体。来源和配图由程序赋值。
- [x] 拒绝截断响应、无效 JSON、未知 ID、空必需字段；401/402/429/5xx 映射简短错误，绝不直接回显响应体。
- [x] 缓存优先于初始包；开始前写尝试标记，失败和取消后仅显式重试。模型／采集失败不覆盖已有内容。
- [x] 用内存 store 和假生成器测试缓存命中无调用、失败保留及首次尝试限制，用 MockWebServer 验证 Authorization 只在模型请求和默认模型正确。

### Task 3: Android 存储、配置和四分页

**Files:** app/src/main/kotlin/cn/traintrip/app/{DeepSeekSettings,AndroidGuideStore,DestinationViewModel}.kt；app/src/main/kotlin/cn/traintrip/app/ui/{DeepSeekSettingsDialog,TrainTripApp,FiltersScreen,ResultsScreen,DestinationGuideScreen,DestinationGuideContent}.kt。

- [x] 私有文件原子保存 JSON；图片下载失败允许正文成功，解码并缩放后写入缓存。Keystore AES/GCM 加密保存密钥，解密异常要求重新配置。
- [x] 独立 ViewModel 状态包含 cityId、guide、loading、stage、error、configured；请求代号与取消防止切城串数据；ON_STOP 取消。
- [x] 所有城市开放了解目的地；保留直接车次入口。缺内容展示阶段／错误／设置与重试；缓存内容展示更新时间和手动更新。
- [x] 四分页对空美食／玩法／照片安全降级；来源弹窗展示实际来源与 AI 整理信息。
- [x] 设置掩码输入仅使用 remember，保存成功清空输入；空输入保留旧密钥，显式移除才删除。
- [x] 添加 UI 用例验证配置、缺图和空玩法、获取失败、缓存复用及返回；继承四分页与大字体回归。

### Task 4: 验证和个人真机交付

**Files:** docs/verification/2026-09-19-deepseek-destination.md；.verification-private/v0.4.0/deepseek/。

- [x] `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ANDROID_HOME=/Users/bytedance/Library/Android/sdk ./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`。
- [x] 隔离模拟器完成相关测试及截图目视；真实 API 用已取得的公开页面验证，保留模型、来源及生成结果，不记录密钥。未保存精确 token 用量，不推算费用。
- [ ] 真机已完成备份、安装、密钥配置、设置与 APK 哈希回读；继续验证泰安时携程要求网页验证。重启后无自动重试、尝试记录不变、设置保留均通过；完整生成及成功缓存复用受来源验证页阻塞。
- [x] 扫描变更与 APK 已通过，验收与回滚说明已写入；本地交付记录与 punk-12 通知已完成。
