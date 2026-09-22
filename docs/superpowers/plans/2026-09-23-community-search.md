# 民间攻略与图片搜索实施计划

**Goal:** 用民间游记和攻略提升邯郸图文与路线覆盖，保持其他城市下载缓存为空。

**Architecture:** DoubaoGuideSource 负责四类网页查询、筛选及有界图片补查；GuideSearchPolicy/GuidePhotoMatching 处理来源与归属；SearchGuideDecoder 从 routes 文档校验路线，既有 UI 展示原文入口。

**Tech Stack:** Kotlin、OkHttp、Gson、JUnit、Android Compose、豆包 Custom API。

## Global Constraints

- 景点/美食单次和累计各最多十项；每项最多三图、每城市一百图。
- 凭据只发指定 API，下载不携带 Key，简体中文原文证据与取消传播不变。
- 用户已授权清理全部城市缓存；仅验证邯郸，不统一生成其他城市。

## 步骤

1. 在 DoubaoGuideTest 加入民间页面收录、政府不占正文名额、四类查询、700429 重试和补查回归；运行 `:core:test --tests cn.traintrip.core.DoubaoGuideTest` 验证旧实现失败。
2. GuideSearchPolicy 区分可引用公开网页和主要攻略来源；新增 GuidePhotoMatching 收敛原名/通用尾缀匹配，保持步行街与公园不同。DoubaoGuideSource 四类检索、每域两篇、每类六篇、图片最多两轮，使用可取消 delay 和有限重试。
3. PlanDay 增加可空 sourceUrl/evidence，兼容原缓存。SearchGuideDecoder 的 routes 原文、名称与次序检查通过才存入，非法 route 单独丢弃。DestinationGuideContent 加“查看攻略原文”，更新设置中的搜索消耗说明。
4. 运行 core 全量测试、Android assembleDebug/assembleRelease/lintDebug。真实调用复用生产源和解码器，保存脱敏请求类别、原文、最终 guide 和图片可解码统计；对失败原因逐项核验。
5. 使用现有正式签名构建安装，确认手机其他城市零缓存，然后只生成邯郸并读回 UI 的图片、路线与来源。保留原 APK 回滚；最终报告实际覆盖与未达标项。
