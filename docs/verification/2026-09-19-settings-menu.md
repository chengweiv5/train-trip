# 设置菜单验收

## 已实现

首页右上角“设置”进入独立菜单，下设“大模型”和“搜索引擎”。DeepSeek 可修改模型 ID 与 API Key；Tavily 管理 API Key，明确基础搜索方式。两项独立保存和移除，空 Key 保留原值。默认模型 deepseek-flash，生成请求及缓存 model 记录采用同一模型快照。

旧 DeepSeek/Tavily Keystore alias 与密文位置保留；模型 ID 单独保存。已有缓存不因更改模型失效。保存不自动发起计费调用，进入设置取消当前目的地整理；返回保持筛选条件。编辑页 Key 只在内存、遮罩输入、不回显旧值、禁止截图；返回丢弃草稿。忙时禁止重复保存/移除和返回。

## 验证

- `:core:test`：59 项通过，0 失败；新增自定义模型请求和缓存记录一致、非法模型不发请求。
- 专用模拟器 `emulator-5582`：13 项通过。SettingsMenuTest 3 项，DeepSeekDestinationTest 9 项，AppFlowTest 的 settingsReturnKeepsFilterSelection 1 项。覆盖服务分项导航、取消草稿、配置独立、旧密钥兼容、保存不生成、缓存/取消、1.5 倍字体、忙状态系统返回、敏感页 flag 恢复、筛选保留。
- debug APK、测试 APK、lint 构建通过；lint 0 errors / 16 warnings，其中新增一项为 SharedPreferences.edit 的 KTX 建议，不影响行为。
- 首页和菜单截图已目视检查；输入页通过语义树、布局测试和 FLAG_SECURE 断言检查，未禁用密钥保护。
- 代码及 APK 内容扫描未检出真实格式 API Key。本轮没有调用收费 API。
- 私有证据：`.verification-private/v0.4.0/settings/`，包括 build-final.log、instrumentation.log、menu.jpg、home.jpg、secret-scan.json。

## 设计稿与阻塞

Pen MCP 中已新增四个画板：首页设置入口、设置菜单、大模型设置、搜索引擎设置，使用项目现有视觉变量；节点 kL4Hp/Y1162/cqFPn/w27DL。最终布局无裁剪问题，预览导出到私有证据目录 design-previews。

由于 Mac 锁屏，原生工具无法执行 Pen 保存，磁盘上的 design/train-trip-v0.4.0.pen 尚未包含这四个画板。已请求用户解锁；不宣称设计文件已保存，不覆盖其他项目当前打开的画板。需解锁后打开此文件并保存，再核对 git diff。

ADB 未检测到 Mate 60 Pro。本轮仅在专用模拟器安装测试，尚未覆盖安装真机。此前 punk-12 通知已被自动审批两次拒绝，本轮无新的授权，未重复发送。

## 交付与回滚

`artifacts/train-trip-settings-personal-debug.apk`，版本 0.4.0 / versionCode 8。个人自用版本，功能分支本地提交，不公开推送。

代码备份 `.verification-private/v0.4.0/settings/source-before.tar`；原版本 APK `artifacts/train-trip-tavily-personal-debug.apk` 可覆盖安装回滚，不卸载、不清数据。新模型字段旧版忽略，旧 Key 仍兼容。
