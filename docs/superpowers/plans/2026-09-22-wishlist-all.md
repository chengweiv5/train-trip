# 想去全部入口实施计划

1. 独立表示全部浏览状态，保留省份选择和各自滚动位置。添加固定入口、总数、全部城市列表与紧凑菜单。
2. 更新省份测试默认选择假设；验证跨省收藏合并排序、移除/撤销、菜单、全部及省份滚动恢复；沿用现有持久化回归。
3. 同步 Pencil 全部入口及全部选中状态，核验布局和保存。构建并在独立模拟器验证离线相关测试与截图。
4. 本地提交并通知；版本仍 0.6.1 / 11，安装/推送按后续指令。回滚使用本次提交的反向提交，收藏格式不变。

按用户明确指定的增量直接实施，沿用已经确认的设计。

涉及文件：`WishlistBrowserState.kt` 增加全部范围；`WishlistScreen.kt` 增加固定行、全部数据和省份文案；`WishlistSidebarTest.kt` 验证排序、计数和恢复。复用既有空/错误状态与收藏存储。规格复核已通过。

验证：`assembleDebug`、`assembleDebugAndroidTest`、`assembleRelease` 和 `lintRelease` 通过，lint 0 error / 19 既有 warning。独立模拟器运行 WishlistSidebarTest（7）、WishlistFlowTest（8）、WishlistPersistenceTest（4）、B4UiTest（4）、DestinationWishlistTest（5），共 28 项全部通过。普通和 320dp / 1.3 字号截图完成，文字边界及触控检查通过。日志在 `.verification-private/wishlist-all/`。

自审：全部使用空省份键保存状态，与真实省名隔离；原先保存的省份名继续恢复。顶部真实省份计数来自原分组；全部城市来自原收藏按时间排序，未额外复制收藏数据。全部入口固定、左省份滚动保留、右城市列表独立滚动均已覆盖。

设计同步已完成：Pencil 的 12A / 12B 添加全部入口，12C `g3543u` 展示全部选中；三张页面及说明区结构检查无裁切，视觉检查和原生保存回读通过。未安装真机或推送远端。代码、设计和验证记录一并本地提交。
