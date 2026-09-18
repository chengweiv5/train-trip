# Train Trip

自用 Android 旅行目的地发现工具：输入北京出发的日期、时段等条件，按真实余票找出可去的城市，再到 12306 购票。

目标设备：华为 Mate 60 Pro，HarmonyOS 4.2.0。计划使用 Kotlin + Jetpack Compose，输出 Android APK。

## 当前状态

已确认首版需求并完成首轮票源验证：四个日期/城市对样本返回真实余票，其中一个与官方网页交叉核对一致。当前尚未实现 Android App 或生成 APK；全国目的地覆盖与目标真机运行待后续验证。

- [首版设计](docs/superpowers/specs/2026-09-18-train-trip-design.md)
- [本轮验证计划](docs/superpowers/plans/2026-09-18-ticket-source-validation.md)
- [验证报告](docs/verification/2026-09-18-ticket-source.md)
- [结构化证据](docs/verification/2026-09-18-evidence.json)
