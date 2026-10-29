# MyViewer 0.1.10 验证记录

更新时间：2026-10-29（北京时间）。此前记录：[0.1.9](docs/validation-0.1.9.md)、[0.1.8](docs/validation-0.1.8.md)、[0.1.7](docs/validation-0.1.7.md)、[0.1.6](docs/validation-0.1.6.md)。

## 安装包与升级

- 文件：`dist/MyViewer-0.1.10.apk`；大小 13,967,226 字节。
- applicationId `com.chen.myviewer`；versionName `0.1.10`、versionCode `11`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名及 16 KiB ZIP 对齐通过，与 0.1.9 签名证书一致。
- Room 从 schema 1 升级为 2，新增 reading_times 表及索引，显式注册非破坏性 MIGRATION_1_2；既有书库、位置、偏好、书签和文件夹来源均保留。
- APK SHA-256：`64055100af760cc6b257aa7e30947233b7719c3806648fa7a82d7fe1d1bd421f`；校验文件：`dist/MyViewer-0.1.10.apk.sha256`。

## 统计口径与显示

用户确认：阅读器前台、屏幕点亮时计时，后台 / 锁屏暂停，同时显示今日、累计和每本书时长。

- 历史页顶部显示今日与累计，各本书显示累计时长，浅色和深色主题均可查看；清空历史仍保留统计，空历史页继续显示总时长。
- 手动阅读与自动阅读均计入阅读器前台亮屏且未锁定的停留时间；书库、导入和打开准备界面不计。没有无操作超时策略。
- 统计从更新后开始，未从旧 lastOpenedAt 或进度推算不可确认的历史阅读时长。
- elapsedRealtime 测量真实间隔，本地时间用于按日期拆分；跨午夜拆段，夏令时或手动改钟不扩大实际总时长。
- 每 5 秒累计检查点，后台 / 锁屏 / 退出补存；保存由应用级 IO 作用域完成。检查点以 (sessionId, day) 为键取最大值，重复或乱序保存不会加倍或回退。
- 监听仅包含受保护的亮屏 / 熄屏 / 解锁系统广播；API 33 及以上显式使用非导出标记，旧版本使用兼容注册，不依赖 AndroidX 旧版自签名接收权限检查。

## 检查结果

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 72 项测试通过，0 失败、0 错误、0 跳过；Lint 0 个错误、19 条警告，未关闭错误检查。

新增 8 项测试：

- 4 项计时单元测试覆盖暂停 / 恢复、重复检查点、跨午夜、夏令时、修改墙钟、打开等待及换书隔离。
- 1 项数据库测试覆盖累计检查点去重和乱序、按天 / 按书聚合，以及清历史、移出书架和更新书籍后的保留。
- 1 项真实 SQLite schema 1 → Room schema 2 迁移测试，依据旧版导出结构创建文件，验证旧小说、文字 / 图片位置、模式 / 方向、书签及备注 / 时间、文件夹均保留，新时长可保存并在关闭后重开读取。
- 2 项完整界面流程覆盖前台、后台、熄屏、锁定亮屏、解锁、手动与自动阅读、换书、历史展示和清空历史后保留。

原有 64 项小说 / 漫画阅读、自动阅读、缩略图、手势、进度、书库、历史、设置和 API 28 / 35 状态栏回归均通过。

已查看统计页非零时长浅色示例与深色效果。截图位于 `.tooling/screenshots/reading-time-history.png`、`reading-time-cleared-history.png`、`history-redesign-dark.png`。

## 验证边界

- Robolectric / 原生图形验证不等同于 iQOO Neo6 SE 实机验证；尚未在目标手机检查系统锁屏 / 解锁事件和实际计时表现。
- 无法补算更新前的阅读时长；进程异常终止时可能丢失最后一段未完成的保存，正常检查点间隔约 5 秒。

最终日志：`.tooling/v0.1.10-tests-final.log`、`.tooling/v0.1.10-release.log`；测试与 Lint 报告位于 `app/build/reports/`。
