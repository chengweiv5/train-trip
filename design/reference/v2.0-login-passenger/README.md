# 登录与乘车人选择 · 修订预览

历史版本。用户已将功能明确为候补下单，当前完整设计见 `../v2.0-waitlist-order/`；以下“提交语义待确认”不再适用。

当前提案。前半段遵循用户已确定的“登录 12306 → 读取乘车人 → 勾选”方向。后半段是待确认方案：每次由用户确认真实提交，成功时显示待支付订单。该方案不等于原先要求的无副作用批量探测。

`review.pdf` 共 7 页；`preview.html` 为静态预览；`screen-01.png` 至 `screen-07.png` 为单页图；`overview.png` 为总览。所有账号、乘客和订单状态均为设计示例，不含真实个人资料。

预览为浏览器近似渲染，**不是 Pencil 导出**。CLI 当前未登录，原生画板创建、保存及回读尚未执行。Android 功能没有实现，未进行任何真实登录、乘客查询或订单操作。

浏览器完成字体加载、父容器边界和横向文本溢出检查；证据见 `preview-validation.json`。主要页面已人工查看。检查不等同 Pencil/Android 运行验证。

设计输入为 `design/v2.0-waitlist-probe/login-passenger-screens.js`，复用原 `canvas.js` 在 `screens=[]` 之前定义的助手函数。旧版 8 页预览仅供历史对照。

图标沿用 Lucide 0.468.0，许可见 `../v2.0-waitlist-probe/LUCIDE-LICENSE`。
