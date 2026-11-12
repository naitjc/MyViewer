# MyViewer 0.2.1 验证记录

更新时间为 2026-11-11，北京时间。上一版记录见 [0.2.0](validation-0.2.0.md)。

## 已读完标记

- TXT、ZIP 和在线漫画作品均可通过书架长按菜单或阅读菜单标记完成，也可取消标记。
- 书架与历史页使用统一的状态文本。书库继续阅读卡片从未标记完成的书籍中选择，历史页中的完成作品可再次打开。
- 在线作品详情提供作品级标记，章节菜单提供逐章标记和“标记为未读完”。
- 作品标记采用独立布尔字段。章节使用可空布尔字段区分手动选择和自动进度，旧章节默认按既有进度判断，手动选择后优先采用手动状态。
- 更新阅读快照时保存已有章节完成标记。整部作品与单章标记可独立操作。

## 语言筛选

“发现”保留全部语言和中文，按实际索引显示英语、日语、韩语常用入口，其他语言放入“更多语言”。地区和文字变体通过基础语言归组，例如 zh-Hans / zh-Hant 归入中文，pt-BR 归入葡萄牙语。

筛选检查源声明的全部语言，也覆盖已安装源。筛选条件存于源浏览 ViewModel，在源页面及源设置中往返后继续保留。

## 数据升级

Room schema 从 3 升为 4，books 新增 completed 字段，默认 false，chapter_positions 新增可空 completed 字段。应用注册 1 → 2 → 3 → 4 升级路径。

真实 SQLite schema 3 迁移测试验证既有作品、阅读位置、章节进度与时长，并检查标记修改后关闭数据库再重开仍可读取。旧 schema 1 迁移回归同步运行。

## 验证命令

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

最终 81 项自动测试通过，0 失败、0 错误、0 跳过。Lint 0 个错误、27 条警告。

定向回归已验证完成状态隔离、后续进度保存、手动取消自动完成章节、语言代码归组、本地书籍标记与取消、跨 Activity 重建、章节菜单、源语言筛选及返回后的筛选保留。

截图已检查。

- `.tooling/screenshots/bookshelf-completed.png`
- `.tooling/screenshots/online-chapter-completed.png`
- `.tooling/screenshots/source-languages-portuguese.png`

最终日志为 `.tooling/v0.2.1-tests-final.log` 与 `.tooling/v0.2.1-release.log`，安装包为 `dist/MyViewer-0.2.1.apk`，versionCode 为 13。

## 验证范围

界面验证采用 Robolectric 与原生图形，目标手机尚未实测。在线源的协议兼容范围沿用 0.2.0 记录。

Release 构建、APK v2 / v3 签名与 16 KiB ZIP 对齐检查通过。签名证书与 0.2.0 一致，可覆盖安装。APK 大小为 15,172,155 字节，SHA-256 为 `782c87b8609e4c6d6913f8265248258426d94a2a2fb1275c01b353a00260d0d8`，校验文件为 `dist/MyViewer-0.2.1.apk.sha256`。
