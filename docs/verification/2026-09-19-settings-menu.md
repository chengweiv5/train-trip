# 设置菜单验收

## 已实现

首页右上角“设置”进入独立菜单，下设“大模型”和“搜索引擎”。DeepSeek 可修改模型 ID 与 API Key；Tavily 管理 API Key，明确基础搜索方式。两项独立保存和移除，空 Key 保留原值。默认模型 deepseek-flash，生成请求及缓存 model 记录采用同一模型快照。

旧 DeepSeek/Tavily Keystore alias 与密文位置保留；模型 ID 单独保存。已有缓存不因更改模型失效。保存不自动发起计费调用，进入设置取消当前目的地整理；返回保持筛选条件。编辑页 Key 只在内存、遮罩输入、不回显旧值、禁止截图；返回丢弃草稿。忙时禁止重复保存/移除和返回。

## 验证

- `:core:test`：59 项通过，0 失败；新增自定义模型请求和缓存记录一致、非法模型不发请求。
- 专用模拟器 `emulator-5582`：13 项通过。SettingsMenuTest 3 项，DeepSeekDestinationTest 9 项，AppFlowTest 的 settingsReturnKeepsFilterSelection 1 项。覆盖服务分项导航、取消草稿、配置独立、旧密钥兼容、保存不生成、缓存/取消、1.5 倍字体、忙状态系统返回、敏感页 flag 恢复、筛选保留。
- debug APK、测试 APK、lint 构建通过；lint 0 errors / 16 warnings，其中新增一项为 SharedPreferences.edit 的 KTX 建议，不影响行为。
- 首页和菜单截图已目视检查；输入页通过语义树、布局测试和 FLAG_SECURE 断言检查，未禁用密钥保护。
- 代码及 APK 内容扫描未检出真实格式 API Key。设置菜单实现轮没有调用收费 API；后续真机验收手动整理泰安一次，见下。
- 私有证据：`.verification-private/v0.4.0/settings/`，包括 build-final.log、instrumentation.log、menu.jpg、home.jpg、secret-scan.json。

## 已保存设计稿

`design/train-trip-v0.4.0.pen` 已原生保存；窗口中的 Edited 标记消失，Git 检测到文件变化。四个新增画板为：首页设置入口、设置菜单、大模型设置、搜索引擎设置，节点 w27DL/kL4Hp/Y1162/cqFPn，使用项目现有视觉变量。保存副本经 MCP 回读包含四个画板，布局无裁剪问题；最终预览与 OCR 已检查。

文件 SHA-256：`6d52cb7e4cdfc26be289d3f4cd12cc4f70c753a68b0782e65e2c1db0895cf400`。备份 `design-before-save.pen`、回读副本 `design-saved-readback.pen` 及预览位于私有证据目录。未保存其它项目的画板。

## 真机验收

- Mate 60 Pro / ALN-AL00 已通过 `adb install -r` 覆盖安装，未卸载或清数据。安装前备份 `phone/before.apk` 与非敏感筛选配置。
- 安装 APK SHA-256 与本地完全一致：`377246fe7d3c7110e6b6f6f512ee929b96c7aab5c95bb294426a46adcd75872a`。
- 筛选配置与 DeepSeek 密文文件 SHA-256 均与安装前一致。北京、10 月 1–3 日、5 人、2 类席别、3 小时车程及 5 个目的地保留。
- 首页设置入口、独立菜单、DeepSeek 模型与 Tavily 页面实际打开；密钥框遮罩，原密钥不回显，模型页窗口含 SECURE 标记。
- 开始检查时 Tavily 未配置，之后从手机界面回读为已配置；本轮代理没有覆盖该密钥。
- 手动重试泰安一次，看到 Tavily 检索进度后成功生成：5 个景点、6 种美食、2 个玩法建议，无照片。缓存记录模型为 `deepseek-flash`，生成时间为 2026-09-19 19:23:58（北京时间）。按当前实现完成两次 basic 搜索与一次模型生成，提供方账单未单独核对。
- 三篇来源均为泰安市文化和旅游局；点击美食“查看原文”，确认向系统浏览器传入对应的官方原文 HTTPS URL。
- 仅重启 Train Trip 后再次打开泰安，摘要和景点直接展示。缓存 SHA-256、生成时间和 attempt 修改时间完全不变，没有再次整理；未关闭手机网络做飞行模式测试。
- 真机证据：`.verification-private/v0.4.0/settings/phone/verification.json`、`taian-generated.json`。

用户明确批准后，2026-09-19 20:13 已由 punk-12 for Codex 向指定配置群发送完成状态。回读确认机器人身份、接收群和正文正确，消息 ID：om_x100b65d389e9f4a4c00c73c9b8f2f34。此前自动审批阻塞已解除。

## 交付与回滚

`artifacts/train-trip-settings-personal-debug.apk`，版本 0.4.0 / versionCode 8。个人自用版本，功能分支本地提交，不公开推送。

代码备份 `.verification-private/v0.4.0/settings/source-before.tar`；手机原版本 APK `.verification-private/v0.4.0/settings/phone/before.apk` 可覆盖安装回滚，不卸载、不清数据。新模型字段旧版忽略，旧 Key 仍兼容。
