The compiled upstream fixtures below come from the Aidoku Community Sources repository, distributed under its MIT license. See `LICENSE-MIT` in this directory.

- `en.guya-v2.aix`, retrieved 2026-11-11. [Source implementation](https://github.com/Aidoku-Community/sources/tree/main/sources/en.guya), [shared implementation](https://github.com/Aidoku-Community/sources/tree/main/templates/guya), [package](https://aidoku-community.github.io/sources/sources/en.guya-v2.aix).
- `zh.jmcomic-v3.aix`, retrieved 2026-11-12. [Source implementation](https://github.com/Aidoku-Community/sources/tree/main/sources/zh.jmcomic), [package](https://aidoku-community.github.io/sources/sources/zh.jmcomic-v3.aix).

Tests serve generated metadata and original test images through HTTP interceptors. They exercise the real source binaries without requiring a live content provider. The JM fixture covers encrypted API responses, page-context decoding and canvas strip reconstruction using an original colored-band image.
