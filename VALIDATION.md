# MyViewer 0.2.0 验证记录

更新时间为 2026-11-11，北京时间。上一版记录见 [0.1.10](docs/validation-0.1.10.md)。

## 新增功能与实现范围

- 新增“发现”入口，默认读取 Aidoku Community Sources 索引，支持修改仓库 URL、语言筛选、安装和手动更新源。
- 使用 Chicory 1.5.1 的 Java WASM 解释器和 ByteBuffer 内存实现，执行真实 `.aix` 包内的程序。宿主提供 std、env、net、HTML、defaults 和基础 JavaScript 接口。
- 接入搜索分页、作品详情、章节目录、书架、在线阅读、章节切换、逐章进度和作品级阅读时长。
- 逐章下载以完整页面清单作为完成标记。取消或失败后可以重试，同一页面清单下复用已保存图片，页面清单变化时重新下载部分文件。
- 完整章节存入应用文件目录，可在未调用源程序和网络的情况下打开。下载期间需要应用进程存活。
- 在线页图及封面缓存合计上限为 256 MB，完整下载由作品详情单独删除。
- 源设置覆盖站点地址、内容语言及静态开关 / 文本 / 选项。HTTP 站点请求可由已安装源发起，仓库索引入口采用 HTTPS。
- 源安装先解包和核对 ID / 版本 / 必要导出，再原子更新安装记录。包内路径、解压大小、文件数量与重复项有边界检查，失败时保留上一安装记录。
- WASM 实例串行调用，设置执行时限和内存上限。调用被打断后丢弃该实例，重试时重新创建。

## 数据升级与进度

Room schema 从 2 升为 3，为 positions 增加 chapterKey，并新增 chapter_positions。应用注册 MIGRATION_1_2 与 MIGRATION_2_3。

在线书架按作品保存 Book，章节位置按作品 ID 和章节 key 保存。当前阅读位置仍使用 sessionId 和 sequence 过滤过期保存，再在同一数据库事务中保存章节快照。阅读时长继续归集到作品 ID。

## 自动验证

```sh
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

最终 77 项常规测试通过，0 失败、0 错误、0 跳过。Lint 0 个错误、27 条警告，未关闭检查。

新增回归覆盖以下流程。

- 使用真实 Guya v2 WASM 包与原创 HTTP 测试数据，验证中文查询、作品 / 章节 Postcard 解码、图片请求头、页面解码、逐章下载，以及新建 Repository 后的纯离线读取。
- 安装包 ID / 版本不匹配时保留旧安装，以及索引相对下载地址解析。
- 两个章节独立保存进度，并拒绝旧 session 的延迟写入。
- 从书架打开已下载作品，进入阅读、切换前后章、恢复逐章位置，再返回详情继续阅读。
- 漫画源列表、中文筛选、源设置入口和站点参数显示。
- 既有 schema 1 数据通过 1 → 2 → 3 迁移后，书库、偏好、文字 / 图片位置、书签、文件夹和时长写入仍可用。

界面截图已检查。

- `.tooling/screenshots/online-source-catalog.png`
- `.tooling/screenshots/online-source-settings.png`
- `.tooling/screenshots/online-offline-chapters.png`
- `.tooling/screenshots/online-reader-chapter-controls.png`

## 真实站点网络检查

对 2026-11-11 下载的实际社区源包进行了独立网络冒烟检查。该检查使用临时测试文件，常规回归使用固定二进制与原创响应数据。

- Guya v2，搜索返回 6 部作品，所选作品解析 39 章，所选章节解析 22 页，图片请求保留源设置的 User-Agent。
- 拷贝漫画 v24，关键词查询返回 12 项，所选作品解析 12 章，所选章节解析 31 页。首张图片请求成功，原生解码得到 580 × 794 像素。

网络检查结果保存在 `.tooling/online-live-smoke-result.xml`。这些结果反映该时点与选定样本，其他站点和章节仍需验证。

## 兼容边界

- 浏览器 WebView、交互登录、Canvas 图片重组、动态筛选和首页编排尚未接入。
- 当前阅读页面采用 URL 图片格式。文本、内嵌图片和 ZIP 页面会显示格式不支持提示。
- 需要图片重组的源会明确提示尚未支持。源 key 格式的破坏性更新需要迁移支持。
- 图像、HTTP、WASM 执行和包大小有移动端资源限制，超出时会显示错误。
- 目标 iQOO Neo6 SE 真机尚未验证。自动验证采用 Robolectric 与原生图形，Android APK 的实际运行体验仍需手机确认。

## 最终产物

最终测试日志为 `.tooling/online-regression-final.log`，Release 日志为 `.tooling/online-release.log`。安装包输出为 `dist/MyViewer-0.2.0.apk`，版本号为 0.2.0，versionCode 为 12。

Release 构建及 APK v2 / v3 签名校验通过，与 0.1.10 的签名证书一致。安装包大小为 15,155,771 字节，SHA-256 为 `044957a39283c880495bb5042568bd56c95e3acf63458602fa7c277d2450e839`，校验文件为 `dist/MyViewer-0.2.0.apk.sha256`。
