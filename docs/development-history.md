# 开发历史与验收索引

本文保留 README 改版前的开发与交付记录，内容截至 2026-09-23。产品介绍和下载入口见[项目首页](../README.md)。

自用 Android 旅行目的地发现工具：输入北京出发的日期、时段等条件，按真实余票找出可去的城市，再到 12306 购票。

目标设备：华为 Mate 60 Pro，HarmonyOS 4.2.0。使用 Kotlin + Jetpack Compose，输出 Android APK。

## 版本进展（截至 2026-09-23）

v0.9.0（versionCode `14`）将网页和图片搜索替换为豆包 Custom API，DeepSeek 负责整理。优先检索民间游记、美食体验及一日/两日原文路线，保留携程、马蜂窝，增加分类型检索、限流重试、缺图条目补查和路线证据核验。景点、美食单次及累计各最多 10 项；每项最多 3 图，兼容旧缓存并保留可用旧图。升级后需单独配置豆包搜索 Key，打开城市不会自动生成。见 [更新说明](releases/v0.9.0.md)、[发布验证](verification/2026-09-23-v0.9.0-release.md) 和 [真实搜索验收](verification/2026-09-23-community-search.md)。

v0.8.0（versionCode `13`）将图片改为景点、美食各自的相册，每条目最多 3 张、每城市最多 100 张。全量更新保留可用旧图，只给零图条目补图；保留全部旧景点和美食，同项更新，新增追加，累计允许超过单次生成上限。修复检索遗漏导致广府古城等缓存条目消失、编号重用和别名造成的关联问题。见 [更新说明](releases/v0.8.0.md) 与 [发布验证](verification/2026-09-22-v0.8.0-release.md)。

v0.7.0 正式版（versionCode `12`）汇总 v0.6.1 开发功能及想去清单双栏优化：左侧按省份浏览，顶部固定「全部」，右侧按收藏时间从新到旧排列；全部与各省分别保留浏览位置，窄屏使用下拉选择。新名称、多图相册、统一子页顶部和更新检查记录一并发布。见 [更新说明](releases/v0.7.0.md) 与 [发布验证](verification/2026-09-22-v0.7.0-release.md)。

v0.6.1 应用名更新为“有票再出发”，桌面、首页及关于页使用同一名称资源。实现四项飞书反馈，版本为 `0.6.1` / versionCode `11`：想去城市按省分组，省内最近收藏优先；子页面顶部统一为 B4 渐变与圆角返回入口；目的地相册最多 5 张，可横滑、看大图及来源；上次检查更新时间与结果保存在本机，返回或重启后保留。详细验收见 [v0.6.1](verification/2026-09-21-v0.6.1.md)。生产签名候选包位于 `artifacts/train-trip-v0.6.1-release.apk`，当时未单独发布，现已纳入 v0.7.0 正式版。

相册继续兼容旧单图缓存；手动更新先保存正文，再逐张保存图片，失败或取消保留已经完成的内容。Tavily 根级搜索图单独注明检索来源，仅采纳含当前城市与已选景点信息的国内简体资源。旧介绍不会自动重新生成或额外调用付费服务。

v0.6.0 实现已确认的 B4 冰蓝湖蓝方案：白色页面、浅蓝卡片与细描边，首页和想去页采用统一渐变顶部、图标标识和圆角操作入口。选中控件使用浅蓝底与主色边，余票状态保留独立语义。版本为 `0.6.0` / versionCode `10`。

出发时段现支持快捷多选，例如“早上＋晚上”只查两段时刻，选择会保存在本机。点击全天、取消最后一项或选齐四项恢复全天；自定义仍选择一个连续区间，完成后替换快捷多选，取消保留原选择。实现、设计稿与离线验证已完成，见 [时段多选验收](verification/2026-09-21-departure-time-multi-select.md)。已包含在当前 v0.6.0 候选包与真机安装中。

最长车程预设为“不限、3小时、5小时”，自定义通过小时/分钟滚轮选择，完成保存、取消保留原值；旧4小时/8小时作为自定义回显。可编辑设计与10项相关界面回归已完成，见 [车程选择器验收](verification/2026-09-21-duration-picker.md)。已包含在当前 v0.6.0 候选包与真机安装中。

查询目的地新增置顶的“想去”快捷分组，可直接勾选已收藏城市，与真实省份中的城市选择同步。支持本组全选、加载/失败重试和空清单；已选省份数量仍按城市实际省份计算，不修改收藏本身。12项相关测试与窄屏复测已通过，见 [想去快捷分组验收](verification/2026-09-21-wish-destinations.md)。已包含在当前 v0.6.0 候选包与真机安装中。

v0.5.0 已实现用户确认的 A 方案：底部「查票 / 想去」双栏目；想去城市支持批量添加、星标同步、取消与撤销。清单保存在本机，不保存过期余票；从清单查票使用独立的单城条件，不改变首页筛选。

设置新增「离线内容」和「关于与更新」。离线介绍可查看、手动更新、按城市删除，清理保留收藏和服务密钥；有内置介绍的城市自动回退内置内容。打开新城市不会自动调用 Tavily / DeepSeek，点击整理后才使用已配置的服务，沿用国内简体资料规则。正文保存后图片失败会保留新正文并明确提示。

版本检查由用户手动发起，从 GitHub 正式 Release 列表比较版本并检查 APK 和校验附件，发现新版本后打开发布页。无需自建服务器，也不会自动下载或安装。

本地正式发布包：`artifacts/train-trip-v0.6.0-release.apk`，校验文件同名 `.sha256`。应用名“有票就出发”，包名 `cn.traintrip.app`，版本 `0.6.0` / versionCode `10`，沿用 v0.4.0 的生产签名。包内不包含个人 API Key；新安装需自行配置内容服务，收藏、内置资料和查票不依赖这些 Key。v0.6.0 正式版已发布，包含出发时段多选、最长车程滚轮和目的地“想去”分组。真机为保留数据沿用历史设备证书，使用独立的 `artifacts/train-trip-v0.6.0-device-release.apk`；两包来自同一 Release 构建，均不可调试。具体验证见 [v0.6.0 验收](verification/2026-09-21-v0.6.0-ui.md) 和 [本次安装交付记录](verification/2026-09-21-v0.6.0-install-push.md)。

车次页仍支持手动刷新当前城市，点击整张卡片标记意向车次，席别只展示余票；无需选择也可点击“打开 12306 App”。详情介绍有景点 / 美食 / 玩法 / 贴士四个分页，支持横滑与阅读位置恢复。

0.4.0 支持整卡选择和可选的车次摘要，适配 320dp / 1.3 倍字体；已移除复制车次入口。局部刷新失败时保留旧车次和原始查询时间，展示未更新范围并支持重试；未安装铁路12306或打开失败时保留选择。验证记录见 [车次页与 App 跳转验收](verification/2026-09-19-v0.4.0-implementation.md) 和 [整卡选择验收](verification/2026-09-19-trip-card-selection.md)。

0.2.3 将席别返回“有”直接计入有票城市，支持多人出行；移除“数量待核验”分流。具体数字仍需满足人数，购票前继续重新查询核验。32 项核心测试、12 项离线界面交互通过，已覆盖安装到 Mate 60 Pro 并保留设置。详见 [“有”计入有票城市验收](verification/2026-09-19-available-seat-counting.md)。

0.2.2 修复尚未起售车次返回 `*` 导致整项解析失败的问题：保留起售状态与文案，正常有票车次继续参与结果，未起售车次不会误报有票。22 项核心测试、2 项模拟器交互及原始 310 条记录重放通过，已覆盖安装到 Mate 60 Pro 并保留原设置。详见 [尚未起售解析验收](verification/2026-09-19-not-yet-sale-parsing.md)。

0.2.1 改为单项失败后继续查询其余项目，保留失败原因和仅重试未成功项，手动停止仍生效。详见 [查询失败隔离验收](verification/2026-09-19-query-failure-isolation.md)。

0.2.0 新增天津、济南、青岛、大同、洛阳五城目的地灵感：结果卡片展示实景图与亮点，详情提供景点、美食、一日/两日玩法和来源署名，资料离线可读。其他城市保留查票功能。详见 [五城灵感验收](verification/2026-09-19-destination-inspiration.md)。

0.2.0 将目的地统一到省下一级行政单位，补充省份搜索和跨省选择；结果按省份放入可独立展开的连续容器。行政目录为 2023 快照，应用中明确标注。详见 [目的地省市分组验证](verification/2026-09-19-destination-provinces.md)。

0.1.2 将自定义出发时段改为同窗口设置开始和结束的小时、分钟滚轮，点一次“完成”应用；支持跨午夜、恢复全天和大字体上下布局。Pencil 已同步，Mate 60 Pro 已覆盖安装并确认原有设置保留。详见 [时段选择验证](verification/2026-09-19-time-range-picker.md)。

0.1.1 修复同窗口日期范围选择、连续查询进度条和车次耗时/到达日期排版；对应 Pencil 文件同步更新。详见 [界面修复验证](verification/2026-09-19-ui-polish.md)。

默认选择 24 个明确列出的城市，可在“查询目的地”调整。每个合并前的铁路城市保留一个代表站，仍可能遗漏部分同城站或目的地；界面始终显示覆盖说明，不声称全国完整。票源为 12306 当前网页使用的匿名查询协议，协议变化或访问失败会明确报错。

已通过 Android API 36 临时模拟器验证。2026-09-19 已在 Mate 60 Pro（ALN-AL00，HarmonyOS 4.2.0.221，Android API 31）安装并正常启动；历史 v0.4.0 已覆盖安装，筛选设置保持不变；当时已实测原生唤起铁路12306并返回。v0.5.0 后续已在真机验证收藏持久化。2026-09-21已保留数据覆盖安装包含三项筛选优化的v0.6.0：回拉APK哈希一致，UID和首次安装时间保持不变。本次只验证安装和包状态，未启动App逐项验收真机界面；三项功能已在隔离模拟器通过离线回归，未重复查询真实票源。

## 正式版本下载

- **[v0.9.0 最新版与更新说明](https://github.com/chengweiv5/train-trip/releases/tag/v0.9.0)** · [下载 APK](https://github.com/chengweiv5/train-trip/releases/download/v0.9.0/train-trip-v0.9.0-release.apk)
- [v0.8.0 历史版与更新说明](https://github.com/chengweiv5/train-trip/releases/tag/v0.8.0) · [下载 APK](https://github.com/chengweiv5/train-trip/releases/download/v0.8.0/train-trip-v0.8.0-release.apk)
- [v0.7.0 历史版与更新说明](https://github.com/chengweiv5/train-trip/releases/tag/v0.7.0) · [下载 APK](https://github.com/chengweiv5/train-trip/releases/download/v0.7.0/train-trip-v0.7.0-release.apk)
- [v0.6.0 历史版与更新说明](https://github.com/chengweiv5/train-trip/releases/tag/v0.6.0) · [下载 APK](https://github.com/chengweiv5/train-trip/releases/download/v0.6.0/train-trip-v0.6.0-release.apk)
- [v0.5.0 历史版与更新说明](https://github.com/chengweiv5/train-trip/releases/tag/v0.5.0) · [下载 APK](https://github.com/chengweiv5/train-trip/releases/download/v0.5.0/train-trip-v0.5.0-release.apk)

以上正式版本均提供生产签名 Release APK 与 SHA256 校验附件，不包含个人服务 Key。支持 Android 8.0 及以上，可覆盖升级同生产证书的旧正式版。历史设备测试证书与公开生产证书不同，无法互相覆盖；已安装专用设备包的用户应继续使用同证书升级包，保留现有数据。

## 构建与验证

使用 JDK 21、Android SDK 36。首次构建在 `local.properties` 中设置本机 `sdk.dir`。

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew :core:test :app:assembleDebug :app:lintRelease :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
```

Release 默认生成未签名包，个人生产签名和密码必须在仓库外配置，签名后再分发。禁止把私钥或服务 Key 提交进源码。

第二条命令需连接测试设备；包含少量真实 12306 查询，不应高频循环。核心测试离线运行，不依赖票数保持不变。

当前仅使用网络权限，无账号、定位、通讯录或存储权限。筛选偏好保存在本机；离开 App 停止查询，当前进程内保留结果并可继续。

- [首版设计](superpowers/specs/2026-09-18-train-trip-design.md)
- [本轮验证计划](superpowers/plans/2026-09-18-ticket-source-validation.md)
- [验证报告](verification/2026-09-18-ticket-source.md)
- [结构化证据](verification/2026-09-18-evidence.json)
- [已确认 UI 设计](../design/README.md)
- [Android 实现与验收记录](verification/2026-09-19-android-v1.md)
