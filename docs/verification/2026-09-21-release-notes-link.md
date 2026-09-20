# 更新说明 404 修复

2026-09-21，基于 `a6b5181`，在 `codex/v0.5.0-design-options` 分支修复。

## 原因与修改

“关于与更新”中的“更新说明”原来直接根据安装版本拼接 `/releases/tag/v0.5.0`。GitHub 当时仅发布 v0.4.0；推送 main 并不会创建 Release。实际请求确认：v0.5.0 标签页 HTTP 404，仓库 Release 列表 HTTP 200。开放 PR 列表为空。

入口改为默认打开本仓库 Release 列表。仅当本次检查成功，且查得的 Release 与当前安装版本一致，才打开该 Release 返回的实际地址。未检查、失败、本地版本更高或远端版本不同均回退发布列表。新版本下载按钮仍打开查得的新版本 Release。此入口不新增网络检查、创建 Release 或触发下载。

链接白名单增加精确的仓库 `/releases` 路径，其他站点、HTTP、相似仓库与相似路径仍被拒绝。界面文字与布局未变，交互规格同步更新。

## 验证

- 修复前按钮回归 4 项中 3 项失败：未检查、检查失败、本地高于远端时均打开不存在的版本页；真实 HTTP 请求同步复现 404。
- 修复后更新模块 7 项 core 测试通过；ReleaseNotesLinkTest 与 WishlistFlowTest 共 12 项 Android 回归通过。测试截获实际按钮发出的 ACTION_VIEW，不依赖只测试 URL 拼接函数。
- Debug、Android 测试及 Release 构建通过；lintRelease 0 错误、19 条既有警告；diff 检查通过。
- 专用模拟器安装实际 Release 包，冷启动成功，265 ms；用例未调用模型/搜索服务。
- v2/v3 签名验证通过，未设置 debuggable，APK 服务 Key 模式与私有文件扫描均无命中。

## 真机交付

本地独立安装包 `artifacts/train-trip-v0.5.0-release-notes-fix-device-release.apk`，版本保持 0.5.0 / 9，8,886,697 字节。SHA256：`9be8af55273beb70687c9ac5e0c65c46d5687a90a50dc69ec960d74232afc6b4`。

使用与原 Mate 60 Pro 安装相同的历史设备证书（Release 构建），证书 SHA256：`cae9ed8a5c544fc2088161792d4811d60ba988265d76b476d3857ab716b6c603`。此设备包不是公开生产签名包。

安装前备份旧 APK、包信息和前台状态；`adb install -r --no-incremental` 成功。回拉已安装 APK，哈希与上述修复包完全一致；版本 0.5.0/9，DEBUGGABLE 未设置。没有卸载或清除数据。

手机当时正在使用另一个 App，故仅后台安装；前台 Activity 前后逐字一致，没有触控或拉起 Train Trip。按钮交互由模拟器验证，目标网址由真实 HTTP 验证，本轮未在真机点击更新说明或打开浏览器。未重读私有目录，也不声称逐文件验证收藏和配置。

## 证据与回滚

- `.verification-private/v0.5.0/release-notes-fix/`：HTTP 复现、修改前备份、红绿测试日志、签名和 APK 扫描结果。
- `phone/verification.json`：真机安装哈希与前台保留检查；`phone/before.apk`：原收藏修复包，可同签名保留数据覆盖回滚。
- 源码使用本次修复提交的反向提交回滚，存储格式没有变化。未创建 GitHub Release、发布 APK 或更改版本号。
