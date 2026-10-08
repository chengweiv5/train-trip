# v1.1 城市选择校验时机修复

日期：2026-10-08，Asia/Shanghai。

## 确认的行为

- 修改出发城市或选择目的地时，允许暂时同城；不自动移除同名目的地，不在选择阶段报同城冲突。
- 点击查票时才提示。无目的地或仅选中出发城市时，在页面跳转、车票源初始化和查询请求之前返回错误。
- 多目的地包含出发城市时，保留用户的选择，仅查询其他城市；结果分组不把被跳过的出发城市显示成“查询尚未开始”。
- 单城市查票入口继续固定目的地，冲突时提示修改出发地，不污染首页条件。
- 重启后保留已保存的同城选择；取消编辑不修改已保存条件。

## 原因与修复

1. 出发地编辑回调原先同时从目的地集合移除新出发城市；唯一目的地因此变空，点“完成”又被目的地非空校验拦截。
2. 普通目的地和想去清单原先直接禁用出发城市。现在只禁用目录不支持的城市，保留加载、错误和未知城市的既有限制。
3. 同城与目的地非空校验移至 `AppViewModel.search`。其他日期、席别、人数等既有校验未改变。
4. `StationCatalog.normalize` 只归一化身份，不删除同城草稿。`plan` 继续跳过同城，`groupResults` 同步按异城范围展示。

## 红绿验证

修改前实际运行并捕获了以下失败，修复后同一行为测试通过：

- 出发地改成唯一目的地后，点“完成”仍留在城市选择面板。
- 普通目的地和想去清单中的出发城市仍为禁用状态。
- 保存仅同城目的地后，新建 ViewModel 会把它替换为默认目的地集合。
- 查询北京、天津时，结果分组错误地包含未查询的北京。

最终验证：

| 检查 | 结果 |
|---|---|
| core 单元测试 | 206 项通过，0 失败/错误/跳过 |
| Android JVM 单元测试 | 22 项通过，0 失败/错误/跳过 |
| 隔离模拟器 UI 回归 | 30 项通过，120.465 秒 |
| Debug APK、测试 APK | 构建通过 |
| lintDebug | 0 Error / 0 Fatal，20 Warning |
| Debug APK 签名 | 验证通过 |
| `git diff --check` | 通过 |

UI 回归包括 `CityValidationTest` 的 8 项新增场景，以及目的地搜索/省份选择/收藏、结果分组、单城市草稿隔离、旧设置迁移、出发时间和查询失败重试。查询使用注入的本地 `TicketSource`，无真实车票查询或订单操作。

最终 Debug APK SHA-256：

```text
07be5019958831705bf719e485a2000b88b48bc13114f58724b044bcae4cceb6
```

## 复验与边界

构建与静态验证：

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

在专用模拟器上安装两个 Debug APK 后，UI 回归命令：

```sh
adb -s emulator-5580 shell am instrument -w -r \
  -e class 'cn.traintrip.app.CityValidationTest,cn.traintrip.app.DestinationUiTest,cn.traintrip.app.DestinationWishlistTest,cn.traintrip.app.WishlistFlowTest#singleCityDraftAndGuideReturnPreserveHomeConditionsWithoutAutomaticRequests,cn.traintrip.app.WishlistFlowTest#homeDestinationSelectorReadsWishlistAndSavesOnlyQuerySelection,cn.traintrip.app.SearchFailureTest,cn.traintrip.app.DepartureTimeUiTest' \
  cn.traintrip.app.test/androidx.test.runner.AndroidJUnitRunner
```

- 开发在功能分支 `codex/v1.1-city-validation`，修改前已确认远端没有待合并 PR。
- 本轮仅本地提交，不推送、不打标签、不发布版本，不安装或修改已连接手机上的应用。
- 未调整版本号：测试包仍为 `1.0.0 / 15`。标题中的 v1.1 是用户提出的修复需求，不表示已经发布 v1.1。
- 原始备份及验证日志位于本工作树的 `.verification-private/city-validation/`，不提交生成物。

## 回滚

基线提交为 `dc9e5b7c282968f5ad50f0f4154fd5c8a912172b`。本次变更单独提交；需要撤销时，在功能分支创建该修复提交的反向提交，并重新执行以上验证。不要 reset、强推或清空应用数据。基线另有经过 gzip 完整性验证的 `baseline.tar.gz` 备份。
