# 结果页城市排序文字左对齐验收

## 改动

排序按钮继续占据刷新按钮左侧剩余宽度，将内部 Text 设为 fillMaxWidth 和 Start 对齐。两种文案都从内容左边线开始；刷新按钮位置、禁用状态、排序回调及分组行为保持不变。

## 验证

- GitHub 无待合并 PR；规格独立审查通过。
- `:app:assembleDebug :app:lintDebug` 通过；lint 0 错误、19 条既有文件警告。
- 专用模拟器 `emulator-5582`：390dp、320dp 两种宽度检查通过。「车程最短」与「车次数最多」点击切换正常；两种状态排序和页面标题左边界均为 55px，刷新保持右侧，按钮点击高度 48dp。
- 最终截图和 OCR 复核通过，无重叠或裁剪。证据在 `.verification-private/v0.4.0/results-sort-alignment/`。
- Mate 60 Pro 已后台覆盖安装，未切换用户前台应用。安装包 SHA-256 与本地相同：`b8f601b2d35673cd4a8401eb312a5c601c767159b2b67b2b3b8d468e4a875310`。
- 真机筛选条件、DeepSeek/Tavily 密文配置、泰安缓存的 SHA-256 均与安装前相同。未触发内容生成。APK 密钥格式扫描 0 命中。

## 交付与回滚

APK：`artifacts/train-trip-sort-alignment-personal-debug.apk`，版本 0.4.0 / 8。个人自用版本仅功能分支本地提交。

源码备份 `ResultsScreen.before.kt`、旧安装包 `before.apk` 位于私有证据目录；旧 APK 可 `adb install -r` 覆盖回滚，不卸载或清数据。
