# Train Trip

自用 Android 旅行目的地发现工具：输入北京出发的日期、时段等条件，按真实余票找出可去的城市，再到 12306 购票。

目标设备：华为 Mate 60 Pro，HarmonyOS 4.2.0。使用 Kotlin + Jetpack Compose，输出 Android APK。

## 当前状态

“了解目的地”已改为景点 / 美食 / 玩法 / 贴士四个分页，支持点击和横滑，景点与美食统一卡片；切换和返回保留阅读位置。已验证 320dp 大字体与短屏。此次安装包为 `artifacts/train-trip-destination-pages-debug.apk`，版本沿用 0.4.0；已覆盖安装 Mate 60 Pro，包哈希一致且筛选设置保留。见 [目的地分页验收](docs/verification/2026-09-19-destination-pages.md)。

v0.4.0 已实现筛选条件、真实余票查询、目的地城市汇总和车次详情。车次页支持手动刷新当前城市，点击整张卡片可标记意向车次，席别只展示余票；无需选择也可点击“打开 12306 App”。打开 App 不强制查询、不弹二次确认，也不回退官网。

本地安装包：`artifacts/train-trip-v0.4.0-debug.apk`（构建产物，不随源码提交）。自行构建后可从 `app/build/outputs/apk/debug/app-debug.apk` 获取，将文件传到手机后点击安装。应用名“有票就出发”，包名 `cn.traintrip.app`，版本 `0.4.0`。这是本机调试签名的自用版本，尚未上架。

0.4.0 支持整卡选择和可选的车次摘要，适配 320dp / 1.3 倍字体；已移除复制车次入口。局部刷新失败时保留旧车次和原始查询时间，展示未更新范围并支持重试；未安装铁路12306或打开失败时保留选择。验证记录见 [车次页与 App 跳转验收](docs/verification/2026-09-19-v0.4.0-implementation.md) 和 [整卡选择验收](docs/verification/2026-09-19-trip-card-selection.md)。

0.2.3 将席别返回“有”直接计入有票城市，支持多人出行；移除“数量待核验”分流。具体数字仍需满足人数，购票前继续重新查询核验。32 项核心测试、12 项离线界面交互通过，已覆盖安装到 Mate 60 Pro 并保留设置。详见 [“有”计入有票城市验收](docs/verification/2026-09-19-available-seat-counting.md)。

0.2.2 修复尚未起售车次返回 `*` 导致整项解析失败的问题：保留起售状态与文案，正常有票车次继续参与结果，未起售车次不会误报有票。22 项核心测试、2 项模拟器交互及原始 310 条记录重放通过，已覆盖安装到 Mate 60 Pro 并保留原设置。详见 [尚未起售解析验收](docs/verification/2026-09-19-not-yet-sale-parsing.md)。

0.2.1 改为单项失败后继续查询其余项目，保留失败原因和仅重试未成功项，手动停止仍生效。详见 [查询失败隔离验收](docs/verification/2026-09-19-query-failure-isolation.md)。

0.2.0 新增天津、济南、青岛、大同、洛阳五城目的地灵感：结果卡片展示实景图与亮点，详情提供景点、美食、一日/两日玩法和来源署名，资料离线可读。其他城市保留查票功能。详见 [五城灵感验收](docs/verification/2026-09-19-destination-inspiration.md)。

0.2.0 将目的地统一到省下一级行政单位，补充省份搜索和跨省选择；结果按省份放入可独立展开的连续容器。行政目录为 2023 快照，应用中明确标注。详见 [目的地省市分组验证](docs/verification/2026-09-19-destination-provinces.md)。

0.1.2 将自定义出发时段改为同窗口设置开始和结束的小时、分钟滚轮，点一次“完成”应用；支持跨午夜、恢复全天和大字体上下布局。Pencil 已同步，Mate 60 Pro 已覆盖安装并确认原有设置保留。详见 [时段选择验证](docs/verification/2026-09-19-time-range-picker.md)。

0.1.1 修复同窗口日期范围选择、连续查询进度条和车次耗时/到达日期排版；对应 Pencil 文件同步更新。详见 [界面修复验证](docs/verification/2026-09-19-ui-polish.md)。

默认选择 24 个明确列出的城市，可在“查询目的地”调整。每个合并前的铁路城市保留一个代表站，仍可能遗漏部分同城站或目的地；界面始终显示覆盖说明，不声称全国完整。票源为 12306 当前网页使用的匿名查询协议，协议变化或访问失败会明确报错。

已通过 Android API 36 临时模拟器验证。2026-09-19 已在 Mate 60 Pro（ALN-AL00，HarmonyOS 4.2.0.221，Android API 31）安装并正常启动；v0.4.0 已覆盖安装，筛选设置保持不变；已实测原生唤起铁路12306并返回。本轮未重复进行真实票源查询，余票刷新行为通过离线可控数据验证。

## 构建与验证

使用 JDK 21、Android SDK 36。首次构建在 `local.properties` 中设置本机 `sdk.dir`。

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test :app:assembleDebug :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二条命令需连接测试设备；包含少量真实 12306 查询，不应高频循环。核心测试离线运行，不依赖票数保持不变。

当前仅使用网络权限，无账号、定位、通讯录或存储权限。筛选偏好保存在本机；离开 App 停止查询，当前进程内保留结果并可继续。

- [首版设计](docs/superpowers/specs/2026-09-18-train-trip-design.md)
- [本轮验证计划](docs/superpowers/plans/2026-09-18-ticket-source-validation.md)
- [验证报告](docs/verification/2026-09-18-ticket-source.md)
- [结构化证据](docs/verification/2026-09-18-evidence.json)
- [已确认 UI 设计](design/README.md)
- [Android 实现与验收记录](docs/verification/2026-09-19-android-v1.md)
