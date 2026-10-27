# MyViewer 0.1.6 验证记录

更新时间：2026-10-26（北京时间）。此前记录：[0.1.5](validation-0.1.5.md)、[0.1.4](validation-0.1.4.md)、[0.1.3](validation-0.1.3.md)、[0.1.2](validation-0.1.2.md)、[0.1.1](validation-0.1.1.md)、[0.1.0](validation-0.1.0.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.6.apk`；大小 13,918,074 字节（13.27 MiB）。
- applicationId `com.chen.myviewer`；versionName `0.1.6`、versionCode `7`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名及 16 KiB ZIP 对齐校验通过。
- 与 0.1.5 签名证书一致；Room 和 DataStore 设置结构未改变，可覆盖安装保留书架、进度、书签与设置。
- APK SHA-256：`b16e9553bd33a9c107263bb66edbfbf56a3112b498b2d30520c370c437b9376c`；校验文件：`dist/MyViewer-0.1.6.apk.sha256`。

## 设置分组折叠

- 书架布局、小说阅读、导入偏好、文件夹来源和存储管理均可点击标题独立展开 / 收起，可同时展开多个。
- 默认仅展开书架布局，其余收起；箭头带旋转动画，内容有展开 / 收起动画，标题显示当前设置摘要。
- 标题为独立按钮，内部滑块、开关及选项不会触发折叠；隐藏内容不会改变设置值。
- 展开状态由根界面的不可变列表持有，用显式 Saver 保存，跨页返回和 Activity 重建时恢复。列表项使用稳定标识。
- 为辅助功能提供展开 / 收起状态和点击动作标签。

## 检查结果

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 46 项测试通过，0 失败、0 错误、0 跳过。Lint：0 个错误、19 条警告，未关闭错误检查。

新增交互回归检查默认展开状态、多分组独立展开、内部设置操作、折叠后值保留、跨页返回和 Activity 重建恢复。原有设置页面用例已适配先展开分组，再访问阅读预览或缓存按钮；其余书库搜索、回顶、列数、小说和漫画测试全部通过。

已查看 `.tooling/screenshots/settings-all-collapsed.png` 和 `settings-redesign-dark.png`，检查了折叠概览、状态箭头、当前值摘要和浅色 / 深色对比。另有默认与阅读展开截图。

## 验证边界

- 尚未在 iQOO Neo6 SE 上验证动画帧率和触摸手感。
- 分组状态属于可恢复的界面状态，未新增跨全新启动永久保存折叠偏好的 DataStore 设置。

本机日志：`.tooling/v0.1.6-tests.log`、`.tooling/v0.1.6-release.log`；测试与 Lint 报告位于 `app/build/reports/`。
