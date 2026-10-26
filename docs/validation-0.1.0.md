# MyViewer 0.1.0 验证记录

更新时间：2026-10-25 21:41（北京时间）。

## 安装包

- 文件：`dist/MyViewer-0.1.0.apk`
- 大小：13.23 MiB。
- applicationId：`com.chen.myviewer`；版本 `0.1.0`，versionCode `1`。
- 最低 Android API 26；编译与目标 API 36。
- 独立 Release 构建，应用专用签名；APK v2 / v3 签名校验通过。
- SHA-256：`9f35251ce0c55c9dac4a045e2fa97f06b79e28d6e26d0b88310b1f1f41fdec4a`

## 已完成检查

最终执行：

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

共 22 项自动测试通过，0 失败。

| 测试组 | 数量 | 主要覆盖 |
| --- | --- | --- |
| AppFlowTest | 1 | 实际 Activity 启动、书架、小说滚动、书签、退出与位置恢复 |
| ComicFlowTest | 1 | ZIP 打开、实际图片像素、双击缩放、RTL 翻页、连续模式与偏好恢复 |
| DatabaseTest | 3 | 会话与序号隔离、清历史 / 隐藏书架后保留数据、Upsert 保留进度 |
| ImportRepositoryTest | 4 | 复制后删除原文件、重复导入保留进度、ZIP 排序与元数据、失败副本清理 |
| NaturalOrderTest | 3 | 嵌套路径和数字排序、超长数字、比较器一致性 |
| PaginationTest | 2 | 分页完整覆盖、前后页一致、字号变化、UTF-16 代理项边界 |
| TextGestureTest | 1 | 平移 / 仿真两种动画的完成与取消 |
| TextParserTest | 7 | UTF-8 / GB18030 / UTF-16、章节偏移、空文件、无目录与摘录边界 |

Lint：0 个错误，24 条警告，主要为固定版本的更新提示、KTX 写法和绘制实现建议；未通过关闭错误检查来放行。

Robolectric 4.17 中分别使用 API 28、34、35。漫画原生分块解码使用框架支持的 API 34 环境，执行了实际像素检查；[框架实现依据](https://github.com/robolectric/robolectric/blob/robolectric-4.17/shadows/framework/src/main/java/org/robolectric/shadows/ShadowNativeBitmapRegionDecoder.java)。

## 渲染页面检查

已查看 `.tooling/screenshots/` 中的书架、小说正文、书签和漫画逐页 / 连续阅读截图。漫画内容为本项目生成的色块样例，未使用第三方漫画。

## 验证边界

- 尚未连接 iQOO Neo6 SE，未进行真实手机安装、文件选择器授权、系统杀进程重开及性能 / 耗电测试。
- 导入仓库测试使用可读取的本地文件 URI；SAF 目录授权与厂商文件管理器的实际表现需要真机试用。
- 仿真动画已经检查状态提交与取消；真实触感、帧率和卷曲细节需要在手机上继续调整。
- 当前支持范围和源文件替换时的限制见 README.md；跨内容版本的锚点迁移未实现。

调试日志：`.tooling/verified-tests.log`、`.tooling/release-signed.log`。完整测试与 Lint 报告位于 `app/build/reports/`。
