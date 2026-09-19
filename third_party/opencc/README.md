# OpenCC 字形数据

`core/src/main/resources/traditional_only_characters.txt` 是离线拒绝繁体资源所用的字形集合，不用于转换或改写引用。

来源：[OpenCC](https://github.com/BYVoid/OpenCC)，固定提交 `f509f2f0ed60cf670f44796a5da3746d974e8892`。原始文件：`data/dictionary/TSCharacters.txt`、`data/dictionary/STCharacters.txt`，许可证见本目录 LICENSE，输入及产物 SHA256 见 source.json。

生成规则：从 TSCharacters 的键中选取没有自身映射、且既不是 STCharacters 的键也不是任意 TSCharacters 映射结果的字符，排序后连接。共 3197 个繁体专用字形。按 Unicode code point 检查，含扩展区字形。这样保留简体中仍合法的共用字，如“乾隆”“著名”，避免简单的“转换前后不相等”误判。

语言版本 URL 另行拒绝 BIG5、zh-Hant、zh-TW、zh-HK 等标识。简繁字形完全相同的文字无法单凭字形区分，来源版本标识和正文同时校验。
