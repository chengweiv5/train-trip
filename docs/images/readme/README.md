# README 截图说明

- 截图版本：v0.6.1 / versionCode 11，源码提交 `e68e35e`，应用名称「有票再出发」。
- 截图日期：2026-09-21；Android 专用模拟器，1080 × 2400、420 dpi、标准字号。
- 图片来自现有 Compose 界面测试；只缩小尺寸并压缩为 JPEG，没有替换文案、合成余票或改动界面。文件最长边 1280 px，可从项目首页点击查看。
- 日期、车次、余票及收藏是离线测试示例，不是实时查询，也不是用户个人数据。本次没有调用真实票源、DeepSeek 或 Tavily。
- 当前公开正式版为 v0.6.0，名称仍为「有票就出发」。v0.6.1 的新名称、按省分组的收藏、统一子页顶部与相册样式尚未发布。

| 图片 | 对应测试场景 |
| --- | --- |
| [home.jpg](home.jpg) | `BlueWhiteUiTest.homeAndSettingsKeepActionsReachable` |
| [results.jpg](results.jpg) | `BlueWhiteUiTest.resultsAndTrainSelectionHaveClearSeparateActions` |
| [trains.jpg](trains.jpg) | 同上，未标记意向车次的状态 |
| [guide.jpg](guide.jpg) | `BlueWhiteUiTest.guideTabsAndFootersStayReachable`，天津内置介绍 |
| [wishlist.jpg](wishlist.jpg) | `WishlistFlowTest.multiSelectPersistsAcrossSearchAndUndoRestoresOriginalOrder`；清理专用模拟器历史测试缓存后重新截图 |
| [destinations.jpg](destinations.jpg) | `DestinationWishlistTest.pinnedWishesShareSelectionWithProvincesAndKeepRealProvinceCount` |

上述 5 个不同测试均通过；其中「想去」场景在清理测试缓存后复测通过。6 张 JPEG 共约 593 KiB，文字 OCR、边界与人工视觉检查通过；GitHub GFM 渲染保留图片、图注、表格和折叠开发说明，文档内 45 处本地链接均已检查。截图不构成对真机界面、实时车票或内容服务的验收。

天津古文化街照片沿用应用内已注明的来源：摄影胡凌云／天津日报，[天津市文化和旅游局刊载页面](https://whly.tj.gov.cn/tjswlzxw/wlsj/mtjj/202404/t20240419_6605163.html)。图片权利归原作者及相关权利人所有。
