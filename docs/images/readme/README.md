# README 截图说明

- 截图版本：v0.6.1 / versionCode 11，应用名称「有票再出发」。其余五图来自源码 `e68e35e`；`wishlist.jpg` 更新于 2026-09-22，对应本次省份双栏实现。
- 截图日期：其余五图为 2026-09-21，想去页为 2026-09-22；Android 专用模拟器，1080 × 2400、420 dpi、标准字号。想去页在 390dp 容器中捕获。
- 图片来自现有 Compose 界面测试；只缩小尺寸并压缩为 JPEG，没有替换文案、合成余票或改动界面。文件最长边 1280 px，可从项目首页点击查看。
- 日期、车次、余票及收藏是离线测试示例，不是实时查询，也不是用户个人数据。本次没有调用真实票源、DeepSeek 或 Tavily。
- 上述功能随 v0.7.0 正式发布；截图保留实际采集时的 v0.6.1 版本来源。想去页截图为加入「全部」入口前的省份视图，v0.7.0 会在省份上方另有固定「全部」入口。

| 图片 | 对应测试场景 |
| --- | --- |
| [home.jpg](home.jpg) | `BlueWhiteUiTest.homeAndSettingsKeepActionsReachable` |
| [results.jpg](results.jpg) | `BlueWhiteUiTest.resultsAndTrainSelectionHaveClearSeparateActions` |
| [trains.jpg](trains.jpg) | 同上，未标记意向车次的状态 |
| [guide.jpg](guide.jpg) | `BlueWhiteUiTest.guideTabsAndFootersStayReachable`，天津内置介绍 |
| [wishlist.jpg](wishlist.jpg) | `WishlistSidebarTest.onlySavedProvincesShowTheirOwnCitiesInCollectionOrder`；河北选中，11 个城市 / 5 个省级地区，离线演示数据 |
| [destinations.jpg](destinations.jpg) | `DestinationWishlistTest.pinnedWishesShareSelectionWithProvincesAndKeepRealProvinceCount` |

初次交付的 5 个测试及文档内 45 处本地链接已检查。此次替换想去截图，双栏场景通过测试，文字边界、OCR 与人工视觉检查通过；其余图片未更新。截图不构成对真机界面、实时车票或内容服务的验收。

天津古文化街照片沿用应用内已注明的来源：摄影胡凌云／天津日报，[天津市文化和旅游局刊载页面](https://whly.tj.gov.cn/tjswlzxw/wlsj/mtjj/202404/t20240419_6605163.html)。图片权利归原作者及相关权利人所有。
