# 0.2.0 目的地省市分组验证

分支：`codex/destination-ui-design`。UI 依据 [已确认设计](../../design/destination-ui-proposal.md) 实现；版本 `0.2.0` / versionCode 4。

## 实现

- 行政目录与铁路字典分离：34 个省级地区、391 个省下一级目的地；直辖市仅北京、天津、上海、重庆四项。424 个旧铁路城市 ID 可迁移，县/区归入地级单位；阿拉尔、图木舒克、北屯、双河、昆玉按站级修正保留为省直辖目的地。
- 选择页为全屏草稿：左省右市；小屏或字体 >1.15 改省份下拉；支持中文、短拼音、全名拼音和城市拼音；省份命中显示全省城市；跨省多选、本省全选、已选分组移除、取消不生效。无车站城市可见并禁选，文案显示 2023 行政目录快照边界。
- 结果页按省份拼音分组，省内再按车程或车次数排序。连续浅绿容器包含省名、白色城市卡片和底部收起；收起显示前三城预览；首省默认展开，各省独立开合。新查询重置，刷新、排序、配置恢复和详情返回保留。
- 城市卡片和详情显示省份；查询中、失败、未开售、无票和多人数量待核验保持不同状态。

## 验证

- 核心 JVM：14 项通过，其中目录/迁移/搜索/合并查询/歧义城市/省份状态 7 项；未知车站组不进入可选目录，同铁路组拆分后只汇总选中的行政目的地。最终迁移补充后，API 31 的 7 项目的地测试再次全部通过。
- 构建：`:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 成功；lint 0 errors、12 个既有依赖/API/样式警告。
- Android 12（与目标 HarmonyOS API 31 同 API 级）模拟器：全量 21 项通过，包含 7 项目的地交互、日期/时间/大字体回归和实时 12306 查询测试。实时测试于 2026-09-19 09:33 CST 成功查询北京→天津/石家庄/秦皇岛；测试日志中的票数只证明当次协议可用，不承诺未来票数。
- Android 16 / API 36：7 项目的地交互全部通过。覆盖省份拼音搜索和全选、跨省草稿、已选分组、直辖市、禁选原因、浏览位置恢复、独立折叠、新查询重置、排序/刷新/状态恢复和 1.3 字体。
- API 31/36 运行截图已人工复核：省份与卡片从属清楚、收起预览直观、大字体下拉和底部完成按钮可见；无重叠或边缘裁切。6 张 API 36 截图经 macOS Vision OCR 识别 138 个文字块，无读取错误。
- 额外的桌面 JVM `:core:liveSmoke` 因本机 JDK 信任库返回 `SSLHandshakeException: PKIX path building failed` 未完成；未关闭 TLS 校验或修改宿主证书。Android 端实时初始化、天津/石家庄/秦皇岛查询及购票前核验均已通过。详见 [证据目录](destination-provinces/)。

## 数据边界

行政目录基于 2023-06-30 国家统计局统计用区划快照的开源整理版，已停止更新；应用明确显示快照年份，不声称是 2026 实时行政目录。目录、来源、许可、同名和非行政站名证据见 [目的地行政目录](../data/destinations.md)。未知的未来铁路城市会隔离，需补充证据后才能进入目录。

## APK 与回滚

- APK：`artifacts/train-trip-v2-debug.apk`，11,963,059 字节。
- SHA-256：`e5985ec63c74bb5cee14afc45977edcaf2cf12448f9400b49886bb782d327fc2`；Android build-tools 36 `apksigner verify` 通过。
- 修改前源码备份：`/private/tmp/train-trip-provinces-backup-f3dhu6v8/`；上一版 APK 保留。代码可从当前功能分支按文件回退。开发验收阶段未安装真机；后续按用户要求完成 Mate 60 Pro 覆盖安装，详见下方记录。未提交或推送。

## 真机安装

2026-09-19 按用户要求在 Mate 60 Pro（ALN-AL00、API 31）执行 `adb install -r`。已回读版本 `0.2.0` / 4，拉回设备 APK 验证 SHA-256 与交付包一致；启动返回 `Status: ok`，MainActivity 位于前台。安装前后筛选偏好文件哈希一致。

安装前 APK 与偏好备份：`/private/tmp/train-trip-v2-phone-backup-lj7hj91g`。回滚可覆盖安装备份 APK，保留现有数据；无需卸载。证据：[huawei-install.json](destination-provinces/huawei-install.json)。
