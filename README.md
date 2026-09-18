# Train Trip

自用 Android 旅行目的地发现工具：输入北京出发的日期、时段等条件，按真实余票找出可去的城市，再到 12306 购票。

目标设备：华为 Mate 60 Pro，HarmonyOS 4.2.0。使用 Kotlin + Jetpack Compose，输出 Android APK。

## 当前状态

首版 App 和可安装调试 APK 已完成：筛选条件、真实余票查询、目的地城市汇总、车次详情、购票前核验、复制行程和打开 12306 官方查询页。

本地安装包：`artifacts/train-trip-v1-debug.apk`（构建产物，不随源码提交）。自行构建后可从 `app/build/outputs/apk/debug/app-debug.apk` 获取，将文件传到手机后点击安装。应用名“有票就出发”，包名 `cn.traintrip.app`，版本 `0.1.0`。这是本机调试签名的自用版本，尚未上架。

首版默认查询 24 个明确列出的城市，可在“查询目的地”调整。每个城市使用代表站，可能遗漏部分同城站或目的地；界面始终显示覆盖说明，不声称全国完整。票源为 12306 当前网页使用的匿名查询协议，协议变化或访问失败会明确报错。

已通过 Android API 36 临时模拟器验证。2026-09-19 已在 Mate 60 Pro（ALN-AL00，HarmonyOS 4.2.0.221，Android API 31）安装并正常启动；真机余票查询、完整交互与 12306 跳转仍待实测。

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
