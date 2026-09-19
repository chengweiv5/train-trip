# 结果页排序与刷新对称留白验收

上版排序文字贴齐页面左边界，刷新文字却有按钮内边距，两端并不对称。本版两个 TextButton 共用左右 16dp、上下 12dp 内边距，显式保留至少 48dp 点击高度；排序仍靠左，刷新仍靠右。两种排序文案和原回调、禁用条件不变。

## 验证

- 无待合并 PR；规格独立审查 Approved。
- `:app:assembleDebug :app:lintDebug` 通过，lint 0 错误、19 条既有文件警告。APK 密钥格式扫描 0 命中。
- 专用模拟器 390dp、320dp 各核对两种排序状态，共四张截图。实际点击切换正常。
- 四种状态中，排序文字距操作行左边界和刷新文字距右边界均为 44px（16dp），距屏幕边缘均为 99px（36dp），两端差为 0px；两按钮点击高度均为 48dp，文字上下边界一致。
- 最终截图、UI bounds 与 OCR 已复核，操作行无重叠或裁剪。未调用大模型或搜索 API；专用模拟器已关闭。
- Mate 60 Pro 保留数据后台覆盖安装成功；真机 APK SHA-256 与构建产物一致，筛选、两项密文配置和泰安缓存哈希不变，前台应用保持不变。

## 交付与回滚

版本 0.4.0 / 8。APK：`artifacts/train-trip-action-symmetry-personal-debug.apk`。

SHA-256：`e54db96d03471fc382829ac684ae50ddbd0f5d04274425556f3fcf68d7290275`。

个人版本仅功能分支本地提交。证据保存在 `.verification-private/v0.4.0/results-action-symmetry/`，包含 build.log、lint.xml、layout.json、OCR、截图和 phone-verification.json。源码备份 ResultsScreen.before.kt；旧 APK before.apk 可通过 `adb install -r` 保留数据覆盖回滚。
