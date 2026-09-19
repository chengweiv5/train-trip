# Train Trip

自用 Android 旅行目的地发现工具：输入北京出发的日期、时段等条件，按真实余票找出可去的城市，再到 12306 购票。

目标设备：华为 Mate 60 Pro，HarmonyOS 4.2.0。使用 Kotlin + Jetpack Compose，输出 Android APK。

## 当前状态

首版 App 和可安装调试 APK 已完成：筛选条件、真实余票查询、目的地城市汇总、车次详情、购票前核验、复制行程和打开 12306 官方查询页。

本地安装包：`artifacts/train-trip-v2.0-debug.apk`（构建产物，不随源码提交）。自行构建后可从 `app/build/outputs/apk/debug/app-debug.apk` 获取，将文件传到手机后点击安装。应用名“有票就出发”，包名 `cn.traintrip.app`，版本 `0.2.0`。这是本机调试签名的自用版本，尚未上架。

0.2.1 改为单项失败后继续查询其余项目，保留失败原因和仅重试未成功项，手动停止仍生效。16 项核心测试和模拟器离线交互通过，已覆盖安装到 Mate 60 Pro 并核对版本、启动、包哈希与原有筛选设置。详见 [查询失败隔离验收](docs/verification/2026-09-19-query-failure-isolation.md)。

0.2.0 新增天津、济南、青岛、大同、洛阳五城目的地灵感：结果卡片展示实景图与亮点，详情提供景点、美食、一日/两日玩法和来源署名，资料离线可读。其他城市保留查票功能。详见 [五城灵感验收](docs/verification/2026-09-19-destination-inspiration.md)。

0.2.0 将目的地统一到省下一级行政单位，补充省份搜索和跨省选择；结果按省份放入可独立展开的连续容器。行政目录为 2023 快照，应用中明确标注。详见 [目的地省市分组验证](docs/verification/2026-09-19-destination-provinces.md)。
