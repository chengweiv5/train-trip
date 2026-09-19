# 车次页紧凑布局执行计划

目标：三个日期在普通字号同一行完整显示，长日期列表可横滑；减少卡片高度并保留点击面积和信息。

- [x] 在独立模拟器以相同三日期、两席别数据记录原日期坐标与卡片高度。
- [x] 修改 DetailScreen.kt：日期 Row + horizontalScroll，紧凑月日标签；卡片 padding(12dp,10dp)、gap 6dp、列表 gap 10dp；顶部增加出发日期，底部合并查询时间与更多席别入口。
- [x] 修改 DetailActions.kt 的 SeatChoice：水平 padding 8dp、垂直 6dp，保留 48dp 最小高度、选中图标和 RadioButton 语义；移除详情页按字体/宽度强制每行一个席别的规则。
- [x] 在 DetailLayoutTest.kt 验证正常宽度三个日期、不溢出、卡片高度；320dp / 1.3 字体的 31 日期滑动与选择、跨日显示和余票详情。
- [x] 更新现有 DetailFlowTest 日期定位为稳定标签，运行详情回归；执行 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline。
- [x] 视觉检查测试截图、记录实际前后高度及验证结果。只在测试 APK 中使用离线数据。

全局约束：新功能分支开发；不覆盖其他任务工作区或真机新版；不操作共享 Pencil；保留原测试及备份；不对线上票务发起测试查询。
