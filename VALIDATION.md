# MyViewer 0.2.4 验证记录

更新时间为 2026-11-12，北京时间。上一版记录见 [0.2.3](docs/validation-0.2.3.md)。

## 界面设计落地

- 采用用户确认的暖白与墨绿设计，顶部以页面名称显示标题，底部导航采用简洁图标和文字。
- 书库保留可选 1～6 列封面，默认三列，分类和完成状态使用两行胶囊筛选。继续阅读卡片采用小封面、书名、操作和进度条的横向布局。
- 发现页整合标题、刷新及仓库管理入口，搜索框与语言筛选统一视觉样式。源列表显示图标、名称、语言与版本，已安装源显示浏览和卸载操作。
- 设置采用分隔线列表，移除重复的顶部摘要。各分组初始全部折叠，“关于 MyViewer”同样可展开查看版本。
- 深色模式采用对应的深色背景与浅绿色强调色。界面使用书库实际封面和现有无封面占位样式。

## 验证

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

复用现有书架筛选、搜索、排序、列数、阅读返回、设置折叠、深色模式、源安装与卸载、章节完成及阅读器回归。截图采用 Robolectric 原生图形，目标手机尚未实测。

测试日志为 `.tooling/v0.2.4-tests.log`，签名构建日志为 `.tooling/v0.2.4-release.log`。

87 项自动测试通过，0 失败、0 错误、0 跳过。Lint 0 个错误、28 条警告。

已检查截图 `.tooling/screenshots/settings-folded-default.png`、`settings-redesign-dark.png`、`online-source-catalog.png`、`bookshelf-search-home.png` 与 `history-redesign.png`，确认设置折叠、源操作、胶囊筛选及继续阅读布局。

## 安装包

Release 构建、APK v2 / v3 签名和 16 KiB ZIP 对齐检查通过，签名证书与 0.2.3 一致，可覆盖安装。

安装包为 `dist/MyViewer-0.2.4.apk`，versionCode 为 16，大小为 15188539 字节，SHA-256 为 `e7a7e75042beb3b8d92f274d75291fe11afe10f39d227771e7ecb8ba7febc8d4`。校验文件为 `dist/MyViewer-0.2.4.apk.sha256`。
