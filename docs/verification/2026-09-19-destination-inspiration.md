# 五城目的地灵感验收

2026-09-19，分支 `codex/destination-inspiration`，基线 `08784f8`。版本 0.2.0 / versionCode 4。

## 实现结果

按用户执行中调整的范围，仅为天津、济南、青岛、大同、洛阳提供目的地资料。卡片展示实景图、推荐理由、旅行标签和建议天数；可进入详情，也可直接查看车次。详情包含三个代表体验、三种地方饮食、一日/两日玩法、季节及到站接驳提醒，以及可点击的来源、核对日期和图片作者/许可。

资料与五张照片打包离线使用，不等待额外网络。图片异步本地解码，资料缺失或解析失败时不影响查票。资料不覆盖的城市保持原车次入口。票源、排序、席别筛选和购票核验行为保持原实现；为离线测试增加 TicketSource 构造注入，默认仍使用 OfficialTicketSource。

目的地详情和车次页面按 cityId 保存滚动位置；从详情进入车次时返回详情，直接进入车次时返回结果。玩法日数独立于原有出发日期范围。

## 验证证据

- 最终 `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 成功；10 项核心测试通过（其中新增 3 项）。
- 新功能 5 项 Android 离线测试通过，24.274 秒：完整导航与显式核验、直接查看车次、未知城市、来源及照片许可、320dp/1.3 倍字体与状态恢复。
- 原日期/进度/车次布局 5 项与时间滚轮 4 项回归通过。初轮完整 14 项运行中，新测试脚本有 3 个定位/截图失败（LazyColumn 未组合的条目不能直接定位、弹窗导致双 root）；修正脚本后五项全部通过。初轮失败日志原样保留。
- 专用 Android API 36 模拟器 `emulator-5580`，1236×2676、480dpi；正式 MainActivity 冷启动 Status: ok，版本回读 0.2.0 / code 4。
- lint 0 错误、13 条建议警告，与本轮添加测试前构建一致。
- 五张实景照片均从已验证的 Wikimedia Commons 文件取得，并检查画面与城市一致。来源元数据、作者、许可和 SHA-256 保留在 sources.json。
- 7 张运行截图与 5 张 Pencil 参考图完成 230 段 Vision OCR；已结合可编辑原文及画面检查。320dp/1.3 倍字体的底部操作处于系统安全区之上，正文可滚动，不要求所有正文同屏显示。
- 本工作树 Pencil 新增 5 个根画板、79 个节点；裁切、同级重叠、空文本、占位均为 0。文件保存后 Edited 标记消失，大小 312205 字节；MCP 回读确认新节点保留。

## 产物

- APK：`artifacts/train-trip-v2.0-debug.apk`，准确字节数与 SHA-256 见 [summary.json](destination-inspiration/summary.json) 和仓库 artifacts 校验文件；签名验证通过。
- [最终构建](destination-inspiration/final-build.log)、[核心测试](destination-inspiration/guide-core-tests.xml)、[既有核心回归](destination-inspiration/core-regression-tests.xml)、[新功能交互](destination-inspiration/feature-tests.log)、[初轮完整回归](destination-inspiration/instrumentation.log)、[lint](destination-inspiration/lint.txt)、[签名](destination-inspiration/signature.txt)。
- [来源与照片许可](destination-inspiration/sources.json)、[OCR](destination-inspiration/ocr.json)、[运行截图](destination-inspiration/runtime)、[可编辑设计](../../design/train-trip-v1.pen)。

## 实际边界

开发阶段没有在个人手机覆盖安装，随后按用户要求完成安装（见下节）；本轮没有重新请求真实余票，交互测试使用明确的离线样例，截图不代表实时有票。来源按钮已验证回调 URL，未把它声称为浏览器端到端验证。城市资料是静态整理内容；开放、门票、预约、天气和返程需要另查。

Pencil MCP 的 filePath 参数未切换实际文档，早期三个新画板曾加入当时打开的另一任务设计稿。发现后已仅撤回本轮三个画板，保留其他任务内容；随后通过原生文件打开操作确认当前工作树路径，再重新加入并保存。最终交付与提交范围仅包含本工作树。

## 回滚

修改前备份：`/var/folders/2w/1f00699j5n5f4jp09txy9n2c0000gn/T/train-trip-inspiration-backup-5uo7h14l`。备份包含原入口、结果页、ViewModel、测试、构建配置、README 和 Pencil 文件。先备份当前工作后可按相对路径选择性恢复；新增资料文件可保留为未引用资源。

也可对本功能提交执行反向提交恢复代码与设计；不要 reset/clean，不卸载手机应用或清除偏好。真机安装前的 0.1.2 原 APK 与偏好文件均已备份，路径见下节。若需设备回滚，优先由旧代码构建更高版本号并使用相同签名覆盖，避免卸载或清除设置。

## 真机安装补充验收

用户要求“安装到真机”后，已从 0.1.2 / code 3 覆盖升级到 0.2.0 / code 4。设备为 Mate 60 Pro（HUAWEI ALN-AL00，HarmonyOS 4.2.0，Android API 31）。安装返回 Success；正式入口冷启动 Status: ok，MainActivity 前台回读通过。

原版本与新版本签名证书一致。拉取已安装 APK 后核对 SHA-256，与交付安装包完全一致。travel-filters.xml 安装前后哈希一致，原有筛选偏好保留。没有卸载或清除数据。

备份原 APK、偏好与文档修改前副本：`/var/folders/2w/1f00699j5n5f4jp09txy9n2c0000gn/T/train-trip-device-install-uyc53mf8`。本次检查限于安装、启动、版本、安装包和偏好保留，未将它等同于真机全部功能回归。详见 [真机安装回读](destination-inspiration/huawei-install.json)。
