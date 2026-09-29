# Android 候补提交能力核对

2026-09-29。用户已明确：目标是蓝色候补入口下，选择 5 位乘车人后，点击提交订单是否会被“候补过多”拒绝。不是灰色入口过滤，也不是候补兑现率预测。

## 结论

在官方 Android 下载包内置的候补流程中，没有找到只传人数即可预先确认提交资格、且不进入订单提交的接口。正式提交 `afterNate.confirmOrder` 使用真实已选乘客的加密标识，人数 `alternateTicketNo` 只是附加参数，不能替代乘客信息。

该接口成功后可能直接返回待支付订单，或者进入异步处理后返回订单。不能以“不支付”为由将正式提交视为无副作用探测。原先“只填 5 人 → 批量显示哪些蓝色车次确定可提交”的方案尚未得到接口支持证据。

## 来源及适用范围

从 12306 官网二维码解析到官方下载入口；官方下载页面脚本及 `down/appDown` 响应均指向：

`https://dynamic.12306.cn/otn/appdownload/12306v5.9.4.5.apk`

检查内置离线 H5 包：

- `assets/60000001_5.9.0.75.amr` → `www/js/list.a58ec60.js`：车次查询与添加候补。
- `assets/60000016_5.9.0.11.amr` → `www/js/robbingTicketOrder.c3445a4.js`：选择乘客、候补提交与响应处理。

安装包下载超时，相关条目从已下载部分提取，并与通过 HTTP Range 获取的 ZIP 中央目录 CRC32 比对一致；未声称完成整包签名验证。没有安装或启动 App，没有访问个人设备或真实乘客信息，没有调用任何候补提交接口。

**限制**：这是官方下载渠道当次返回的安装包，内置 H5 可能在运行时热更新，也未确认用户手机的实际版本。本报告不能证明服务器绝不存在其他接口，更不能把用户所见报错断言为某一响应码。

## 确认的调用顺序

| 阶段 | App 操作名 | 关键输入 / 输出 | 对 5 人探测的意义 |
| --- | --- | --- | --- |
| 添加候补车次 | `afterNate.checkTrain` | `secretList`；成功后添加到候补列表 | 没有显式人数，不能证明 5 人可提交 |
| 进入候补填写 | `afterNate.checkOrder` | `secretList`、身份验证上下文；`needLogin=true`；返回 `enc_data` 等 | 是前置检查，但不是按 N 人容量检查 |
| 更改人数等条件 | `afterNate.queryOrderSuccessRate` | `enc_data`、截止兑现条件、临客条件、`passenger_num`；输出 `successRate` | 返回预测概率，不是是否允许提交 |
| 点击提交订单 | `afterNate.confirmOrder` | `passengerInfo`、`alternateTicketNo`、`enc_data`、兑现条件及验证上下文；`needLogin=true` | 真正提交，不能直接复用为纯探测 |
| 异步处理 | `afterNate.queryWaitingOrder` | 返回处理状态或 `reserveListNew` | 属于提交后的结果，可能已产生待支付订单 |

这里是 App RPC 操作名，不是可以未经验证就拼接调用的公网 HTTP URL。未实施协议重放、认证或风控规避。

### 乘客参数

候补提交参数生成函数遍历 `parsedPassengers` 中 `checked` 的实际乘客，使用票种、证件类型和 `enc_no` 拼出 `passengerInfo`。代码结构摘录：

```javascript
var selected = parsedPassengers.filter(function (p) { return p.checked; });
selected.forEach(function (p) {
  // 证件类型会先经过类型归一化；此处仅保留核心数据依赖。
  passengerInfo += p.user_type + "##" + normalizedIdType + "##" + p.enc_no + "#|";
});
data.alternateTicketNo = state.passengers.length.toString();
data.enc_data = state.enc_data;
```

以上为变量重命名后的说明性摘录，不是可直接运行的请求代码。它说明 App 不一定明文传递完整姓名、证件号，但仍依赖真实乘客身份，不能把“使用加密标识”解读为“不需要乘客信息”。数字 5 单独不足以构成该接口的有效业务输入。

### 成功和失败都来自提交流程

提交按钮调用 `submit()` → `confirmRequiredOrde` → `afterNate.confirmOrder`。同步成功分支读取：

```text
reserveListNew[0].reserve_no
reserveListNew[0].prepay_amount
reserveListNew[0].pay_left_time
```

随后打开 `robbingTicketUnpay.html`。异步分支轮询 `afterNate.queryWaitingOrder`，成功后也进入同一待支付页面。拒绝分支把响应 `error_msg` 展示为提示。

用户说的“选完乘车人，提交时弹错”与该时序一致，但尚无用户那次请求的实际响应，不能精确断言来自同步确认还是异步处理。需保留这一证据边界。

成功率按钮自身的说明为：

> 预估成功率按照历史候补订单兑现情况测算，仅供参考。

因此不能用返回的成功率数值替代“这 5 位乘客的订单能否提交”。

## 产品决策影响

- 保留用户原目标，不能把它悄悄降级成“过滤灰色”后宣称完成。
- 目前不能承诺无登录、无乘客、无订单副作用的按人数探测。
- 若未来找到官方明确支持的预检接口，可继续只填人数方案；否则真实提交路径应明确选择实际乘客，并在提交前由用户决定，不能后台逐车次尝试创建订单来探测。
- 不用先要求用户发送身份证号、密码或 Cookie。当前有价值的下一份证据，是用户实际版本在点击提交时的操作名、返回状态与去除身份/凭证的错误文案；不是乘客明细。

来源清单与哈希见 [android-source-manifest.json](waitlist-api/android-source-manifest.json)。网页接口对照见 [网页核对报告](2026-09-29-waitlist-api-investigation.md)。

## 乘客标识来源补充

用户询问：是否能直接在 train-trip 填写乘客信息或加密标识，12306 会不会解密校验失败。

进一步核对同一 APK 内 `60000001_5.9.0.75.amr` 中的 `www/js/passenger.a58ec60.js`：乘客选择页调用 `queryPassenger`，参数 `queryVersion=1`，并声明 `needLogin=true`。回调接收 `passengerResult`，在保留乘客对象字段的基础上处理选中状态与核验状态；`enc_no` 随乘客对象交给候补页，不是在候补页用姓名、证件号现算出来。

候补页 `selectPassengers()` 打开上述乘客页，取回选中的真实乘客。提交参数组装读取该对象的 `user_type`、归一化后的 `id_type` 和 `enc_no`。姓名、证件号、出生日期、国籍/地区、联系方式和核验状态也用于展示、票种或资格检查，但不能将所有这些字段误称为每次 confirmOrder 都明文上送的必填字段。

据此区分三件事：

1. **在产品中录入姓名、证件资料**：可以设计表单，但录入本地不等于已经获得 12306 的有效乘客标识。需要接通官方登录、乘客登记/核验及查询流程，取得返回的乘客记录；这些能力在 train-trip 尚未实现或验证。
2. **手填 enc_no**：没有证据支持把它当作可跨账号、跨会话、长期复用的独立凭据。正确实现应从当前登录流程返回的乘客列表原样获取、使用，并处理失效刷新，不能由用户随便填值或把证件号直接放进该字段。
3. **是否“解密失败”**：客户端只证明它是服务返回、随后原样使用的不透明标识，未证明其内部加密/签名方式、账号/会话绑定规则或有效期。错误值可能被服务拒绝，但不能确定具体报错一定是解密失败。使用官方返回值也不自动保证第三方客户端通过所有认证、业务和风控检查。

推荐的条件性产品流程是：完成 12306 身份认证 → 获取当前账号可用乘客 → 用户选择 5 位 → 由记录取得标识。用户无需看到或填写 enc_no，客户端也无需解密它。该流程仍不能解决“纯探测”问题：目前找到的 confirmOrder 成功时会进入正式订单流程，不能因为乘客标识已取得就称为无副作用容量校验。
