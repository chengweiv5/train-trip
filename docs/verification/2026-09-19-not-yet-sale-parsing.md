# 尚未起售星号解析验收

2026-09-19，分支 `codex/not-yet-sale-parsing`，基线 `6b6c69e`，版本 0.2.2 / code 6。修改前 GitHub 待合并 PR 列表为空。

## 修复结果

只有在已识别为 SaleState.NOT_YET 的车次中，席别 `*` 才被映射为 AvailabilityKind.NOT_YET（“尚未起售”）。原始星号与起售文案保留，不计为有票或数量待核验；同一响应里的正常车次完整保留。无上下文的 `availability("*")` 以及已售、暂停、关闭车次的星号仍为未知状态，其他未知值和结构错误继续报错。

上一轮的失败隔离继续有效；原错误测试改用真正未知值“未知状态”。没有改动请求间隔、失败重试、取消、车次过滤或页面布局。

## 验证证据

- 修复前新增 6 项解析回归中的 4 项失败，覆盖原始响应、单条 C2551、混合车次和全部席别。见 [修复前测试](not-yet-sale-parsing/core-red.log)。
- 修复后 22 项核心测试全部通过（新增 6 项），覆盖起售时间保留、未售车次即使返回数字也不误报有票、其他未知值与星号上下文仍严格校验。见 [解析回归](not-yet-sale-parsing/TEST-cn.traintrip.core.NotYetSaleTest.xml)、[调度回归](not-yet-sale-parsing/TEST-cn.traintrip.core.SearchEngineTest.xml)。
- 直接使用构建后的 core.jar 重放此前诊断保存的原始文件：最小单条解析通过；完整 310 条全部保留，其中 291 条未起售、1173 个起售前占位值、0 个 UNKNOWN。见 [重放输出](not-yet-sale-parsing/replay.log)。
- 完整脱敏 fixture 已加入 `core/src/test/resources/official-not-yet-sale-sanitized.json`；采集时间为 2026-09-19 09:38:25（北京时间），路线为 2026-10-03 / BJP / TJP。只保留解析所需公开车次、车站、时刻、发售和席别字段；会话和预订相关字段为空。见 [采集元信息](not-yet-sale-parsing/fixture-capture.json)。
- `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 成功；lint 0 错误、13 条既有警告。见 [构建](not-yet-sale-parsing/build.log)、[lint](not-yet-sale-parsing/lint.txt)。
- 专用模拟器 `train_trip_inspiration_test` / `emulator-5580` 上 2 项离线交互通过（10.865 秒）：真实解析器处理未售/已售混合响应、正常显示有票车次且排除未售车次；上一轮失败后继续、错误查看和仅重试失败项仍通过。见 [交互验证](not-yet-sale-parsing/instrumentation.log)。

本轮未重新请求实时 12306 余票。310 条是此前采集的固定历史样本，不能当作当前有票数量。

## 真机安装

Mate 60 Pro（FMR0224725012307）从 0.2.1 / code 5 同签名覆盖升级为 0.2.2 / code 6。安装返回 Success，启动 Status: ok，MainActivity 前台核对通过；拉回已安装 APK 的 SHA-256 与交付包一致；筛选偏好文件安装前后完全一致。没有卸载或清除数据。

APK：`artifacts/train-trip-v2.2-debug.apk`。见 [安装回读](not-yet-sale-parsing/huawei-install.json) 和 [产物摘要](not-yet-sale-parsing/summary.json)。真机验证限于安装、版本、启动、包一致性和设置保留，票态交互使用专用模拟器的离线样本验证。

## 回滚

修改前源码、设备原 APK 和筛选设置备份于 `/private/tmp/train-trip-not-yet-sale-84m_2vys`。可在保存后续修改后按 manifest.json 选择恢复源码，或对本修复做反向提交。设备回滚优先用原代码构建更高版本号、相同签名覆盖，不卸载或清数据。
