# MyViewer 0.3.2 验证记录

更新时间为 2026-11-13，北京时间。上一版记录见 [0.3.1](docs/validation-0.3.1.md)。

## 整本作品下载

作品详情增加“下载整本到文件夹”。漫画全书生成一个 ZIP，根目录保留 RTL ComicInfo 元数据，内部以章节序号加章名建立文件夹，图片按五位数字排列。小说整本 UTF-8 TXT 与单章保存继续使用系统文件夹授权。章节行可单独保存该章。

全部选定章节成功后才写入完整作品文件，任一章节失败或取消会清理应用内暂存。文件写入失败时尝试清理当前新建目标。保存范围为当前目录内可读取的章节，任务随应用进程运行，终止进程后需重新发起。

回归验证整个 ZIP 的章节顺序、页面格式与 RTL 元数据、通过本地阅读器再次打开，以及后续章节失败后目标目录无残缺整书文件。

## 个人维护小说源

内置仓库选择默认使用 [speauty/legado.sources](https://github.com/speauty/legado.sources) 的 active.json，另提供 [XIU2/Yuedu](https://github.com/XIU2/Yuedu) 与 tickmao 综合合集。导入后保存来源，在列表按仓库筛选并更新当前仓库，筛选保存到本机。自定义 URL 与 JSON 文件导入继续可用。

2026-11-13 核查 GitHub API，speauty 最后提交为 2026-09-04，活动订阅共 7 条；XIU2 最后提交为 2026-08-14，共 22 条，作者说明其维护频率有限。

使用应用实际解析器对两个仓库各 4 个站点，以公版作品“红楼梦”验证。speauty 中爱下书小说网返回 50 个搜索结果，匹配作品解析到 126 章，修复排除索引后首章为第一回，首章正文 8029 字符。完整验证覆盖搜索、目录和首章。其余候选出现 TLS、HTTP 403 / 500 或无匹配搜索结果，未作为可用性通过结果。

实时日志为 `.tooling/v0.3.2-personal-live-results.txt` 与 `v0.3.2-personal-live-final-results.txt`，临时验证代码归档在 `.tooling/novel-source-review/PersonalSourceLiveTest.kt`。个人仓库部分源使用完整 Legado Java / 浏览器接口，当前仍需逐个验证兼容性。

真实 speauty 爱下书规则以测试夹具导入，搭配原创 HTML 验证来源持久化、搜索、正文段落和目录 `!0:1:2:3:4:5:6:7:8` 排除规则，保证预览章节不进入整本下载顺序。

## 正则错误

原模板表达式 `\{\{([\s\S]*?)}}` 的结尾右花括号未转义。在本机原生 ICU 引擎下精确复现 `U_REGEX_RULE_SYNTAX offset=15`。改为 `\{\{([\s\S]*?)\}\}` 后编译通过，小说模块 7 条静态正则均通过原生 ICU 检查。JVM 的正则容忍行为使之前的搜索自动测试未发现这一问题。

原生 ICU 检查记录为 `.tooling/v0.3.2-icu-validation.txt`，另增加模板分隔符及实际表达式替换回归。目标手机上的 Android 系统正则和文件夹选择器仍待实测。

## 验证命令

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

完整日志为 `.tooling/v0.3.2-tests.log`，签名构建日志为 `.tooling/v0.3.2-release.log`。数据库继续使用 schema 5。

102 项完整自动测试通过，0 失败、0 错误、0 跳过。Lint 0 个错误、30 条既有警告。已检查漫画整本保存与个人仓库选择面板的原生截图，分别为 `.tooling/screenshots/comic-folder-download.png` 和 `novel-personal-catalogs.png`。

## 安装包

Release 构建、APK v2 / v3 签名与 16 KiB ZIP 对齐检查通过。签名证书 SHA-256 为 `ed00efc51ec2fbd3b5d52fc35faf0390e95708461651020e929b578498dbf0d4`，与上一版一致。

安装包为 `dist/MyViewer-0.3.2.apk`，versionCode 为 20，大小为 15483451 字节，SHA-256 为 `2850015795a218b61ccbd416a9690c7ad58ebf9a040ae37cc5fb75b9e744b26c`。校验文件为 `dist/MyViewer-0.3.2.apk.sha256`。
