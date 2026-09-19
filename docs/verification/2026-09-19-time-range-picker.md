# 同窗口出发时段选择验收

2026-09-19，版本 0.1.2 / versionCode 3，分支 `codex/ui-polish`。保留前一轮日期、进度条和车次排版修改；以下记录为提交前完成的构建、交互及安装验收。

## 结果

点击“自定义”直接进入同一个底部窗口，同时显示开始与结束的小时、分钟滚轮，无需输入文本或切换第二个窗口。滚轮停止后，点一次“完成”应用两端；取消、返回不应用草稿。支持草稿恢复、跨午夜、恢复全天、24:00 结束；结束小时为 24 时分钟固定 00。开始与结束相同时解释原因并禁用完成。

窄屏或字体超过 1.15 倍时两组滚轮上下排列，内容可滚动，底部操作保持可达。原生 NumberPicker 处理滚动与无障碍操作，禁用键盘编辑；API 29 以上使用主题颜色及放大字体。

## 验证

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline` 成功；lint 0 错误、14 条建议警告。新增一条仅涉及测试依赖 Espresso 可更新版本的建议，其余 13 条为此前已有。
- Android API 36 临时模拟器：`TimeRangePickerTest` 四项、`AppFlowTest#timeAndScopeApply` 一项，最终 `OK (5 tests)`，耗时 26.032 秒。覆盖手指滑动后保存、两端一次应用、取消保留原值、相等/24:00/跨午夜/全天、草稿恢复、1.3 倍字体及首页筛选回显。本轮没有发起实时余票查询。
- 正常与 1.3 倍字体运行截图已复核；Pencil 正常和上下布局均复核。四张局部图完成 Vision OCR，共 105 段；冒号存在 OCR 误识别，已结合可编辑原文和截图确认，不把 OCR 当作逐字无误证明。
- Pencil `design/train-trip-v1.pen` 已保存，窗口 Edited 标记消失；文件 276391 字节，保存时间 08:22:00，MCP 回读为 749 个可见节点、338 个文字节点，裁切、占位、空文字/名称、顶层重叠均为 0。更新 `HceGu`、交接说明 `gNcl5`，新增上下布局 `NE7Tn`；参考图已重新导出。
- Mate 60 Pro（HUAWEI ALN-AL00）覆盖安装返回 Success，版本回读 0.1.2 / code 3；启动 Status: ok，MainActivity 在前台。更新前后偏好文件哈希一致。真机检查限于安装、启动及偏好保留，功能交互回归在临时模拟器完成。

## 产物与证据

本地 APK：`artifacts/train-trip-v1.2-debug.apk`，11,613,181 字节，签名验证通过。

SHA-256：`2ef824dca47f32cfbb16bc9588abb9bf7a82aa5d05b5f89c316de9b189d97972`。

- [构建日志](time-picker/build.log)、[五项回归](time-picker/instrumentation.log)、[lint](time-picker/lint.txt)、[签名](time-picker/signature.txt)
- [汇总](time-picker/summary.json)、[真机安装](time-picker/huawei-install.json)、[OCR](time-picker/ocr.json)
- [正常运行图](time-picker/time-range.jpeg)、[大字体运行图](time-picker/time-large-font.jpeg)
- [Pencil 结构校验](../../design/validation/time-picker.json)、[Pencil 参考图](../../design/reference/time-picker)

## 保留与回滚

修改前备份位于本机 `/var/folders/2w/1f00699j5n5f4jp09txy9n2c0000gn/T/train-trip-time-backup-nt5l0a8r`，保留 FilterSheets、AppFlowTest、构建配置、Pencil、README 与相关参考图的原始内容。恢复时先备份当前版本，再按相对路径恢复这些文件；不使用 reset/clean，以免覆盖前一轮尚未提交的修改。新增 TimeFilterSheet 不再被入口引用后可保留待审阅。

0.1.0 与 0.1.1 本地 APK 均保留。需要退回手机旧版时可对调试包尝试 `adb install -r -d artifacts/train-trip-v1.1-debug.apk`；若系统拒绝降级，应由旧实现构建更高版本号覆盖更新，不通过卸载清除设置。
