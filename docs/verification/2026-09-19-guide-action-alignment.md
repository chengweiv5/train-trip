# 目的地更新入口左对齐验收

## 改动

`GuideRuntimeStatus` 中 `refresh-guide` 文字按钮移除水平内边距，保留 12dp 垂直内边距并明确最小高度 48dp。「更新目的地介绍」「整理目的地介绍」「重试整理」共用这一布局；文案、颜色、状态、回调均保持不变。

## 已验证

- GitHub 待合并 PR 为空，设计规格独立审查通过。
- `:app:assembleDebug :app:lintDebug` 构建通过。lint 0 错误、19 警告，修改文件无告警；其余为已有文件的依赖更新、KTX、Modifier 和备份策略建议。
- Mate 60 Pro 已保留数据覆盖安装，APK 与本地产物 SHA-256 一致：`e8e2c0604f3e72d686dd84b519fbc2004d234f34bb6059a6bf49550b3fb69cc8`。
- 筛选条件、DeepSeek/Tavily 密文配置、泰安缓存 SHA-256 与更新前完全一致。没有点击重新整理，没有搜索或模型调用。
- APK 真实格式密钥扫描 0 命中。

## 视觉测量

手机在验证时切换到其他应用，已停止触控。后续在专用模拟器 emulator-5582 补完实际布局测量：整理日期、更新文字、资料来源的左边界均为 55px，按钮高度 48dp，最终截图复核通过。使用无效测试占位 Key 与已有缓存，未点击更新或发起搜索/模型请求。证据为私有目录中的 emulator-after-ui.xml、emulator-layout.json 和 emulator-after.jpg。真机已安装相同实现，未中断用户补做截图。

## 产物与回滚

- 新 APK：`artifacts/train-trip-guide-alignment-personal-debug.apk`，版本 0.4.0 / 8。
- 私有证据：`.verification-private/v0.4.0/guide-action-alignment/`。
- 原源码 `DestinationGuideScreen.before.kt`、旧 APK `before.apk` 保存在证据目录；可使用旧 APK `adb install -r` 回滚，不卸载或清除数据。
- 个人自用版本仅在功能分支本地提交。
