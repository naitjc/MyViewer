# MyViewer 0.1.2 验证记录

更新时间：2026-10-26（北京时间）。历史记录：[0.1.0](validation-0.1.0.md)、[0.1.1](validation-0.1.1.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.2.apk`；大小 13,885,306 字节（13.24 MiB）。
- applicationId `com.chen.myviewer`；versionName `0.1.2`、versionCode `3`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名及 16 KiB ZIP 对齐校验通过。
- 与 0.1.1 签名证书 SHA-256 完全一致；数据库结构未变，可覆盖安装保留书架、进度和书签。
- APK SHA-256：`9b8cee239d953342e4e134dca0bf002dfee4d48a7f7d3888d342c2b468a08ca0`。
- 校验文件：`dist/MyViewer-0.1.2.apk.sha256`。

## 本次行为

- 翻页模式的全屏触控 View 在图片尚未加载时也存在；两侧点击根据当前页码和阅读方向翻页，中心打开菜单。
- 放大后轻点两侧仍然翻页；双指与拖动有独立的判定，不触发轻点翻页。
- 翻页模式在当前 View 内缩放；连续模式移除了独立图片 Dialog，改为缩放包含相邻图片的整个阅读画面。
- 长按临时放大，松手或取消时恢复此前的比例和位置。已有放大比例下长按，也恢复到长按之前的状态。
- 双指与双击缩放在松手后保持；连续模式放大后拖到画面上下边缘可继续移动底层列表。
- 漫画切换模式后收起菜单；小说的原有菜单流程保留。
- 手势中节流发布阅读进度，结束时补存最新位置；临时长按缩放不发布临时图片锚点。

## 自动验证

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 34 项测试通过，0 失败、0 错误、0 跳过。Lint：0 个错误、19 条警告；未关闭错误检查。

| 测试组 | 数量 | 覆盖 |
| --- | --- | --- |
| ComicGestureTest | 4 | 实际触摸事件与像素：长按松手还原、已有缩放后取消还原、双指原地缩放及缩放后两侧翻页、加载前两侧翻页 |
| ComicTouchPolicyTest | 3 | 左 / 右阅读方向、中心菜单、禁用翻页时的点击 |
| ComicRenderingTest | 3 | 慢解码预览、重复绑定、快速可见区域变化、后台释放解码器 |
| LongComicFlowTest | 2 | 48 张长图、双向预加载、滚动进度节流与保存、从连续切换到翻页后两侧点击 |
| ComicFlowTest | 1 | 实际 Activity：RTL 点击 / 滑动、双击、切换连续后双指缩放和长按恢复、无 Dialog、模式和进度恢复 |
| AppFlowTest | 1 | 小说书架、滚动、书签、退出与恢复 |
| DatabaseTest / ImportRepositoryTest | 7 | 会话和保存顺序、进度保留、复制与重复导入、ZIP 元数据 |
| NaturalOrderTest / PaginationTest / TextGestureTest / TextParserTest | 13 | 排序、分页、动画、编码与章节 |

漫画图形测试使用 Robolectric 4.17 / API 34 原生图形，另外的测试覆盖 API 28 / 35。测试资料为项目生成的原创图案和色块，不含第三方漫画。已查看 `.tooling/screenshots/comic-continuous-inline-zoom.png`；缩放后仍使用 Activity 原有画面，测试同时检查无 Dialog。

## 验证边界

- 仍未连接 iQOO Neo6 SE。本次验证覆盖自动触摸与原生像素，不代表已经确认该手机的触屏、帧率和系统手势表现。
- 0.1.1 的有限范围预加载和预览机制继续保留；极快跳到尚未加载的图片时仍可能短暂显示加载占位。
- 本次没有修改 SAF 授权、内容格式、数据库结构或小说绘制实现。缩放比例为当前阅读会话状态，未新增跨重启保存缩放比例的功能。

本机日志：`.tooling/v0.1.2-tests.log`、`.tooling/v0.1.2-release.log`；测试和 Lint 报告位于 `app/build/reports/`。
