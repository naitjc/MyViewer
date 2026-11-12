# MyViewer 0.2.3 验证记录

更新时间为 2026-11-12，北京时间。上一版记录见 [0.2.2](docs/validation-0.2.2.md)。

## 书库阅读状态筛选

- 搜索框下增加独立的“全部状态、未读完、已读完”筛选，默认显示全部状态。
- 筛选依据书籍 completed 字段，覆盖 TXT、ZIP 和在线漫画作品。进度 100% 且未触发末页完成操作的书籍继续归入未读完。
- 与小说 / 漫画分类、关键词搜索和排序组合使用，书籍数量和继续阅读卡片采用筛选后的结果。
- 更换筛选时回到列表顶部，阅读返回、页面切换及 Activity 重建保留本次选择。
- 当前条件没有结果时显示空状态，可用“显示全部状态”解除完成状态筛选。书籍完成状态变化后结果即时更新。
- 数据库继续使用 schema 4。

## 验证

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

新增组合筛选界面回归，覆盖已完成 / 未完成 TXT 与漫画、在线作品、进度 100% 的未完成书籍、搜索与分类组合、阅读返回、Activity 重建、动态状态变化及空结果恢复。既有末页自动完成、章节完成、源卸载、设置折叠与全部阅读器回归同步运行。

界面验证采用 Robolectric 与原生图形，目标手机尚未实测。

87 项自动测试通过，0 失败、0 错误、0 跳过。Lint 0 个错误、28 条警告。完整测试日志为 `.tooling/v0.2.3-tests.log`。

已检查 `.tooling/screenshots/bookshelf-completion-filter.png`，显示“小说 + 已读完 + 山海关键词”组合筛选及对应书籍。签名构建日志为 `.tooling/v0.2.3-release.log`。

## 安装包

Release 构建、APK v2 / v3 签名和 16 KiB ZIP 对齐检查通过。签名证书与 0.2.2 一致，可覆盖安装。

安装包为 `dist/MyViewer-0.2.3.apk`，versionCode 为 15，大小为 15188539 字节。SHA-256 为 `48825d852a6509335ee53d93650ccea9e06d6abd76e21e8ec7b7c58d5aa89948`，校验文件为 `dist/MyViewer-0.2.3.apk.sha256`。
