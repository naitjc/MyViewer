# MyViewer 0.1.1 验证记录

更新时间：2026-10-26（北京时间）。0.1.0 的记录保留在 [docs/validation-0.1.0.md](validation-0.1.0.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.1.apk`；大小 13,868,922 字节（13.23 MiB）。
- applicationId：`com.chen.myviewer`；版本 `0.1.1`，versionCode `2`。
- 最低 Android API 26；编译与目标 API 36。
- Release 构建、APK v2 / v3 签名和 16 KiB ZIP 对齐检查通过。
- 已比对 0.1.0 / 0.1.1 签名证书 SHA-256，完全一致；数据库结构未改变，支持覆盖安装。
- APK SHA-256：`58927cedbe0d7acb211631f2f2c19a6979c595cbcd224a8ffedf11244825dbb2`。
- 校验文件：`dist/MyViewer-0.1.1.apk.sha256`。

## 漫画修复与验证

- 左 / 中 / 右点击区域遵循 LTR / RTL 设置：两侧翻页，中间菜单；放大后单击不切页，双击仍可切换缩放。
- 按滚动方向预加载前方 3 页、身后 1 页；先读图片尺寸，减少加载时的布局变化。
- 受内存限制的整图预览铺底，固定网格的高清块逐步补齐；快速改变可见区域不再反复取消解码，已完成的块可复用。
- 图片解绑后在后台关闭解码器；图片缓存清理也移出前台读取路径。
- 同一页滚动时界面进度发布最多每 200 ms 一次，停止、退出与切换模式时保留最新位置；亮度仅在设置变化时应用。

执行命令：

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

29 项自动测试通过，0 失败、0 错误、0 跳过。Lint 为 0 个错误、19 条警告，未关闭错误检查。

| 测试组 | 数量 | 主要覆盖 |
| --- | --- | --- |
| ComicTouchPolicyTest | 3 | LTR / RTL 两侧点击、中心菜单与放大状态 |
| ComicRenderingTest | 3 | 慢打开时重复绑定只开一次、慢解码和快速移动时预览像素完整、主线程解绑不等待解码 |
| LongComicFlowTest | 1 | 48 张 600×12000 长图、双向预加载、快速滑动后的不透明图片像素、滚动发布节流与退出保存最新位置 |
| ComicFlowTest | 1 | 实际 Activity、ZIP 图片像素、RTL 左右点击与滑动、双击缩放、模式与偏好恢复 |
| AppFlowTest | 1 | 书架、小说滚动、书签、退出与恢复 |
| DatabaseTest / ImportRepositoryTest | 7 | 会话与保存序号隔离、保留进度、复制与重复导入、ZIP 元数据 |
| NaturalOrderTest / PaginationTest / TextGestureTest / TextParserTest | 13 | 数字排序、分页边界、翻页动画、文本编码与章节 |

Robolectric 4.17 使用 API 28 / 34 / 35。漫画渲染用例使用 API 34 的原生图形与实际位图像素；慢解码用例另外用受控解码器验证并发时序。48 页长图是本项目生成的原创不透明色块测试资料，不包含第三方漫画。已查看 `.tooling/screenshots/long-comic-after-fling.png`。

## 验证边界

- 尚未连接 iQOO Neo6 SE；自动测试不能替代该手机上的帧率、存储读取速度、耗电和安装体验。
- 预加载范围有限。极快跳到尚未加载的图片、首次读取很大的 ZIP 条目时，仍可能短暂显示加载占位；已加载图片的高清块未就绪时先显示低清预览。
- 沿用首版的 SAF 授权和内容格式范围；文件选择器授权、系统杀进程后重开仍需真机检查。
- 本次没有更改小说解析和翻页实现；已有小说回归测试通过。

本机构建日志：`.tooling/v0.1.1-final-tests.log`、`.tooling/v0.1.1-release.log`；测试与 Lint 报告位于 `app/build/reports/`。
