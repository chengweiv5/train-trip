# 目的地独立补图验收

日期：2026-09-22。基线：已刷新并同步 origin/main 的 `8817acd`（v0.7.0 / versionCode 12）；开发分支 `codex/destination-photo-recovery`。修改前检查 GitHub 待合并 PR，无候选修复。本轮不改版本号，不推送、不发布、不安装用户手机。

## 结果与原因

原图片链路依赖普通文本搜索附带的图片及说明匹配，未给旧介绍提供独立补图入口。实际请求还发现携程目录对旧移动端 UA 返回验证页、部分原图超过 5 MiB 下载上限。新链路读取公开城市与景点的结构化归属，核验省份、城市、景点 ID、名称和区域；适配公开桌面页 UA，照片上限调整为 10 MiB，HTML 仍限制 5 MiB。验证页不绕过，图片仍限制受支持的 HTTPS 来源且不携带服务密钥。

旧介绍新增“补充图片 / 只更新图片”，不要求 DeepSeek Key，不调用正文生成或改动正文/生成时间/收藏/查票条件。图片逐张原子保存，成功新图置前、旧图补足最多 5 张；全部失败或无候选不改 JSON，取消保留已成功图片。无法读取主图源或候选不足时用最多两次 Tavily 定向检索；主源候选足够但全下载失败时也可尝试尚未调用的备用检索。每批最多 12 个候选，备用补救最多另试 12 个。已有有效缓存图不重复下载，缺失文件可重下。新介绍先保存文字，再独立补图。

## 真实来源验证

凭据为空，使用当前 `CtripPhotoSource` 和 `GuideNetwork` 在宿主机真实读取、下载，ImageIO 解码。输入是指定城市与已有景点名，不调用 DeepSeek 或付费 Tavily。

| 城市 | 指定景点 | 候选及成功解码 | 首图尺寸 |
| --- | --- | --- | --- |
| 邯郸 | 邯郸市博物馆 | 4 / 4 | 2500×1875 |
| 保定 | 直隶总督署 | 4 / 4 | 1796×1235 |
| 承德 | 避暑山庄 | 3 / 3 | 4000×2670 |

邯郸、保定各含一张明确标注的城市资料页配图；其余为景点页图片。三城各抽查首图，内容与对应建筑/景观相符。实际较大图片包括 6,053,996 和 10,012,240 字节，均在新 10 MiB 上限内。相片仅保存于忽略目录，没有打包进应用或提交到仓库。

证据：`.verification-private/photo-recovery/live.log`、`live/results.json`、`live/*.jpg`。网络网站内容会变化，以上是本次验证结果，不承诺所有城市总能获得图片；携程验证/超时或 Tavily 不可用时仍可重试并保留旧内容。Tavily 备用逻辑以隔离夹具覆盖，未消费真实搜索额度。

## 构建与回归

命令：`./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain`。

- 核心 97 项测试全部通过，无失败/错误/跳过。覆盖结构化归属、异地/错误 ID、地址及别名、城市封面、失效备用/取消、Tavily 两次查询与来源标注、6 MiB 图片/10 MiB 上限/5 MiB HTML、验证及不可信重定向。
- Debug 与 instrumentation APK 构建成功；lint 0 errors / 16 warnings。警告均落在原有依赖版本、Modifier 参数位置、Android 备份规则和 KTX 建议代码，本轮未新增对应问题。
- 首轮模拟器运行 PhotoRecoveryTest、V061Test、DeepSeekDestinationTest、OfflineManagementTest，共 36 项通过。
- 最终生产代码 APK 再运行 PhotoRecoveryTest（10 项）与 DeepSeekDestinationTest（10 项），共 20 项通过。覆盖正文不变、全部失败 JSON 字节不变、取消保留部分成功、缺失缓存恢复、共享文件保护、无模型 Key、离开后忽略过期结果、下载失败启用备用和成功主源不调用备用。
- UI 夹具统一为天津内置内容后，仅重建测试包并重跑普通成功/320dp 与 1.3 倍字体两个用例，2 项通过，lint 仍为 0 errors / 16 warnings。

使用专属 `emulator-5582`：现有 API36 AVD 的只读无窗口实例，无快照写回。未操作用户真机。APK SHA-256：`36d9de55f238b6a4b1ea80724805fcc6fd5654148b9d39ab257edb5f70309009`。

日志：`build-final.log`、`ui-tests.log`、`ui-final.log`、`build-ui-fixture.log`、`ui-visual.log`，均在 `.verification-private/photo-recovery/`。真实来源下载/解码在宿主机完成；Android 落盘及界面为可重复夹具验证，不等同于用户手机网络实测。

## 设计与界面

`design/train-trip-photo-recovery.pen` 为独立的可编辑补充画板 `BTo68`，22 个节点；原生保存到当前工作区后复制到私有目录并重新打开，MCP 回读结构与文字一致。只包含本次三种状态，无旧设计外部素材依赖。按钮高度 48dp、节点无裁剪，1140×420 JPEG 目视与 OCR 可读。相册成功、无图失败与大字体在 Android 实际渲染，补图/取消/重试均可见、可点击。

`design/reference/photo-recovery/states.html` 是静态状态参考。私有截图 `photo-recovery-success.png`、`photo-recovery-large.png` 使用天津夹具，不作为三城真实内容截图。`ocr.txt` 保存 OCR 结果。

## 回滚

重要源文件修改前备份在 `.verification-private/photo-recovery/source-before.tar`；设计 README 另有 `design-readme-before.md`。本次不迁移存储 schema，也不改密钥/版本配置，旧版本可读新的相册字段。需要撤回时，在功能分支对本次实现提交执行 `git revert <实现提交>` 并重新构建；若后续已有其他修改，先检查反向 diff，避免直接覆盖后续代码。补图对用户缓存的更新只能在安装新构建并由用户触发后发生，本轮未修改手机缓存。
