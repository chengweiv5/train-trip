# 12306 候补接口核对

核对日期：2026-09-29。范围为官网当前实际引用的 JavaScript、HTML 和一次匿名余票 GET 查询。没有调用候补提交、确认或支付接口，没有读取用户账号、Cookie 或真实乘客资料。不是正式开放 API 文档，也没有对 12306 App 私有协议作结论。

后续补充：用户确认拒绝出现在选择乘车人后、点击提交订单时；已另外核对官方 Android 下载包内置 H5，见 [Android 提交链路报告](2026-09-29-waitlist-android-investigation.md)。以下保留网页范围的原始证据。

## 结论

**提前识别被官方标记为“候补订单较多”的席别，不需要真实乘客信息。确认指定 N 人一定能提交候补，目前没有找到独立、无下单副作用的官方网页接口。官网正式提交链路需要登录及真实乘客信息。**

不能用“没有限制标记”或“有预估成功率”作为“人数校验通过”的证据。此前设计中的按 N 人绿色通过态属于待验证提案，不能直接进入实现。

## 接口与参数

所有路径均相对于 `https://kyfw.12306.cn/otn/`。

| 环节 | 接口 / 字段 | 当前证据 | 能说明什么 |
| --- | --- | --- | --- |
| 余票及候补标记 | 当前入口 `leftTicket/queryG`；`houbu_train_flag`、`houbu_seat_limit` | 匿名 GET 实际成功，无乘客参数 | 支持候补及哪些席别被标记为候补订单较多，不含 N 人容量保证 |
| 进入候补流程 | `login/checkUser` → `afterNate/submitOrderRequest`，携带 `secretList` | 官网脚本明确先检查登录，再进入 `lineUp_toPay.html` | 进入带会话的候补填写流程，不能仅因未带乘客数据就当成纯查询 |
| 初始化填写页 | `confirmPassenger/getPassengerDTOs`、`afterNate/passengerInitApi` | 页面拉取乘客列表、候补车次上下文、验证配置与人数上限 | 依赖当前流程上下文，返回值不是匿名人数校验 |
| 候补统计 | `afterNate/getQueueNum` | 请求未显式传人数；页面展示 `queue_info`、`queue_all_num`、`queue_realize_num` | 候补统计，不能据此判断指定 N 人可提交 |
| 预估兑现率 | `afterNate/querySuccessRate` | 显式参数为 `plans`、`realize_limit_time_diff`、`add_train_flag`、`passenger_num`；页面将 `data.result` 渲染为“预估成功率” | 参数无需附带姓名/证件，但属于概率估计，非提交资格或容量通过标记；未实测匿名调用资格 |
| 正式提交 | `afterNate/confirmHB` | 发送 `passengerInfo`、候补车次、兑现截止条件、验证字段等 | 有真实订单副作用的提交步骤，不能用于静默批量探测 |
| 提交后查询 | `afterNate/queryQueue` | 在 `confirmHB` 后调用；页面文案“候补订单已经提交，系统正在处理中” | 已提交请求的处理状态，不是提交前容量检查 |

### 真实乘客信息如何进入 confirmHB

当前 HTML 的乘客模板含：

```text
passenger_type
passenger_name
passenger_id_type_code
passenger_id_no
allEncStr
```

活动 bundle 从已选乘客组装 `passengerInfo`。单人结构为以下语义顺序，多人用分号拼接（其他规则字段依当前版本变化）：

```text
票种 # 姓名 # 证件类型 # 证件号码 # allEncStr # 老年卧铺相关标志
```

页面没有已选乘客时先提示“请选择乘车人”。因此不能拿一个人数整数替代该提交接口的乘客数据，也不能用占位身份试探并宣称等价于真实提交。

### querySuccessRate 为什么不能替代提交校验

当前实际 bundle 中，选择/取消乘客后更新 `passenger_num`，然后调用 `getSuccessRate()`。该函数仅把响应中的 `result` 拼到“提交订单 / 预估成功率”按钮上；异常时显示 `--`。没有据此输出“可以提交 / 候补已满”的判断。

这也说明“添加乘客之后才弹错”还不足以精确定位报错接口，可能发生在提交步骤或其后的异步结果。要证明用户所见那条报错来自哪个响应，仍需要一次真实界面操作的请求时序证据。当前没有复现该报错。

## 已实测的匿名筛选能力

GET 查询条件：2026-10-01，北京南 `VNP` → 济南西 `JGK`，`purpose_codes=ADULT`。入口路由从当次官方 `leftTicket/init` 动态读取，未固定猜测 `queryG`。

响应 HTTP 200、业务 `status=true`，共 145 条记录，其中精确起终站为 VNP → JGK 的记录 108 条；16 条的 `houbu_seat_limit` 含二等座代码 `O`。返回含同城站结果，计数已区分准确区间。

| 车次 | 二等座票态 | 车次候补标记 | 席别限制标记 | 本次可作的判断 |
| --- | --- | --- | --- | --- |
| G37 | 无 | 1 | `9MO` | 二等座 O 被标记为候补订单较多 |
| G39 | 无 | 1 | `9MOD` | 二等座 O 被标记为候补订单较多 |
| G981 | 无 | 1 | `M` | 一等座 M 有标记，不能因此过滤二等座 |
| G547 | 无 | 1 | 空 | 未标记候补过多，不能推断 N 人可提交 |

以上均为 2026-09-29 当次查询快照，不保证后续状态不变。标记不是剩余人数，无法计算“还可以候补几个人”。

官方查询脚本的字段映射为零基下标 `37 → houbu_train_flag`、`38 → houbu_seat_limit`。当席别代码命中限制字段时，官方展示：

> 当前…车次…席别提交的候补订单较多，可更换车次、席别或稍后重试。

现有项目 `TicketParser.kt` 只读取下标 37，未保留下标 38。这是可直接补齐的初筛信息，但不能达到原先“按人数验证可提交”的完整目标。

### 用户截图的灰色 / 蓝色候补核对

用户随后提供洛阳→北京、10月7日的查询截图，圈出 D30 二等灰色候补和 G374 二等蓝色候补。截图未显示年份，本轮按当前年份 2026 查询，不能将新的响应视为截图当时的响应。

2026-09-29 20:57:35 +08:00 匿名查询结果与截图的限制差异吻合：D30（洛阳→北京西）下标 37 为 `1`、下标 38 为 `O`；G374（洛阳龙门→北京西）下标 37 为 `1`、下标 38 为空。Z180、K4238 的下标 38 均为 `4`（软卧），也与截图软卧灰色一致。

因此图中这类入口状态可通过匿名余票返回做初筛，不需要乘客信息。G374 未标记二等座限制仍不构成指定 N 人提交保证；截图没有乘客选择页或提交报错，不能据此定位后续拒绝接口。精简结果见 [screenshot-comparison.json](waitlist-api/screenshot-comparison.json)。

## 对 v2.0 的影响

1. 可落地的第一层：无需乘客信息，显示“候补订单较多 / 未标记拥挤 / 未确认”，按准确区间和席别初筛。不得把“未标记拥挤”命名为“探测通过”。
2. 人数输入如果只驱动 `querySuccessRate`，应明确其用途为预估兑现率，还需要候补计划与截止兑现时间；它不是容量检测。
3. 原目标仍有待验证部分：当前未找到只传人数即可确认“不会报候补人数过多”的纯查询接口。不能把 `confirmHB` 包装成无副作用探测，即使不支付也已经进入提交链路。
4. 若进一步分析 App 与网页是否不同，应使用用户正常操作产生的、去除认证和乘客信息后的请求时序与响应样例，定位具体拒绝环节，再决定是否需要修改范围。无需先向用户收集身份证信息。

## 官方来源

- [查询页](https://kyfw.12306.cn/otn/leftTicket/init)
- [查询页当前脚本 1.95053](https://kyfw.12306.cn/otn/resources/merged/queryLeftTicket_end_js.js?scriptVersion=1.95053)
- [候补填写页](https://kyfw.12306.cn/otn/view/lineUp_toPay.html)
- [填写页实际引用 bundle main_v90003](https://kyfw.12306.cn/otn/personalJS/dist/lineUp_toPay/main_v90003.js)

官网同时可下载未压缩 `toPay_init.js`，但其字段和活动 bundle 不完全相同，本报告以 HTML 当前引用的生产 bundle 为准，未把旧开发文件当作现行协议。

抓取时间、公开静态资源哈希与精简查询记录见 [source-manifest.json](waitlist-api/source-manifest.json)。未保存登录 Cookie、令牌、乘客信息或查询返回中的 `secretStr`；没有将整份官网 bundle 纳入仓库。
