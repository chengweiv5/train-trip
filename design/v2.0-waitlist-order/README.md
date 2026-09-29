# v2.0 候补下单设计源输入

当前产品范围：筛选车次、选择候补需求、登录及勾选乘车人、一次发起自动提交。每次遇到可准确归因的候补过多拒绝时，排除该需求并自动继续，直至订单创建或全部候选被排除。用户已明确授权这一产品行为，无需对每轮续提重新询问。

`helpers.js` 与 `screens.js` 组成 17 页 Pencil 原生 execute 输入。全部筛选集中第 1 页，结果页通过“修改条件”返回并回填原输入；原第 3 页独立筛选页已移除，后续页码顺次前移。所有文本、图标和容器为独立节点，没有把整张预览图作为画布背景。覆盖整批提交、逐项排除后整批续提、处理记录、全失败、无法归因和停止后核对。没有自动分组功能；所选组合须满足官方单次订单限制。

当前 **Pencil CLI 0.3.9 未登录，未执行原生建图或保存**。这些文件是待运行输入，不是已完成的 `.pen`。目标为 `design/train-trip-v2.0-waitlist-order.pen`。

准备交互命令：

```sh
rtk proxy node design/v2.0-waitlist-order/prepare-input.mjs /tmp/train-trip-v2-order-input.txt
```

认证后逐步在 Pencil interactive 中执行、检查警告和截图、修复后保存，再重新打开回读。`execute` 失败时用返回的 `editId` / `edits` 修正；不要忽略错误继续接受保存结果。prepare 脚本不绕过登录、不启动 AI 子代理，也不执行真实票务操作。

静态预览：`design/reference/v2.0-waitlist-order/`。规格：`docs/superpowers/specs/2026-09-29-v2.0-waitlist-order-design.md`。
