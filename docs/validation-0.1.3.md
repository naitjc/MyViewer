# MyViewer 0.1.3 验证记录

更新时间：2026-10-26（北京时间）。历史记录：[0.1.0](validation-0.1.0.md)、[0.1.1](validation-0.1.1.md)、[0.1.2](validation-0.1.2.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.3.apk`；大小 13,885,306 字节（13.24 MiB）。
- applicationId `com.chen.myviewer`；versionName `0.1.3`、versionCode `4`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名及 16 KiB ZIP 对齐校验通过。
- 与 0.1.2 的签名证书 SHA-256 完全一致；数据库结构未变，可覆盖安装保留书架、进度和书签。
- APK SHA-256：`5f2f286f5411ad8253a0d1cab4e7fc511389a5df47ca2fb4cd6c00c46bff5468`。
- 校验文件：`dist/MyViewer-0.1.3.apk.sha256`。

## 本次修复

漫画阅读区绘制黑色背景，但进入漫画前的浅色主题仍设置深色状态栏图标，造成时间、电量缺乏对比。现在根据实际背景选择图标颜色：漫画使用白色图标，书架和小说使用对应主题的图标。仅在窗口或所需图标颜色变化时设置，避免滚动期间反复写入窗口属性。

回归中还发现，加载提示框的独立组合可能在加载状态被清空后读取 `loading!!`。改为捕获当前加载书籍，避免打开很快的文件时偶发空值异常。

## 自动检查

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 38 项测试通过，0 失败、0 错误、0 跳过。Lint 为 0 个错误、19 条警告，未关闭错误检查。

新增 `StatusBarFlowTest` 共 4 项：在 Android API 28 / 35、日间 / 夜间配置下实际启动 Activity、导入并打开漫画、切换逐页 / 连续模式、退出回书架，读取窗口状态栏外观标志，检查漫画白色图标及退出后的主题恢复。

其余 34 项回归覆盖小说、ZIP 导入、阅读进度、书签、翻页与缩放手势、48 页长图预加载和图片解码；具体覆盖见 [0.1.2 记录](validation-0.1.2.md)。

## 验证边界

- Robolectric 的窗口外观标志检查不等同于真机系统 UI 截图。尚未连接 iQOO Neo6 SE，需要手机复测时间、电量图标的实际显示。
- 本次没有修改漫画手势、预加载和小说排版实现，已有阅读回归通过。

本机日志：`.tooling/v0.1.3-tests.log`、`.tooling/v0.1.3-release.log`；测试和 Lint 报告位于 `app/build/reports/`。
