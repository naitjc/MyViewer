# MyViewer

Android 本地 TXT 小说 / ZIP 漫画阅读器。主要针对 iQOO Neo6 SE 使用，最低 Android 8.0（API 26）。

## 功能

- 封面书架，小说 / 漫画筛选，按最近阅读、书名或导入时间排序。
- 系统文件选择器多选文件，或授权文件夹后扫描子目录。
- 每次导入选择直接读取原文件，或复制到应用保存独立副本。
- 小说提供目录、手动书签、分页 / 连续阅读、平移 / 仿真翻页设置。
- 字号、行距、纸色 / 浅色 / 护眼 / 深色背景、应用内亮度设置。
- 漫画提供逐页 / 连续阅读、左右阅读方向、双指缩放与双击放大；连续阅读时双击或双指进入当前图片的放大视图。
- 阅读历史和自动进度保存；清空历史、移出书架不删除书签或进度。
- 单独清理漫画图片缓存，图片缓存上限 256 MB。

小说点击中部打开菜单，点击两侧或左右滑动翻页。漫画分页时左右滑动翻页，点击图片打开菜单。应用内功能可离线使用。

## 安装与构建

已构建的安装包见 [GitHub Releases](https://github.com/naitjc/MyViewer/releases/latest)。源码仓库不保存签名密钥、本机配置与构建缓存。

最终签名体验包输出位置：`dist/MyViewer-0.1.0.apk`。转到手机上安装即可。

Android Studio 可直接打开本项目。命令行需要 JDK 17 或以上、Android SDK 36 与 Build Tools 36.0.0：

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

本机可使用封装脚本，它会选择已安装的 JDK 和 SDK，将构建缓存写入本项目的 `.tooling/`：

```sh
./scripts/build.sh :app:assembleDebug
./scripts/build.sh :app:testDebugUnitTest :app:lintDebug
./scripts/sign-release.sh
```

签名脚本首次运行时在 `.signing/` 创建应用专用签名材料；此目录已被 Git 忽略，也不作为构建缓存。保留它即可继续签署后续覆盖更新版本。后续版本同时递增 `app/build.gradle.kts` 中的 versionCode。

Debug 和最终签名包使用不同签名；首次手机试用以 `dist/` 中的签名包为准。

## 测试资料

`sample-books/` 是用本项目脚本生成的原创小样例，包含 UTF-8 / GB18030 小说、分页漫画和长条漫画。可把整个目录传到手机，通过文件夹扫描体验两种内容。

```sh
python3 scripts/make-samples.py
```

分页 ZIP 的文件写入顺序刻意为 1、10、2、3，用于检查阅读器是否按 1、2、3、10 显示；其中元数据标记从右到左。长条 ZIP 用于检查连续模式与页内位置恢复。

自动测试覆盖文本编码与章节位置、数字排序、分页边界、字号变化后的文字锚点、数据库进度隔离、复制与重复导入、ZIP 元数据及核心页面流程。验证结果在交付时写入 `VALIDATION.md`；页面截图位于 `.tooling/screenshots/`，真机性能与触感另行验证。

## 首版兼容范围

- 漫画：普通无密码 ZIP，内部 JPG / JPEG / PNG / WebP；没有递归打开嵌套 ZIP。
- TXT：UTF-8、UTF-16 BOM、GB18030 自动识别，阅读设置中可手选编码；上限 48 MB 原文件、约 2400 万 UTF-16 单元。
- 直接读取的原文件移动或授权失效后，需要重新定位；无法随机读取的 ZIP 来源需要使用复制导入。
- 索引与封面按本地内容生成；当前没有 EPUB、RAR、在线书源或手动备份界面。
- 替换原文件内容后，旧位置按原偏移尝试恢复，暂未实现跨版本内容的语义匹配。

需求记录见 [PROJECT_DESIGN.md](PROJECT_DESIGN.md)，工程设计建议见 [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md)。工程实际行为和本文件中的兼容范围优先于尚未验证的设计建议。
