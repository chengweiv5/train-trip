# 蓝白 UI 实现验收

日期：2026-09-19。版本：0.4.0 / 8。依据用户确认的 `design/train-trip-blue-white.pen` 17 张画板实现。

## 实现

- 首页、城市结果、车次、目的地四分页、设置及筛选界面统一为蓝白出行工具风格。主蓝 `#1769E8`，页面灰 `#F5F7FA`，白色卡片、中文导航和紧凑信息层级；绿色只表达有票或成功。
- 车次整卡标记、席别只读、独立更多席别和无需标记即可打开 12306 App 保留。城市按省分组、查询停止/继续/重试和目的地来源/更新入口保留。
- 日期与席别使用全屏面板；首页长日期在大字体下纵排。Material 日历在小于 360dp 时缩放网格几何尺寸，同时补偿字体缩放，保持文字物理大小，避免末列被裁切。
- 排序与刷新保留用户确认的对称缩进：页面 16dp + 按钮内部 16dp。带文字操作的导航栏左右等宽 64dp，保证“修改”不在 1.3 字号下换行。
- DeepSeek/Tavily 独立配置、密钥加密与安全窗口保留。未修改网络、缓存、查询或生成逻辑。

## 验证

| 项目 | 结果 |
| --- | --- |
| Gradle | `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 成功 |
| Core | 59 项通过，0 失败 |
| Lint | 0 错误，19 条既有警告 |
| Android 仪器测试 | 本轮累计 65 个独立用例，最新一次结果均通过；按失败项目定向复测，非单次全量运行通过 |
| 窄屏 | 320dp、1.3 字号下 5 项蓝白界面用例全部通过；日期跨月/取消/恢复、车次时间分行通过 |
| 大字体日期 | 31 天跨月范围、2.0 字号：两端日期完整、查询入口可达 |
| 最终常规宽度 | 390dp 下导航/整卡标记/席别只读/日期面板再次通过 |
| 视觉 | 首页、结果、车次、四个目的地分页、设置、日期/席别截图和 OCR 核对；真实 MainActivity 系统栏显示正确 |
| 独立代码复核 | 规范与规格复核通过；长日期挤压已修复，排序边距规格已明确 |
| 凭证扫描 | APK 0 命中；源码仅命中两份已有测试文件中的明确测试假值 |

初轮 64 项有 14 项失败，主要是改版后的文字定位与测试隔离问题；全部已定向复测通过。窄屏验收发现并修复日历末列裁切及导航操作换行。旧时间布局用例已兼容宽/窄屏分行。真实 12306 查询与刷新用例在本轮早期运行通过；最终布局复测使用离线数据，没有调用付费模型或搜索服务。

原始测试结果与各用例最新状态保存在 `.verification-private/v0.4.0/blue-white-implementation/test-summary.json`。构建日志为 `build-narrow-final.log`，最终界面日志为 `narrow-final-320.log` 和 `normal-final-390.log`；截图在同目录 `fixed-320/`、`verified-390/`，OCR 为 `ocr-final.json`。

## 真机交付

- Mate 60 Pro（ALN-AL00）通过 `adb install -r` 后台覆盖安装成功。
- 手机 APK SHA256 与本地产物一致：`a4a8c9eaafd39c56015646563dd0d3d5b502d1fe723579326d6e855e6445a778`。
- 回读确认：筛选偏好、DeepSeek 密文、Tavily 密文、泰安缓存四份文件指纹均未变化，前台应用保持不变。
- 产物：`artifacts/train-trip-blue-white-personal-debug.apk`。APK、旧包及私有证据不纳入 Git。
- 安装证明：`.verification-private/v0.4.0/blue-white-implementation/phone-verification.json`。

## 回滚

源码以本次实现提交的反向提交回滚，保留其他提交。原始源码备份位于私有证据目录 `src-before/`。

真机可使用 `.verification-private/v0.4.0/blue-white-implementation/before.apk` 执行保留数据覆盖安装；无需卸载或清除数据。
