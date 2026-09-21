# Wish Destinations Implementation Plan

> 当前隔离工作树直接执行；按brainstorming进行独立规格审阅，执行类子技能未安装。

**Goal:** 在目的地省份首位提供“想去”快捷分组，统一城市勾选与收藏数据。

**Architecture:** WishlistState经TrainTripApp/FiltersScreen传入DestinationSelector，视图用独立分组键切换收藏列表，继续用destinationCityIds保存草稿。

**Tech Stack:** Kotlin、Compose、现有WishlistViewModel、Android离线测试。

## Global Constraints

规格docs/superpowers/specs/2026-09-21-wish-destinations-design.md。0.6.0/10，B4风格；不改核心行政目录/偏好/收藏格式，不联网、不安装、不推送。

## Task 1: 对接与快捷分组

Files: ui/TrainTripApp.kt、ui/FiltersScreen.kt、ui/DestinationSelector.kt；新增ui/DestinationWishList.kt。

- [x] FiltersScreen/DestinationSelector新增末尾参数`wishlist:WishlistState=WishlistState(loading=false),onReloadWishlist:()->Unit={}`。TrainTripApp首页/单城传`wish,wishlistVm::reload`，FiltersScreen在scope分派传入；Selector打开时LaunchedEffect(Unit)调用回调。
- [x] 用`WISH_DESTINATION_GROUP="wishlist"`保存虚拟分组，`showWishes=browser.provinceId==WISH_DESTINATION_GROUP`；真实省份fallback保留，想去不修改行政目录。初始仅未设置分组时恢复原省份。
- [x] 左栏Column顶部独立想去行、下方原LazyColumn；紧凑下拉第一个DropdownMenuItem为想去。同组列表按`items.distinctBy(cityId).sortedByDescending(addedAt)`；全选只对catalog映射中selectable(city)的ID增删。
- [x] 想去右列使用现有DestinationCityRow并显示省份，未知城市使用禁用行/原因。状态优先加载无数据、错误、空、列表；刷新中旧列表禁用选择并显示读取提示。全选仅本组可用城市、禁止空或未就绪时操作。
- [x] 搜索/已选视图不变，分组切换不动selected；cityId跨分组复用。保留默认恢复、取消/完成和真实省份数量。

## Task 2: 验证、设计、提交

Files: 新增DestinationWishlistTest.kt、修改WishlistFlowTest.kt增加真实接线测试；design/train-trip-surface-contrast.pen、README与验收。

- [x] 新增4–5项组件测试：有收藏默认非自动选中、跨省同步/全选和禁用；状态/重试/实时更新；取消及恢复；大字体下拉第一项。每项断言实际已选集合/勾选状态，覆盖未知收藏与跨省计数。
- [x] WishlistFlowTest增加主页选择器接线，注入离线WishlistStore、添加收藏、打开查询目的地、点想去选城市，断言AppViewModel和Preferences更新且无票源调用。
- [x] JDK21构建Debug/Android测试/Release和lint；emulator-5586离线运行新测试、4项现有目的地选择器及收藏持久化相关回归；320dp/1.3字体截图与文字溢出检查。
- [x] Pencil新增10画板普通列表/空状态，保留B4；结构检查、截图/OCR、原生保存与目标节点回读。若用户切换其他项目停止原生操作，记录保存验证范围。
- [x] 更新README、设计说明、验收、TASK_STATE；关闭专用模拟器，本地提交本轮文件，punk-12通知回读。

自检：分组入口/数据/选择语义→Task1；加载失败空/排序/禁用/恢复/真实接线/布局→Task2。沿用已有持久化，不新增服务器或请求。
