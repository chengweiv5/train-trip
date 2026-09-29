# v2.0 候补探测设计准备

当前修订为 `login-passenger-screens.js`：登录、选人、正式提交语义等 7 页；需复用本目录 `canvas.js` 在 `screens=[]` 前的助手函数。不能直接用旧 `prepare-input.mjs` 生成当前修订。旧 `canvas.js` 的 8 页纯探测流程仅作历史提案，不应继续作为实现依据。

`canvas.js` 是按 Pencil CLI 0.3.9 的原生 execute API 编写的画板构建输入，尚未经过 Pencil 执行验证。它不是 `.pen` 文件，也不代表可编辑稿已交付。

本机 `pen status` 和 `pen interactive` 返回认证要求。登录后，通过交互 shell 的 `execute({input: ...})` 运行本文件，检查警告、截图和节点布局，修正后使用 `save()` 保存，再从磁盘重新打开验证。输入不能直接交给 JavaScript shell 执行，因为 `Insert` / `Update` / `Print` 由 Pencil 提供。

目标源文件：`design/train-trip-v2.0-waitlist-probe.pen`。

预览：`../reference/v2.0-waitlist-probe/preview.html`。该预览为本地浏览器近似渲染，用于等待 Pencil 认证期间评审页面结构与文案，**不是 Pencil 导出**。所有车次、时刻和状态为演示数据；真实人数校验能力尚未验证。

规格：`../../docs/superpowers/specs/2026-09-29-v2.0-waitlist-probe-design.md`。

`prepare-input.mjs` 可把画板输入编码成 Pencil 交互命令文件（只准备文件，不调用 Pencil、不进行认证、不启动其他 AI）：

```sh
rtk proxy node design/v2.0-waitlist-probe/prepare-input.mjs /tmp/train-trip-v2-pencil-input.txt
```

认证后执行应逐步检查每次返回。发生 execute 错误时按照 Pencil 要求使用返回的 `editId` / `edits` 修复，不能忽略错误继续接受结果。批量命令中包含保存、导出命令，仅适合已人工检查的输入。原生布局、图标、字体与主题变量尚待 Pencil 实际校验，浏览器预览不能替代这些检查。
