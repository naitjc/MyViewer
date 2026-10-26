# MyViewer 0.1.5 验证记录

更新时间：2026-10-26（北京时间）。历史记录：[0.1.4](docs/validation-0.1.4.md)、[0.1.3](docs/validation-0.1.3.md)、[0.1.2](docs/validation-0.1.2.md)、[0.1.1](docs/validation-0.1.1.md)、[0.1.0](docs/validation-0.1.0.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.5.apk`；大小 13,918,074 字节（13.27 MiB）。
- applicationId `com.chen.myviewer`；versionName `0.1.5`、versionCode `6`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名与 16 KiB ZIP 对齐校验通过。
- 与 0.1.4 签名证书完全一致；Room 结构未变，列数使用新增独立设置键，支持覆盖安装保留书架、进度和书签。
- APK SHA-256：`0b920632835aae38d4f2e6471a3af67fb4ce4be8528279786d126589953782fc`；校验文件：`dist/MyViewer-0.1.5.apk.sha256`。
- 已检查 APK 包含新书库 / 历史 / 设置页面和列数设置，旧滚动条类已移除。

## 本次行为

- 书库固定书名搜索框，支持中文部分匹配、英文大小写忽略及清空；阅读返回后保留关键词、分类与排序。
- 取消滚动条；双击底部“书库”直接定位顶部，滚动到底后出现悬浮回顶按钮。单击当前书库入口保持原位置。
- 列数支持 1～6 本，默认 3 本，写入 DataStore。密集布局同步缩小封面占位文字和书名。
- 实际搜索、分类、排序或列数更新完成后再回顶，避免异步排序更新时按旧书籍键重新锚定。
- 历史页使用继续阅读卡片、日期分组、阅读时间和进度；清空历史仍保留书籍与进度。
- 设置页使用分组卡片，提供阅读预览、背景色样、导入偏好、文件夹和缓存管理；浅色 / 深色统一配色。

## 检查结果

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 45 项测试通过，0 失败、0 错误、0 跳过。Lint：0 个错误、19 条警告，未关闭错误检查。

| 相关测试 | 数量 | 覆盖 |
| --- | --- | --- |
| BookshelfFlowTest | 4 | 排序 / 分类在阅读返回和界面重建后保持；120 本书到底后的按钮及单击 / 双击回顶；书名搜索、清空和阅读恢复；4 列 / 6 列布局与重建恢复 |
| LibraryPagesFlowTest | 2 | 历史日期分组、清空取消与确认、保留进度；设置预览、阅读背景变更和原有缓存入口；浅色 / 深色实际渲染 |
| SettingsStoreTest | 1 | 关闭所有写入任务和缓存后重开真实文件，排序、4 / 6 列及阅读设置互不覆盖 |

其余 38 项覆盖小说、漫画手势、进度、书签、ZIP、长图预加载及状态栏。

已查看 `.tooling/screenshots/` 下的 `bookshelf-six-columns.png`、`bookshelf-bottom-action.png`、`history-redesign.png`、`history-redesign-dark.png`、`settings-layout.png` 和 `settings-redesign-dark.png`，确认布局、对比度和底部按钮留白。截图使用本项目生成的虚构书籍资料。

## 验证边界

- 尚未连接 iQOO Neo6 SE。系统输入法、双击手感、屏幕边缘系统手势和真实帧率仍需手机复测。
- 列数持久化通过设置文件关闭重开验证；界面状态通过 Activity 重建验证，未进行真机系统强杀流程。
- 书名搜索仅过滤书架标题，不搜索小说正文或 ZIP 内图片文本。

本机日志：`.tooling/v0.1.5-tests.log`、`.tooling/v0.1.5-release.log`；报告位于 `app/build/reports/`。
