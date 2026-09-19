# 查询失败隔离验收

2026-09-19，分支 `codex/query-failure-isolation`，基线 `64b3bcb`，版本 0.2.1 / code 5。修改前查询 GitHub 待合并 PR，返回空列表。

## 结果

SearchEngine 不再因单项 Failure 或连续 3 次失败结束整个批次。query 抛出的普通异常转为当前项 Failure，继续查询后续项；CancellationException 原样传播。删除数据源错误类型的 stopSearch 字段，避免恢复旧的批次停止语义。

维持串行请求、默认 1100 毫秒间隔和每项一次请求。全部尝试完毕后，待查为 0、running=false、stopped=false；有失败仍为部分结果，可查看原因、仅重试未成功项。手动停止、切后台和初始化失败处理保留。

此前诊断的未起售 `*` 仍会导致对应日期/城市项解析失败，但不会再阻塞其他项目。本次只改变失败隔离，没有把未知票态映射为有票、无票或候补。

## 验证

- 修复前 16 项核心测试中 4 项失败：真实解析失败后继续、连续失败后继续、抛异常后继续，以及重试计数。见 [修复前记录](query-failure-isolation/core-red.log)。
- 修复后 16 项核心测试全部通过，其中新增 6 项调度测试；覆盖真正取消与数据源主动取消、成功结果保留、请求间隔不变。见 [核心调度测试](query-failure-isolation/TEST-cn.traintrip.core.SearchEngineTest.xml) 和 [既有回归](query-failure-isolation/TEST-cn.traintrip.core.CoreTest.xml)。
- `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 成功。lint 为 0 错误、13 条既有警告。见 [构建](query-failure-isolation/build.log)、[lint](query-failure-isolation/lint.txt)。
- 项目专用模拟器 `train_trip_inspiration_test` / `emulator-5580` 上新增离线交互测试通过（1 项，7.456 秒）：天津失败后其余两城成功；错误弹窗保留原因；重试只再次调用失败项目，最终三城成功。见 [Android 测试](query-failure-isolation/instrumentation.log)。
- 本次实现/回归没有再次访问实时 12306 余票接口；测试不代表当前真实有票。

## 真机与产物

Mate 60 Pro（FMR0224725012307）从 0.2.0 / code 4 同签名覆盖升级为 0.2.1 / code 5，安装 Success、启动 Status: ok、MainActivity 前台回读成功。安装后拉回 base.apk，SHA-256 与交付 APK 一致；安装前后 travel-filters.xml 哈希一致。没有卸载或清除数据。设备验证范围为安装、启动、版本、包一致性和设置保留，失败隔离交互在离线模拟器验证。

APK：`artifacts/train-trip-v2.1-debug.apk`。摘要与 SHA-256 见 [summary.json](query-failure-isolation/summary.json)，设备回读见 [huawei-install.json](query-failure-isolation/huawei-install.json)。

## 回滚

源码及原设备 APK、筛选偏好已备份至 `/private/tmp/train-trip-failure-isolation-g4i0ttxi`；设备文件分别在 `device-before` 和 `device-after`，不提交个人偏好。可在保存后续修改后按 manifest.json 选择性恢复原源码，或对本修复提交做反向提交。设备回滚优先使用原代码构建更高版本号、相同签名覆盖安装，避免卸载或清数据。
