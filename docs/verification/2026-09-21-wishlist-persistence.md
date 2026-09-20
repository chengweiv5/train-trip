# 想去清单偶发空白修复

2026-09-21，分支 `codex/v0.5.0-design-options`。

## 用户现象与证据边界

用户添加保定、承德后，回到首页再进入“想去”看到“先收藏一座想去的城市”；重新进入 App 后两座城市又出现。用户不确定最初是否点击底部添加按钮。

已在真实 MainActivity、默认 ViewModel 作用域及 AndroidWishlistStore 文件存储下复现同类现象：原 Activity 先读取空清单，第二个 Activity 保存两座城市后退出，返回原 Activity 并切换栏目，磁盘记录存在而原页面仍显示空状态。修复前该用例稳定失败。正常单 Activity 添加后切换、Activity 重建及前后台切换原本通过。

这证明存在旧页面快照未刷新的缺陷，但没有证明用户真机当时确实出现了两个 Activity。USB 能识别 ALN-AL00，ADB 仍只列出本任务模拟器，未取得有效真机截图。本轮没有触控、安装、卸载或清理真机。

## 修复

- Activity 回到前台、进入“想去”或添加城市时重新读取清单；读取期间保留已显示城市，避免刷新时清空页面。
- 添加、取消和撤销操作基于最新磁盘记录完成，避免旧 Repository 覆盖其他页面刚保存的城市。
- 同一文件的多个 AndroidWishlistStore 实例共用进程内锁，将读取、变更和写入串行执行；文件读写仍使用 AtomicFile。存储失败不将旧记录替换为空清单。
- 收藏切换在同一次事务中判断当前是否已收藏，防止根据过期内存做反向操作。

数据格式与版本保持 0.5.0 / versionCode 9。本次是本地修复候选，没有发布新版本或推送。

## 验证

- 修复前：Android 3 项中 1 项失败，旧 Activity 显示空清单；core 3 项中 1 项失败，旧 Repository 覆盖其他页面新增记录。
- 修复后：78 项 core 测试全部通过；26 项 Android 回归全部通过，覆盖真实页面生命周期、进入栏目刷新、12 个存储实例并发添加/移除、损坏文件不覆盖、旧快照增删及撤销、保存失败重试，以及离线管理和设置页面。
- Debug、Android 测试 APK、Release 构建成功；lintRelease 0 错误、19 条既有警告；git diff --check 通过。
- 在本任务专用模拟器上，将含保定/承德的 Debug 安装覆盖为 Release 修复包；冷启动、切换栏目及进程重启后均显示两城，没有空状态。界面截图和 UI 树已核对。
- 回拉模拟器已安装 APK，SHA256 与本地修复包一致；v2/v3 签名通过；DEBUGGABLE 未设置，run-as 返回 package not debuggable。
- APK 服务 Key 格式扫描 0 命中，未包含私有收藏、配置或签名文件；没有调用付费生成/搜索服务。

构建命令：

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintRelease
```

模拟器回归类：WishlistPersistenceTest、AndroidWishlistStoreTest、WishlistFlowTest、OfflineManagementTest、SettingsMenuTest。

## 本地产物与回滚

- 修复包：`artifacts/train-trip-v0.5.0-wishlist-fix-device-release.apk`。
- SHA256：`2853c9583a14f6bfcaf945e9fe221de21c6c6be39eaa2f1d3b62898c8d2cc782`。
- 8,886,697 字节；0.5.0 / 9；Release 构建，使用原手机的历史 Debug 证书以支持保留数据覆盖安装；不是公开发布所用的生产签名。
- 设备证书 SHA256：`cae9ed8a5c544fc2088161792d4811d60ba988265d76b476d3857ab716b6c603`。
- 旧生产包与设备包均未改写，修复包尚未安装到真机，也未上传 GitHub。
- 证据及修改前源文件：`.verification-private/v0.5.0/wishlist-fix/`。可信修前日志为 `red.log`、`core-red.log`；最终日志为 `final-build.log`、`final-android.log`。`repro-expanded.log` 含早期测试自身的文件读取竞态，不用作生产缺陷证据。
- 源码可用本次提交的反向提交回滚；收藏文件格式未变。真机应用本轮未改动，无需回退。后续若覆盖升级，不卸载或清除个人数据。

## 尚待真机验证

用户已重新连接手机，宿主 USB 枚举可见设备，但 ADB 未出现设备或 unauthorized 条目。已请用户确认 USB 调试及电脑授权。连接后先只读取当前截图和页面状态，再结合真机现象判断；不能将上述模拟器结果写成真机根因已证实。
