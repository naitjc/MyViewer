# MyViewer 0.1.7 验证记录

更新时间：2026-10-27（北京时间）。此前记录：[0.1.6](validation-0.1.6.md)、[0.1.5](validation-0.1.5.md)、[0.1.4](validation-0.1.4.md)、[0.1.3](validation-0.1.3.md)、[0.1.2](validation-0.1.2.md)、[0.1.1](validation-0.1.1.md)、[0.1.0](validation-0.1.0.md)。

## 安装包

- 文件：`dist/MyViewer-0.1.7.apk`；大小 13,918,074 字节（13.27 MiB）。
- applicationId `com.chen.myviewer`；versionName `0.1.7`、versionCode `8`；最低 API 26、目标 API 36。
- Release 构建、APK v2 / v3 签名及 16 KiB ZIP 对齐校验通过。
- 与 0.1.6 签名证书一致；Room 与 DataStore 结构未改变，可覆盖安装保留书库、进度、书签和设置。
- APK SHA-256：`a28377e26f73cdd6c2db4febeda05d9508058995052fd8ffcc13560f864c8a6b`；校验文件：`dist/MyViewer-0.1.7.apk.sha256`。

## 重复计页修复

用户报告部分 ZIP 的 20 页被计为 40 页，清晰图与模糊图重复出现。未获取用户原始 ZIP，不能确认其实际条目命名。本次用含 20 张原图和 20 张缩略图的生成压缩包复现了旧版重复计页，并修复这一类索引问题。

- 针对 thumb / thumbs / thumbnail / thumbnails / preview / previews 目录或文件名前后缀进行配对，忽略文件扩展名的差异。
- 优先匹配相同逻辑路径；根级缩略图没有路径匹配时，可在全包内寻找唯一同名原图。不同章节间不回退匹配，多个候选时保留图片。
- 配对后检查图片尺寸与长宽比：缩略图不大于原图、比例差异不超过 2%，并且尺寸更小，或 CRC 与条目大小相同，才从有效页列表移除缩略图。
- 保留普通小图、重复画面但不同页名的正文、只有缩略图的压缩包、无法唯一匹配或尺寸不符合的图片。
- 只按需读取配对图片的尺寸，不把所有漫画图片完整解码或预解压；原 ZIP 不修改。
- 逐页和连续阅读共用过滤后的页列表。旧缩略图条目保留到对应原图的映射，重开时恢复原图页、保留页内比例并重新计算进度。

## 检查结果

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
./scripts/sign-release.sh
```

最终 52 项测试通过，0 失败、0 错误、0 跳过。Lint：0 个错误、19 条警告，未关闭错误检查。

新增 5 项归档回归测试和 1 项完整页面流程测试：

- 20 张原图加 20 张缩略图只计为 20 页，保留原图尺寸并按数字自然顺序排列。
- 缩略图目录、文件名前后缀、不同扩展名、跨章节同名、根级唯一配对、相同尺寸副本及不确定配对的处理。
- 旧缩略图位置恢复到对应原图；直接读取与复制导入都检查总页数、两侧翻页、切换连续阅读、保存与重开。复制导入后删除原 ZIP 仍可阅读。

原有小说、漫画缩放 / 预加载、书库搜索 / 排序 / 回顶 / 列数、历史和折叠设置回归全部通过。此次没有修改阅读画面的绘制或手势逻辑。

## 验证边界

- 用户原始 ZIP 未提供；本次验证覆盖明确缩略图命名的压缩包，不保证识别未标记、重命名或任意相似图片。
- 不按画面相似度或图片大小对全书去重，避免误合并正文。
- 未在 iQOO Neo6 SE 真机复测。

本机日志：`.tooling/duplicate-pages-repro.log`、`.tooling/duplicate-pages-flow.log`、`.tooling/v0.1.7-tests.log`、`.tooling/v0.1.7-release.log`；测试与 Lint 报告位于 `app/build/reports/`。
