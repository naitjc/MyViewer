# MyViewer 0.3.4 验证记录

移除小说书源联网引擎、导入搜索与校验界面、内置整合包及专用依赖。发现页直接提供漫画源。本地 TXT 的导入、分页、连续阅读、设置、进度、书签和阅读时长继续通过已有回归验证。

保留旧版小说元数据与本地章节读取兼容，书架和历史可进入已保存章节列表。缺少本地正文的章节显示不可读状态，读取失败提示导入 TXT。旧版下载数据与原有 Room schema 5 继续兼容。

新增回归覆盖旧永久下载章节的读取，现有 TXT 与漫画测试全量回归。目标手机尚未实测。

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

测试日志为 `.tooling/v0.3.4-tests.log`，签名构建日志为 `.tooling/v0.3.4-release.log`。

97 项自动测试全部通过，0 失败、0 错误、0 跳过。Lint 0 个错误、29 条警告。

Release 构建、v2 / v3 签名和 16 KiB 对齐通过。APK 内已移除小说整合源资产。安装包 `dist/MyViewer-0.3.4.apk`，versionCode 22，大小 15237691 字节，SHA-256 `e5c4f4b846de7b05c5a0fc9677fdf77165b7ce04d71f55de81b26c8b46c9a7d9`。
