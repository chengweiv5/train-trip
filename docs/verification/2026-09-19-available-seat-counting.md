# “有”计入有票城市验收

2026-09-19，功能分支 `codex/available-seat-counting`，基线 `2b6df9d`（包含与 origin/main 的整合），版本 0.2.3 / code 7。修改前待合并 PR 列表为空。

席别返回“有”时，应用支持的 1–20 人直接按有票处理；城市总数、省份分组、详情席别和再次核验使用相同判断。原始值仍为“有”、count=null，不编造张数。删除旧数量待核验分流和文案。具体数字仍需同一车次、同一席别满足人数；未起售、暂停/停止发售、停售时间及其他筛选条件继续生效。

## 验证

- 修改前新建 3 项核心回归中 1 项失败，复现双人时城市漏计。见 [修改前日志](available-seat-counting/core-red.log)。
- 修改后 32 项核心测试全部通过；新增覆盖全部人数 1–20、数字不足、跨席别不凑票、无票/候补和发售/筛选边界。原有未起售星号、失败隔离及省份分组回归继续通过。见 [新增核心测试](available-seat-counting/TEST-cn.traintrip.core.AvailableSeatTest.xml)。
- `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 成功，lint 0 错误、13 条既有警告。见 [构建日志](available-seat-counting/build.log) 和 [lint](available-seat-counting/lint.txt)。
- 专用模拟器 `train_trip_inspiration_test` 上 12 项离线交互通过，耗时 62.134 秒。新增 3 项使用真实 TicketParser：双人“有”出现在城市卡片和详情；重新查询仍为“有”时正常核验通过；降为1张或无票时清除选择、禁止跳转并从有票城市移除。另覆盖未起售、单项失败继续、省份选择/展开/状态恢复及大字体。见 [交互日志](available-seat-counting/instrumentation.log)。

本轮使用离线样本验证票态，不将其作为实时余票结果，也没有自动打开购票入口。

## 真机安装

Mate 60 Pro 从 0.2.2 / code 6 同签名覆盖升级为 0.2.3 / code 7，安装 Success，MainActivity 启动 Status: ok 且前台回读通过。已安装 APK 拉回的 SHA-256 与本地产物一致：`4a367268a03beb8878ea67bb1f50afd0e9b692ff46bbb41469425b0cd33904b5`。安装当时筛选偏好文件升级前后完全相同。

APK：`artifacts/train-trip-v2.3-debug.apk`，不随源码提交。见 [真机验证](available-seat-counting/huawei-install.json)。专用模拟器已关闭。

## 回滚

源码基线归档、真机原 APK 和安装当时偏好备份在 `/private/tmp/train-trip-available-wgv8qrxi`。源码可对本功能提交做反向提交；设备优先用基线代码构建更高版本号、相同签名 APK 覆盖安装，避免卸载/清除数据。
