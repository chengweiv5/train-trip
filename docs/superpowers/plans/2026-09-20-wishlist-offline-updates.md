# 想去、离线内容与更新 Implementation Plan

> For agentic workers: 按任务顺序在本任务内执行并检查。当前环境没有 executing-plans 技能，使用下面的明确检查点；不额外派发实现任务。

**Goal:** 实现用户确认的 A 方案，完成本地收藏、离线管理与 GitHub 正式版更新检查。

**Architecture:** 核心模块放可离线验证的收藏和更新规则；Android 层负责原子存储、任务取消、导航和 Compose 页面。保留现有查询管道及服务密钥存储，不增加服务器。

**Tech Stack:** Kotlin、Compose Material3、StateFlow、AtomicFile、OkHttp、JUnit、Android instrumentation。

## Global Constraints

- 仅功能分支提交；同步使用 rebase；发布推送目标为 origin/main，禁止强推。
- 收藏无网络请求；新介绍只有显式整理才请求；国内简体来源规则保留。
- 清理不删除收藏或密钥；更新正文提交前失败保留旧版本，图片失败为部分成功。
- 单城条件草稿不污染首页，底栏仅两个根页显示；最小触控 48dp。
- 本轮先实现并验证，不自动发布 GitHub Release 或清除真机数据。

## Task 1：收藏与独立查询导航

Files: `core/.../Wishlist.kt`, `app/.../WishlistViewModel.kt`, `app/.../AppViewModel.kt`, `app/.../ui/WishlistScreen.kt`, `TrainTripApp.kt`, `FiltersScreen.kt`, `ResultsScreen.kt`, `DestinationGuideScreen.kt`。

Interfaces:
```kotlin
data class WishCity(val cityId:String,val name:String,val province:String,val addedAt:Long)
interface WishlistStore { fun read():List<WishCity>; fun write(items:List<WishCity>) }
// WishlistRepository: load, add(cities,now), remove(id), restore(record); returns committed lists.
// AppViewModel: selectRoot(Page), openCityQuery(id), leaveCityQuery(), navigate(page), back().
```
- [ ] 添加核心行为测试：添加去重和稳定顺序、移除后恢复原排序、写入失败不改变快照、读取失败不当作空清单。
- [ ] 运行 `:core:test --tests '*WishlistTest'` 验证缺失实现；实现原子存储与 ViewModel，重复执行确认通过。
- [ ] 加入想去列表、多选添加、底栏与星标；单城查询草稿复用表单并固定目的地。
- [ ] Android 测试验证离线收藏、跨入口同步、取消/撤销、重启与查询草稿返回不污染首页。

## Task 2：离线存储与手动介绍

Files: `AndroidGuideStore.kt`, `DestinationViewModel.kt`, `core/.../GuideRepository.kt`, `ui/OfflineContentScreen.kt`, `ui/DestinationGuideScreen.kt`, `SettingsScreen.kt`。

Interfaces:
```kotlin
data class OfflineEntry(val cityId:String,val guide:DestinationGuide?,val bytes:Long,val hasPhoto:Boolean)
// AndroidGuideStore: entries(), delete(cityId), preparePhoto(guide), save(guide).
// DestinationViewModel: refreshOffline(), deleteOffline(cityId), open(city), retry().
```
- [ ] 更新已有首次自动整理测试，断言 open 不调用 source/generator；显式 retry 才生成。
- [ ] 正文先原子提交无旧图片引用，再下载独立版本图片并关联；删除前 cancelAndJoin，防止旧回调写回。
- [ ] 文件扫描包含已知城市残留，失败按实际剩余容量统计；内存列表同步回退内置内容。
- [ ] 增加离线页面、确认框、空态与失败提示；测试旧内容保留、部分图片成功、删除残留与重新进入不下载。

## Task 3：关于与更新

Files: `core/.../AppUpdates.kt`, `app/.../UpdateViewModel.kt`, `ui/AboutScreen.kt`, `SettingsScreen.kt`。

Interfaces:
```kotlin
data class ReleaseVersion(val major:Int,val minor:Int,val patch:Int):Comparable<ReleaseVersion>
data class AppRelease(val version:ReleaseVersion,val url:String,val summary:String,val downloadable:Boolean)
interface UpdateSource { suspend fun latest():AppRelease }
```
- [ ] MockWebServer 测试分页、语义比较、草稿/预发布过滤、缺附件、限流、无稳定候选和外部 URL 拒绝。
- [ ] 实现 GitHub 固定仓库分页请求，取消传播、总超时和响应限制；不传入密钥。
- [ ] 实现五种状态、明确主操作与打开链接失败重试/复制链接；版本从安装包读取。

## Task 4：集成和交付验证

Files: 新增 Android 流程测试、`docs/verification/2026-09-20-v0.5.0.md`、README、`app/build.gradle.kts`。

- [ ] 版本递增到 0.5.0 / 9，运行 `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintRelease :app:assembleRelease`。
- [ ] 独立模拟器运行新功能和受影响旧流程测试；检查标准/320dp大字体截图，修复实测问题。
- [ ] Release 构建签名使用宿主已有生产签名；核验包名、版本、debuggable=false、无真实 Key，保留 SHA256。
- [ ] 更新验证报告与 TASK_STATE，保存已确认设计及实现提交；通知用户结果和限制。发布与推送按用户明确交付指令执行。

## Self review

规格覆盖：收藏、导航、单城查询→Task1；手动整理、内置回退、删除竞态与图片提交点→Task2；五种更新状态与链接→Task3；无配置与版本升级、安全区、大字体、正式构建→Task4。数据删除测试仅操作专用测试文件/模拟器，禁止清除真机数据。
