# 票源和目标设备可行性验证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 当前环境未提供以上执行技能。本轮由主代理顺序执行只读验证；设计审查按 brainstorming 要求由独立审查代理完成。无需再次确认已经授权的数据接入验证。

**Goal:** 判断北京出发的真实余票、同城归并和目标设备是否足以启动 Android App 的真实数据实现。

**Architecture:** 用少量官方查询验证 TicketSource 和 StationCatalog 的输入契约；不创建伪票源或全量扫描。把网页可用、普通 HTTP 可用、App 内可用及全国覆盖分别判定。

**Tech Stack:** 本机 curl、Python 标准库、独立 in-app browser；Android SDK/adb、JDK 21。未来 App 使用 Kotlin + Jetpack Compose。

## Global Constraints

- 目标真机：华为 Mate 60 Pro，HarmonyOS 4.2.0。
- 交付形式：Android APK，Kotlin + Jetpack Compose，不依赖 Google Play Services。
- 开发基线：minSdk 26，compileSdk/targetSdk 36；真机实际 API 等级必须验证。
- 北京出发、仅去程直达、车程/席别默认不限，支持出发日期和时段。
- 不登录、不下单、不提交候补；不读取用户浏览器 Cookie；不修改现有设备/模拟器配置。
- 一次正常网页查询的成功不等于获得公开开发者 API、长期稳定性或全国覆盖。
- 当前目录原为空且无远端，使用 `feat/ticket-source-validation` 分支保存新增产物。

---

## 文件与证据职责

- `docs/superpowers/specs/2026-09-18-train-trip-design.md`：已确认范围、状态语义、设备与验收边界。
- `docs/verification/2026-09-18-ticket-source.md`：可读验证报告、来源 URL、结果与后续决策。
- `docs/verification/2026-09-18-evidence.json`：无凭证的结构化观测、样例车次与工具版本。
- `/private/tmp/train-trip-*`：临时网页/字典/响应。Cookie 不纳入仓库。

## Task 1：核验公开字典、查询入口和城市归并

**Interfaces:** 输入为官方查询页和其实际引用的资源 URL；输出为字典版本、样本车站、城市字段、查询路由。输出只证明当前版本的可读性。

- [x] 用 curl 读取 `https://kyfw.12306.cn/otn/leftTicket/init`，记录 HTTP 状态和字节数。
- [x] 从响应提取 `CLeftTicketUrl` 和 `station_name.js` 的实际路径，不枚举猜测接口。
- [x] 获取该字典并统计记录数、城市 id/name，核对北京南、北京西、北京丰台、天津的城市归属。
- [x] 检查官方页面引用的结果解析代码，记录席别、可售标记、车次、上下车站和日期字段。
- [x] 记录行政归属并非有效客运站名单，字典不等于直达候选全集。

## Task 2：验证真实余票和正常访问差异

**Interfaces:** 输入为已开售日期 2026-09-19、北京、天津、普通票；输出为网页真实样例与普通 HTTP 结果，二者分别判定。

- [x] 通过实际页面路由进行一次普通 HTTP 查询，记录响应，不把重定向当成无票。
- [x] 初始化全新匿名会话，再进行一次同条件查询；Cookie 仅保留在临时目录，不输出内容。
- [x] 在独立 in-app browser 打开官方查询页，通过城市选择器、日期和查询按钮执行一次正常查询。
- [x] 等到查询结果显示后，读取可见车次与席别状态，统计行程行数和内部车次去重数。
- [x] 保存“有、数字、候补、无、--”和跨日到达样例，注明这是瞬时观测。
- [x] 检查全国覆盖所需候选数据是否存在；未取得全量候选证明时记录未知，不扩大查询量。

普通 HTTP 请求（路由由本次页面读取得到）：

```bash
/usr/bin/curl --silent --show-error --connect-timeout 10 --max-time 25 \
  --get 'https://kyfw.12306.cn/otn/leftTicket/queryG' \
  --data-urlencode 'leftTicketDTO.train_date=2026-09-19' \
  --data-urlencode 'leftTicketDTO.from_station=BJP' \
  --data-urlencode 'leftTicketDTO.to_station=TJP' \
  --data-urlencode 'purpose_codes=ADULT' \
  --referer 'https://kyfw.12306.cn/otn/leftTicket/init' \
  -D /private/tmp/train-trip-query-headers.txt \
  -o /private/tmp/train-trip-query-response.json \
  -w 'http_status=%{http_code}\nsize=%{size_download}\n'
```

验收观察而非要求固定结果：成功应返回包含真实车次的结构化响应；302/403/HTML/超时均是访问或协议问题，禁止输出为“无票”。网页样例的数量和余票不写成下一次运行必须保持一致的断言。

## Task 3：核验 Mate 60 Pro 适配条件

**Interfaces:** 输入为用户提供的机型/系统、宿主工具链及 adb 设备列表；输出为已验证工具版本和未完成的真机项。

- [x] 查阅华为官网的 Mate 60 Pro / HarmonyOS 4.2 用户指南入口，保留 URL。
- [x] 运行 JDK 和 adb 版本命令，列出 SDK 平台和构建工具。
- [x] 列出当前 AVD，只读保留原配置，不新建或启动额外设备。
- [x] 运行 `adb devices -l`；无设备时记为真机验证未执行。
- [x] 固定未来构建使用 JDK 21，不盲用 Android Studio 内置的 JDK 25。

```bash
/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java -version
/Users/bytedance/Library/Android/sdk/platform-tools/adb version
/Users/bytedance/Library/Android/sdk/platform-tools/adb devices -l
/Users/bytedance/Library/Android/sdk/emulator/emulator -list-avds
```

预期：命令正常运行；空设备列表是明确的未验证状态，不代表设备不兼容。

## Task 4：报告、审查和交付

**Interfaces:** 输入为上述证据；输出为可审阅文档、JSON、提交和 punk-12 通知。

- [x] 保存已确认设计，独立审查通过；审查结果 Approved，无必修问题。
- [x] 将正常网页查询与 HTTP 结果、全国覆盖限制、设备检查写入报告及证据 JSON。
- [x] 用 Python 校验 JSON 和必要字段；用 `git diff --check` 验证文件格式。
- [x] 检查没有 Cookie/密钥/个人数据，提交新增文件；回读提交与工作区状态。成果提交 `5ee380a`，功能分支工作区干净。
- [x] 用 punk-12 发送本阶段实际结果，并回读消息确认正文。消息 `om_x100b65e103d0f0a0c44bfeb461b6895`，发送者回读为 `punk-12 for Codex`。

本阶段结束后，优先用已验证的正常匿名 HTTP 会话实现手机端单城市对查询，再验证全国直达候选覆盖与请求成本。WebView 或有明确接口契约的其他票源是手机端直接查询失败时的备选。尚未验证这些依赖时不能把整个 App 标记为完成。
