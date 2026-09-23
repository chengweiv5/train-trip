# 有票再出发 · Train Trip

**先看哪里有票，再决定去哪里。**

一个帮你发现短途旅行目的地的 Android 应用：按日期、时段和车程查找有票城市，看景点、美食与多日玩法，把心动城市加入「想去」，再到 12306 核验并购票。

> [!TIP]
> **假期快到了，又没抢到票，旅行就要泡汤了吗？**
>
> 想去的地方没票，不妨换个思路：**“还有哪些城市有票？把它们列出来，我从里面选！”**
>
> 圈定想考虑的城市，设好出发日期、时段和能接受的乘车时长。应用会查询直达去程车票，**只展示查询时有票、且符合筛选条件的城市**，再用景点、美食和参考玩法帮你挑选下一站。
>
> **少一点“没票就算了”的遗憾，多一次发现新目的地的机会。**

**[⬇ 下载 Android 正式版 v1.0.0](https://github.com/chengweiv5/train-trip/releases/download/v1.0.0/train-trip-v1.0.0-release.apk)**　·　[更新说明与历史版本](https://github.com/chengweiv5/train-trip/releases)　·　[安装方法](#下载安装)

Android 8.0 及以上 · 无需注册 · 查票、收藏和主题切换无需 API Key

> **v1.0.0 已发布**：四套浅色主题随心切换；玩法支持一天、两天和更长行程，每城市最多保留三种天数的参考安排。[查看更新说明](docs/releases/v1.0.0.md)

## 给一次临时起意的旅行，找个目的地

- **不用逐城试车票**：一次选择多个目的地，按符合条件的直达去程余票发现可去城市。
- **按自己的时间出发**：日期范围、出发时段多选、席别、人数、3 小时 / 5 小时或自定义车程，都能调整。
- **先了解，再决定**：查看景点介绍、独立相册、美食参考和旅行贴士；资料保留来源，方便打开原文。
- **一天或多天，都有参考**：按天查看路线安排，每种天数保留一条，最多三种；玩法可以包含景点列表之外的地点，具体安排以检索到的攻略为基础。
- **把心动留到下次**：批量收藏城市，按省份查看或通过「全部」浏览；最近收藏排在前面，查票时可直接从「想去」分组选取。
- **准备好，再去购票**：对比发到时刻、车程和席别余票，打开 12306 App 完成后续查询与购票。
- **换一种喜欢的颜色**：晴空蓝、松林绿、暖阳橙、暮山紫四套浅色主题，点选即切换，下次打开仍会保留。
- **保存下来，路上再看**：已保存的城市介绍和图片可离线阅读，也能按城市管理占用空间；清理离线资料会保留收藏与服务配置。

## 四套主题，选一种喜欢的颜色

进入 **设置 → 外观 → 主题配色**，先看效果预览，再点选喜欢的主题。晴空蓝延续原有外观；松林绿、暖阳橙、暮山紫会同步调整页头、按钮、卡片与选中状态。

选择自动保存在本机，离线也能切换。切换时保留筛选条件、车次标记和想去清单；有票、提醒等状态色保持一致，便于辨认。小屏或大字号下，主题选项会改为可滚动的单列。

[![四套主题选择页：晴空蓝、松林绿、暖阳橙、暮山紫](docs/images/v1.0-themes/picker-comparison.jpg)](docs/images/v1.0-themes/picker-comparison.jpg)

[查看四套主题的首页效果](docs/images/v1.0-themes/home-comparison.jpg)

以上为 v1.0.0 实际界面的四套主题对比，城市、日期与余票为演示数据。

## 从查票到出发

下面 10 张截图均已按 v1.0.0 重新采集，展示查票、收藏、目的地介绍、主题设置和离线管理流程。点击图片可查看大图。

车次、余票、日期和收藏使用离线演示数据；天津图文与玩法来自应用内置资料。截图说明界面，不代表实时余票或每个城市都有完整图片、路线。[截图来源与采集说明](docs/images/readme/README.md)

<table>
  <tr>
    <td width="50%" align="center" valign="top">
      <strong>① 设好条件，发现有票城市</strong><br><br>
      <a href="docs/images/readme/home.jpg"><img src="docs/images/readme/home.jpg" width="320" alt="查票首页：北京出发，选择日期范围、早上和下午、席别、人数、最长车程及查询城市"></a><br>
      选择出发地、日期范围和可接受的车程；出发时段可多选，一次查询多个目的地。
    </td>
    <td width="50%" align="center" valign="top">
      <strong>② 比一比，哪些城市适合出发</strong><br><br>
      <a href="docs/images/readme/results.jpg"><img src="docs/images/readme/results.jpg" width="320" alt="有票城市：按省份分组，展示可选车次、最快车程与目的地亮点"></a><br>
      结果按省份分组，结合车次数、最快车程和城市亮点比较；可查看介绍或进入车次列表。
    </td>
  </tr>
  <tr>
    <td width="50%" align="center" valign="top">
      <strong>③ 看清车次，再去 12306</strong><br><br>
      <a href="docs/images/readme/trains.jpg"><img src="docs/images/readme/trains.jpg" width="320" alt="车次详情：发到时刻、乘车时长、席别余票，以及打开 12306 App 入口"></a><br>
      对比发到时刻、车程和余票，可标记意向车次、手动刷新；打开 12306 App 核验并购票。
    </td>
    <td width="50%" align="center" valign="top">
      <strong>④ 从「想去」快速选择目的地</strong><br><br>
      <a href="docs/images/readme/destinations.jpg"><img src="docs/images/readme/destinations.jpg" width="320" alt="查询目的地：想去分组、收藏城市勾选和选择完成入口"></a><br>
      按省份查找城市，也可直接勾选「想去」清单；当前选择会带回查票条件。
    </td>
  </tr>
  <tr>
    <td width="50%" align="center" valign="top">
      <strong>⑤ 收藏心动城市，下次再出发</strong><br><br>
      <a href="docs/images/readme/wishlist.jpg"><img src="docs/images/readme/wishlist.jpg" width="320" alt="想去清单：固定全部入口、河北山东天津分组，以及最近收藏的城市"></a><br>
      通过「全部」浏览所有收藏，或按省份筛选；最近收藏优先，可直接了解目的地或查车票。
    </td>
    <td width="50%" align="center" valign="top">
      <strong>⑥ 看景点介绍，也看实景图片</strong><br><br>
      <a href="docs/images/readme/guide.jpg"><img src="docs/images/readme/guide.jpg" width="320" alt="天津景点：五大道介绍、古文化街实景照片与图片来源、建议停留时长"></a><br>
      每个景点有独立介绍与相册，可横滑、查看大图和来源；景点累计最多 10 个，每项最多 3 张图。
    </td>
  </tr>
  <tr>
    <td width="50%" align="center" valign="top">
      <strong>⑦ 看看当地有什么好吃的</strong><br><br>
      <a href="docs/images/readme/food.jpg"><img src="docs/images/readme/food.jpg" width="320" alt="天津美食：煎饼馃子、十八街麻花、耳朵眼炸糕介绍与资料来源入口"></a><br>
      美食按具体菜品介绍，找到匹配照片时展示参考图；累计最多 10 种，没有合适图片时保留文字。
    </td>
    <td width="50%" align="center" valign="top">
      <strong>⑧ 按游玩天数参考行程</strong><br><br>
      <a href="docs/images/readme/plans.jpg"><img src="docs/images/readme/plans.jpg" width="320" alt="天津玩法：一日与两日切换、两天的逐日安排和资料来源入口"></a><br>
      图中展示天津两日玩法；也支持更长的行程，最多保留三种天数，按天查看参考安排。
    </td>
  </tr>
  <tr>
    <td width="50%" align="center" valign="top">
      <strong>⑨ 切换主题，按需配置服务</strong><br><br>
      <a href="docs/images/readme/settings.jpg"><img src="docs/images/readme/settings.jpg" width="320" alt="v1.0 设置：主题配色、DeepSeek 大模型、豆包搜索、离线内容和关于与更新入口"></a><br>
      在「主题配色」切换四套浅色外观；需要新城市介绍时再配置 DeepSeek 和豆包搜索，查票与收藏无需 Key。
    </td>
    <td width="50%" align="center" valign="top">
      <strong>⑩ 保存到本机，随时再看</strong><br><br>
      <a href="docs/images/readme/offline.jpg"><img src="docs/images/readme/offline.jpg" width="320" alt="离线内容：已下载城市、占用空间、查看更新介绍与删除入口"></a><br>
      查看已保存城市和占用空间，按需更新或删除；清理离线资料会保留收藏与服务设置。
    </td>
  </tr>
</table>

图片权利归原作者及相关权利人所有。本组截图中的古文化街照片保留来源，美食页展示无图片时的文字介绍；[查看图文出处](docs/images/readme/README.md#图文来源)。

## 三步开始一次短途旅行

1. **告诉它你什么时候能走。** 选择出发城市、日期、时段和最长车程；想少坐一会儿车，就先试试「3 小时」。
2. **挑一座有票又心动的城市。** 点击「查询有票城市」，查看结果、了解目的地，也可以先收藏。
3. **到 12306 App 核验并购票。** 查看合适车次后，点击「打开 12306 App」，手动填写条件完成购票。返程车票需要另行安排。

## 下载安装

| 下载入口 | 说明 |
| --- | --- |
| **[下载 v1.0.0 正式 APK](https://github.com/chengweiv5/train-trip/releases/download/v1.0.0/train-trip-v1.0.0-release.apk)** | 生产签名 Release 包 |
| [查看最新正式发布](https://github.com/chengweiv5/train-trip/releases/latest) | 更新说明与安装附件 |
| [下载 SHA-256 校验文件](https://github.com/chengweiv5/train-trip/releases/download/v1.0.0/train-trip-v1.0.0-release.apk.sha256) | 用于核对安装包完整性 |

在 Android 手机上下载 APK 后打开，按系统提示允许当前浏览器或文件管理器安装应用，再完成安装。打开应用即可查票，不必先配置大模型或搜索服务。

同一生产签名的正式版可以直接覆盖升级并保留本机数据。若提示签名冲突，请保留原安装，使用与原包同签名的升级包；卸载会清除收藏、设置和离线介绍。应用内也可通过「设置 → 关于与更新」手动检查新版本。

## 常见问题

### 能直接买票或抢票吗？

应用负责发现目的地与查询余票，不提供购票、抢票或自动下单。「打开 12306 App」会唤起已安装的铁路 12306，仍需在其中手动填写条件。余票随时变化，最终以 12306 App 的查询及下单结果为准。本项目是个人开发的独立工具，与铁路 12306 无隶属关系。

### 不配置 DeepSeek、豆包搜索也能用吗？

可以。**查票、想去清单、主题切换和内置城市介绍都不需要 Key。** 天津、济南、青岛、大同、洛阳已有内置资料，可离线阅读。

需要整理其他城市时，到「设置」填写自己的 DeepSeek 与豆包搜索 Custom 版 API Key。豆包检索网页和图片，DeepSeek 整理介绍；只有手动发起整理或更新才会消耗服务额度，打开城市和升级应用不会自动重新生成资料。APK 不包含个人 Key，也无需自建服务器。

每城市最多保留 10 个景点和 10 种美食，每项最多 3 张图。没有合适图片时保留文字；资料优先采用国内简体中文来源，保存后可离线查看。从使用 Tavily 的旧版升级时，需单独填写豆包搜索 Key。

### 玩法支持几天？会自动生成完整行程吗？

支持一天、两天及更多天数的参考安排。每种天数保留一条，每城市最多三种，优先保留天数较短的玩法；实际有哪些天数，取决于检索资料。

不同玩法可以参考不同文章，同一条多日玩法来自同一篇攻略，并保留原文入口和逐日安排。玩法中的地点可以不在景点列表里。资料不足时可能没有合适路线，也不会为凑齐数量补造行程；出发前请结合原文确认开放时间、交通和预约要求。

### 换主题会影响已经选好的行程吗？

不会。主题切换只调整界面颜色，保留筛选条件、车次标记、收藏和离线资料，也不会重新查票或调用内容服务。选择保存在本机，重启后继续使用；没有选过主题时默认晴空蓝。

### 可以查任意城市、所有车站吗？

可以更换出发城市，并在已收录的目的地目录中选择查询范围；默认勾选 24 个城市。目前查询**直达去程**，不规划中转或往返组合。车站映射和代表站策略可能遗漏部分同城站及路线，不保证全国完整覆盖。查询失败会给出提示，未查到结果也不等同于所有路线都无票。

### 需要哪些权限？数据放在哪里？

应用仅申请网络权限，不读取定位、通讯录。筛选偏好、想去清单、主题、服务配置与已下载介绍保存在本机，没有应用账号或云同步。查票需要联网；手动整理介绍会向配置的搜索和模型服务发送城市及资料整理请求。卸载应用会删除本机数据。

## 参与与开发

遇到问题或有想法，欢迎[提交 Issue](https://github.com/chengweiv5/train-trip/issues)。反馈时附上应用版本、手机系统和复现步骤；如果它帮你找到了下一站，也欢迎点个 Star。

<details>
<summary>本地构建与项目资料</summary>

技术栈为 Kotlin + Jetpack Compose。构建环境：JDK 21、Android SDK 36；在本机 `local.properties` 中配置 `sdk.dir`。

```bash
./gradlew :core:test :app:assembleDebug :app:lintRelease :app:assembleRelease
```

Release 默认生成未签名包，分发前需使用仓库外的私有签名配置。不要提交签名私钥或服务 Key。

连接专用测试设备后可运行 `./gradlew :app:connectedDebugAndroidTest`。该完整测试集包含少量真实 12306 查询，不应高频循环；核心测试离线运行。

- [开发历史与验收索引](docs/development-history.md)
- [已确认的 UI 设计](design/README.md)
- [v1.0 四套主题可编辑设计稿](design/train-trip-v1.0.0-themes.pen)
- [v1.0 主题设计与切换验收](docs/verification/2026-09-23-v1.0-themes.md)
- [v1.0.0 更新说明](docs/releases/v1.0.0.md)
- [v1.0.0 发布验证](docs/verification/2026-09-23-v1.0.0-release.md)
- [v0.9.0 更新说明](docs/releases/v0.9.0.md)
- [v0.9.0 发布验证](docs/verification/2026-09-23-v0.9.0-release.md)
- [v0.6.1 开发实现与验证](docs/verification/2026-09-21-v0.6.1.md)
- [v0.5.0 / v0.6.0 正式发布记录](docs/verification/2026-09-21-v0.5.0-v0.6.0-releases.md)

</details>
