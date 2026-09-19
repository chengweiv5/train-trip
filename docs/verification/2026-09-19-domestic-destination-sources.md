# 国内目的地图文替换验收

2026-09-19。基线为 rebase 后的 `origin/main`：`d457523`，开发分支为 `codex/v0.4.0`。本轮交付个人离线版，版本保持 `0.4.0` / `versionCode 8`。

## 实际改动

天津、济南、青岛、大同、洛阳的景点与美食重新依据已读取的国内政府、文旅资料整理，共 18 条正文来源；五张照片也更换为国内原文配图。来源、核对日期、照片署名、原图地址与 SHA-256 见 [来源清单](2026-09-19-domestic-destination-sources.json)。

| 城市 | 配图 | 刊载来源与署名 |
| --- | --- | --- |
| 天津 | 古文化街 | 天津市文化和旅游局；摄影：胡凌云／天津日报 |
| 济南 | 大明湖 | 济南市园林和林业绿化局；原页面未署名 |
| 青岛 | 青岛湾 | 青岛市文化和旅游局；图片署名：青岛图片库（图／董天顺） |
| 大同 | 华严寺 | 大同市政府网站／市外办；原页面未署名 |
| 洛阳 | 龙门石窟 | 河南省文化和旅游厅，来源：学习强国；原页面未标明摄影者 |

原图仅转正、压缩和缩放，保持完整画面与已有署名；App 沿用原有裁剪展示。来源弹窗展示真实出处，移除旧的固定 CC 正文许可；图片许可字段改为可选且成对校验，没有许可时不显示许可按钮。HTTP / HTTPS 原文链接通过外部浏览器打开。

五城 cityId 和一日／两日路线与基线逐项比对一致。保留四分页、导航和车票行为；未覆盖城市继续正常查询车次。本轮未扩展城市数量或加入运行时爬虫。共享 Pencil 正在由另一设计任务使用，本轮未编辑旧版 `.pen`，来源弹窗以运行截图验收。

## 自动与视觉验证

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
ANDROID_HOME=/Users/bytedance/Library/Android/sdk \
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline
```

- 核心测试：37 项通过，0 失败、0 跳过，包括图像解码、国内来源、许可成对校验及非法链接降级。
- 隔离模拟器 `emulator-5582`：`DestinationGuideTest` 的 10 项测试通过（36.363 秒），覆盖四分页、横滑、返回、状态恢复、来源链接、许可按钮、320dp / 1.3 倍字体及短屏。
- APK 和测试包构建通过；lint 为 0 errors、12 个已有 warnings。青岛摄影者署名修正后已重新构建安装包和测试包，再执行界面测试。
- 五张照片逐张目视核对；常规页、来源弹窗、大字体及短屏截图通过目视检查，三张主要截图另做 OCR。`08-places.jpg` 确认天津照片加载完成；`02-destination.jpg` 截于异步加载前，仅保留为原始过程证据。
- `git diff --check` 通过；来源清单与打包 JSON、照片摘要一致。

本地原始证据位于 `.verification-private/v0.4.0/domestic-sources/`：`build.log`、`rebuild-credit.log`、`ui-tests.log`、`photos-contact.jpg`、`runtime/` 和 `ocr-final.json`。图片与截图保持私有，不纳入公开交付。

## 真机交付

Mate 60 Pro（`FMR0224725012307`）已先备份旧 `base.apk` 和筛选设置，再保留数据覆盖安装。安装返回 `Success`；启动返回 `Status: ok`，前台为 `cn.traintrip.app/.MainActivity`。回读包信息为 `0.4.0` / code `8`。

安装包：`artifacts/train-trip-domestic-personal-debug.apk`。从真机拉回的安装包与本地 APK 的 SHA-256 相同：

```text
cc16aa5dcb2d439836dd64fda27bd90c2a546c240cb3a9f8e6c105ea24e0a020
```

安装前、安装后及启动后的 `travel-filters.xml` 字节一致，SHA-256 均为：

```text
43a2dd98fb6ff498f708934fb096eeab07819ed622c363c10307905e4f18b9d9
```

详细回读与机器可核验结果保存于私有证据目录的 `phone/delivery.json`、`phone/sha256.txt`、`phone/package.txt` 和 `phone/activities.txt`。未清除数据，未在真机运行 instrumentation。

## 分发范围与回滚

本轮仅本地提交、构建和真机安装，未推送公开仓库。照片保留真实署名，不将个人自用表述为开放许可。未来公开推送前，应检查完整提交历史中的图片和带图产物。

代码可通过对本次实现提交执行反向提交回滚；修改前备份为 `.verification-private/v0.4.0/domestic-sources/source-before.tar`。真机可用以下命令覆盖回旧包，保留设置：

```sh
/Users/bytedance/Library/Android/sdk/platform-tools/adb \
  -s FMR0224725012307 install -r \
  .verification-private/v0.4.0/domestic-sources/phone/before.apk
```
