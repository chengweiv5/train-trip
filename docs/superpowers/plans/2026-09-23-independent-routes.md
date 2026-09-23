# Independent Route Suggestions Implementation Plan

**Goal:** 玩法独立保存地点，按用户最终要求保留单条玩法同篇来源。
**Architecture:** PlanDay 新增 nullable stops 兼容 Gson；routeStops 读取独立名称或旧 ID 名称。生成、补救只检查玩法结构和来源归属；limit/merge 在景点截断前迁移旧路线。
**Tech Stack:** Kotlin, Gson, JUnit, Android Compose, Gradle, ADB。

## Global Constraints

- 不同玩法可用不同文章，同一条玩法的多天必须来自同一篇文章。
- 不校验玩法与景点的一致性，不逐字核对原文地点、顺序。
- 景点、美食各 10，玩法地点不受此限；每种天数一条完整玩法，单次及累计最多 3 种，优先较短天数。
- 不清缓存、不动公开 release，重要文件已备份到 .verification-private/independent-routes/backup。

## Steps

- [x] 修改 DestinationGuide.kt：`stops: List<String>? = null`；`routeStops(experiences)` 优先 stops，回退旧 ID；缓存校验使用日程内容和来源集合。允许 `days > 0 && schedule.size == days`。
- [x] 修改 GuideRouteEvidence.kt / GuideRouteRepair.kt / SearchGuideDecoder.kt / DeepSeekGuideGenerator.kt：生成 `stops`，一条玩法引用一个 sourceId，去除 quote/景点语义验证；补救输入不传景点列表。新增多日检索。
- [x] 修改 GuideItemPolicy.kt / GuideRefreshPolicy.kt / UI：先将旧路线 `day.copy(stops = day.routeStops(guide.experiences), experienceIds = emptyList())`，再合并截断；整体替换同天数玩法；UI 不再 `first { id == ... }`。
- [x] 重写相反旧契约测试；测试 3 天、景点外地点、不同玩法异源、单条混源拒绝、满容量和 Gson 缺字段迁移。运行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test :app:assembleRelease`。
- [x] LiveRouteRepairSmoke 用豆包与 DeepSeek 验证结果；手机覆盖安装同证书 Release，更新邯郸检查玩法与留存。最终包冷启动中手机断开，未完成最终回读，详见验证记录。
- [x] 记录结果，提交；刷新 origin/main，快进推送并回读；飞书发送结果并回读。
