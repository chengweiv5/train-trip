# 设置菜单

用户要求新增设置菜单，管理大模型和搜索引擎。当前只接入 DeepSeek 和 Tavily，先管理已有服务；不增加未接入的供应商选择。

## 页面和操作

首页右上角增加文字“设置”入口，移除底部“目的地内容设置”。目的地缺少配置时仍提供“配置内容服务”，打开相同的设置菜单。

设置首页保留返回导航，显示“大模型 / DeepSeek · 当前模型 / 已配置或未配置”和“搜索引擎 / Tavily · 基础搜索 / 已配置或未配置”两行，以及本机加密与缓存说明。点击分别进入全屏配置页。

大模型页：固定服务 DeepSeek、可编辑模型 ID（默认 deepseek-flash）、遮罩 API Key、当前密钥状态、保存、独立移除密钥。模型 ID 限制 1–100 个字母数字或 . _ : / -，无空白；保留固定官方 API 地址。模型可用性由真实生成时服务返回判断，不凭名称宣称可用。搜索引擎页固定 Tavily、基础搜索说明、遮罩 Key、保存与独立移除。

草稿只保留页面内存，不使用 rememberSaveable 保存 Key，不回填已有 Key；空 Key 保留已存值。返回/取消丢弃草稿；保存成功返回菜单，显示结果；失败留在编辑页，回读真实状态。敏感编辑页启用 FLAG_SECURE，离开恢复原 flag；保存过程中禁用重复保存/移除/返回以免状态竞争。移除按钮明确标示服务名，保留已有缓存与另一项配置。

进入设置取消当前目的地生成。设置保存只保存，不触发搜索或模型计费；返回原页面和查票条件保持，缺内容由用户点重试。系统返回从编辑页回菜单、菜单回原页面。键盘出现/大字号/短屏时页面滚动且保存按钮可达。

## 实现边界

新增 SettingsScreen.kt 包含设置菜单和编辑页，复用现有主题。TrainTripApp 保留业务 page，不向查票 ViewModel 加设置状态；设置是单独本地导航，原 SaveableStateHolder 保留业务页面滚动。移除旧 DeepSeekSettingsDialog.kt 后测试迁移至新页。

DeepSeekSettings 的 preference、encryptedKey、Keystore alias 不变。新增 GuideModelPreference 接口及 Android 实现，模型名称单独存 destination-ai preference，缺失用原默认。移除 Key 不删除模型选择。

DestinationViewModel 增加模型名、保存忙状态；分别保存模型与搜索设置，旧双 Key 内部保存逻辑保留测试兼容但不自动生成。每次生成从模型配置读取快照；DeepSeekGuideGenerator 支持 model supplier，真实请求、DestinationGuide.model 记录同一模型。凭据只发给固定官方接口，无自动连通性收费测试。

## 验证与交付

同步 design/train-trip-v0.4.0.pen 中的首页入口、设置菜单、大模型配置、搜索引擎配置。保留已有画板，不触及当前打开的其他项目。

测试：默认及自定义模型请求/缓存记录一致，旧配置向后兼容；两项保存/移除独立，设置保存不调用搜索；导航取消草稿、查票条件保留、忙状态防重复、大字短屏可达、敏感页面保护。核心测试、Android 构建/lint、专用模拟器回归，真机若重新连接则覆盖安装保留数据。

备份位于 .verification-private/v0.4.0/settings/source-before.tar。版本保持 0.4.0/8，本地功能分支提交，个人素材不公开推送。通知已有自动审批阻塞未获得新的明确批准，本轮不绕过。回滚可用前一 APK 覆盖安装及反向提交。
