# MyViewer 0.3.0 验证记录

更新时间为 2026-11-12，北京时间。上一版记录见 [0.2.5](docs/validation-0.2.5.md)。

## 小说源接入

发现页增加漫画 / 小说切换，小说页提供 Legado JSON 网络导入和本地文件导入，按地址更新去重、按名称筛选及删除书源。默认导入地址使用 tickmao/Novel 主要书源包。

小说源提供搜索、详情、目录、正文、目录刷新、书架和阅读历史入口。在线章节复用现有文字阅读器，每章保存文字位置、完成状态和书签，阅读时间汇总到作品。末页再次前进后退出章节并标记完成，全部已知章节完成后标记整部作品。

支持单章 / 整书下载、取消、重试与删除离线正文。临时正文位于应用缓存目录，上限为 64 MB，主动下载位于应用文件目录，删除书源后仍能读取完整离线章节。

## 规则兼容

实现 HTML / CSS 与常见 Legado 选择链、索引 / 排除、基础元素 XPath、JSONPath、正则替换、模板表达式、有限 JavaScript 桥接、请求头、POST 和字符编码。JavaScript 禁止直接访问 Java 类，设置执行超时。完整浏览器验证、站点登录及 Legado Java API 尚未接入。具体范围见 README。

真实上游得奇小说网与小说阅读源定义作为测试夹具，返回原创 HTML 与正文，验证搜索、目录、正文分页、段落、下一章边界及离线读取。另验证 JSONPath、脚本、动态模板与 HTML 索引规则。

## 实时站点验证

2026-11-12 使用维护包中的源规则进行实时请求，搜索关键词为“红楼梦”。结果仅记录数量与正文长度。

| 小说源 | 搜索结果 | 选定作品章节数 | 首章正文字符数 |
| --- | --- | --- | --- |
| 全本小说网 | 48 | 118 | 2580 |
| 看书 | 2 | 226 | 7547 |
| 错层小说 | 2 | 226 | 7547 |

实时构建日志为 `.tooling/v0.3.0-novel-live.log`，上表记录本次运行的标准输出结果，临时验证代码为 `.tooling/NovelLiveSmokeTest.kt`。这些结果验证了各站点一部搜索匹配作品的首章，覆盖范围随该次网络状态变化。

得奇小说网同次搜索返回 0 条，小说阅读源可以搜索和获取目录，但选定作品的正文规则未命中。这两个结果保留为兼容边界，合集全部书源尚未逐个验证。

## 数据升级

Room schema 从 4 升为 5，chapter_positions 新增 textOffset，bookmarks 新增 chapterKey。真实 SQLite 迁移验证旧书签、完成状态及章节位置，schema 1 和 3 的历史迁移回归也更新至当前版本。

## 验证命令

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

测试日志为 `.tooling/v0.3.0-tests.log`，签名构建日志为 `.tooling/v0.3.0-release.log`。界面使用 Robolectric 原生图形，目标手机尚未实测。

92 项完整自动测试通过，0 失败、0 错误、0 跳过。Lint 0 个错误、30 条警告。最后的小说分页状态整理后，另运行小说界面、规则回归及 Lint，日志为 `.tooling/v0.3.0-final-targeted.log`。

完整界面回归覆盖网络导入、搜索、详情、章节切换、恢复位置、章节书签隔离、下载全书、删除源后的离线读取，以及所有章节完成后标记整书。保存书签的提示条曾遮挡章节按钮，现按菜单实际高度避开操作区，正常触摸点击回归通过。

已检查原生截图 `.tooling/screenshots/novel-online-details.png` 与 `novel-online-downloaded.png`。

## 安装包

Release 构建、APK v2 / v3 签名和 16 KiB ZIP 对齐检查通过。签名证书与 0.2.5 一致，可覆盖安装。

安装包为 `dist/MyViewer-0.3.0.apk`，versionCode 为 18，大小为 15467067 字节，SHA-256 为 `88d46d716fc371a532f6f68f88084c53859f10c1ef4e0094a44fb9cd09e01dfa`。校验文件为 `dist/MyViewer-0.3.0.apk.sha256`。
