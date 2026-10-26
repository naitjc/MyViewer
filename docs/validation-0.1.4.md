# MyViewer 0.1.4 验证记录

更新时间：2026-10-26（北京时间）。此前记录：[0.1.3](validation-0.1.3.md)、[0.1.2](validation-0.1.2.md)、[0.1.1](validation-0.1.1.md)、[0.1.0](validation-0.1.0.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.4.apk`；大小 13,885,306 字节（13.24 MiB）。
- applicationId `com.chen.myviewer`；versionName `0.1.4`、versionCode `5`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名及 16 KiB ZIP 对齐校验通过。
- 与 0.1.3 的签名证书 SHA-256 完全一致；Room 数据库结构未变，新增独立排序设置键，可覆盖安装保留书架、进度和书签。
- APK SHA-256：`7e5569488cd58a32e3d2de6a6d2a33e2129953d3f344d5f5804c7ac3eae7db1e`。
- 校验文件：`dist/MyViewer-0.1.4.apk.sha256`。

## 本次修改

- 书架右侧增加可拖动滚动条，支持点击轨道；随普通滚动更新位置，少于一屏和空分类时隐藏。触摸区域与封面留距。
- 排序以稳定枚举写入 DataStore，进入阅读后返回不会重置；字号等阅读设置更新不会覆盖排序。
- 书名和导入时间的相同值按固定次序排列，避免受最近阅读查询顺序影响。
- 分类及网格滚动状态在阅读页面之外持有，阅读返回时保留；排序菜单标记当前选项。

## 检查结果

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 41 项测试通过，0 失败、0 错误、0 跳过。Lint：0 个错误、19 条警告，未关闭错误检查。

| 新增测试 | 数量 | 覆盖 |
| --- | --- | --- |
| BookshelfFlowTest | 2 | 120 本书：滚动条拖动首尾、点击中部、跟随普通滑动、空分类隐藏；书名 / 同导入时间排序在阅读后保持、分类保留、Activity 重建 |
| SettingsStoreTest | 1 | 关闭 DataStore 的全部写入任务和缓存后重新打开真实设置文件，排序保持，且与字号和导入设置互不覆盖 |

其余 38 项回归覆盖小说、漫画手势、进度、书签、ZIP、长图预加载和状态栏，见前版验证记录。UI 测试显式清理各自 ViewModel 所属的模拟数据库，避免不同模拟 Activity 的测试数据互相影响。

已查看 `.tooling/screenshots/bookshelf-fast-scroll.png`：三列封面书架保留，右侧滚动条与封面分开。另有 `bookshelf-sort-title.png`、`bookshelf-sort-imported.png` 用于记录两种排序画面。

## 验证边界

- 书架滚动条按网格行估计中间位置；首尾直接定位首末条目，中间用于快速近似定位。
- Robolectric 检查不等同于真机测试；尚未在 iQOO Neo6 SE 上确认拖动手感和系统手势边缘的实际表现。
- 设置文件关闭后重开验证了持久化；没有进行手机系统强杀后的自动化安装测试。

本机日志：`.tooling/v0.1.4-tests.log`、`.tooling/v0.1.4-release.log`；测试和 Lint 报告位于 `app/build/reports/`。
