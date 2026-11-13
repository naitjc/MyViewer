# 0.3.3 小说源验证结果

验证日期为 2026-11-13，使用 MyViewer 实际解析器进行实时请求。候选来自 [speauty](https://github.com/speauty/legado.sources)、[XIU2](https://github.com/XIU2/Yuedu) 和 [tickmao](https://github.com/tickmao/Novel)。

共验证 353 条书源定义，172 条通过，按站点地址去重后整合 120 个书源。

每条书源验证搜索、作品详情和目录，抽取首个可读章节及中间可读章节的正文。仅有一个可读章节时验证一个样本。正文检查有效文字、解码替换符及重复内容。测试关键词优先采用书源定义中的 checkKeyWord，否则使用红楼梦和三国演义。

结果反映当前解析器、本机网络与当次抽样。HTTP 404、TLS、超时、无匹配结果和不兼容规则均记录为未通过，可在应用中重新验证。整合包仅纳入当次通过的去重条目，保留作者元数据。

| 仓库 | 名称 | 站点 | 结果 | 阶段与记录 |
| --- | --- | --- | --- | --- |
| speauty | 256中文[NSFW] | https://www.256zw.cc/ | 未通过 | 搜索 · java.security.cert.CertPathValidatorException: Trust anchor for certification path not found. |
| speauty | 54看书 | https://m.54kanshu.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| speauty | 七猫中文网 | https://www.qimao.com/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| speauty | 书海阁 | https://book.feius.live | 未通过 | 搜索 · connection closed |
| speauty | 爱下书小说网 | http://www.aixiashu.la/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| speauty | 玄幻阁小说网[NSFW] | http://www.xuanhuange.info/ | 未通过 | 搜索 · timeout |
| speauty | 笔仙阁 | https://www.bixiange.top/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| XIU2 | 69书吧 | https://69shuba.cx | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| XIU2 | 69书吧.com | https://www.69shuba.com | 未通过 | 搜索 · invalid flag 'a' after regular expression (novel-source#1) |
| XIU2 | 八一中文 | https://www.81zw2.com | 未通过 | 搜索 · Wrapped java.lang.IllegalArgumentException: 小说网页请求失败（HTTP 403）。 (novel-source#1) |
| XIU2 | 和图书 | https://hetushu.com | 未通过 | 搜索 · illegally formed XML syntax (novel-source#1) |
| XIU2 | 大文学无错小说网 | https://www.wcxsw.org | 未通过 | 搜索 · Wrapped java.lang.IllegalArgumentException: 小说网页请求失败（HTTP 403）。 (novel-source#1) |
| XIU2 | 天天看小说 | https://cn.ttkan.co | 未通过 | 搜索 · 小说网页请求失败（HTTP 403）。 |
| XIU2 | 就爱文学 | http://www.92xs.info | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| XIU2 | 得奇小说网 | https://www.deqixs.com | 未通过 | 搜索 · Read error: ssl=0x78733121c8: Failure in SSL library, usually a protocol error error:10000410:SSL routines:OPENSSL_internal:SSLV3_ALERT_HANDSHAKE_FAILURE (/Users/runner/work/_temp/boringssl/ssl/tls_record.cc:491 0x786f350840:0x00000003) error:1000009a:SSL routines:OPENSSL_internal:HANDSHAKE_FAILURE_ON_CLIENT_HELLO (/Users/runner/work/_temp/boringss |
| XIU2 | 快书网 | https://www.kuaishu5.com | 未通过 | 搜索 · java.security.cert.CertPathValidatorException: Trust anchor for certification path not found. |
| XIU2 | 思路客 | https://www.siluke.com | 未通过 | 搜索 · Wrapped javax.net.ssl.SSLHandshakeException: connection closed (novel-source#1) |
| XIU2 | 手机小说 | https://www.shoujix.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| XIU2 | 来看文学 | https://m.laikan9.com | 未通过 | 搜索 · TypeError: Cannot find function longToast in object [object Object]. (novel-source#18) |
| XIU2 | 武林中文网 | https://www.50zw.so | 未通过 | 搜索 · connection closed |
| XIU2 | 独步小说网 | https://www.dbxsd.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| XIU2 | 番茄小说2 | https://fqapi.komr.cn | 未通过 | 搜索 · json string can not be null or empty |
| XIU2 | 艾途小说 | https://m.aitbooks.com | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| XIU2 | 起点中文 | https://www.qidian.com | 未通过 | 搜索 · ReferenceError: "cookie" is not defined. (novel-source#6) |
| XIU2 | 速读谷 | https://www.sudugu.co | 未通过 | 搜索 · timeout |
| XIU2 | 酷我小说 | http://appi.kuwo.cn | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| XIU2 | 铅笔小说 | https://www.23qb.com | 未通过 | 搜索 · 小说网页请求失败（HTTP 403）。 |
| XIU2 | 阅友小说 | http://m.suixkan.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| XIU2 | 顶点小说 ddxs | https://www.ddxs.com | 未通过 | 搜索 · Wrapped javax.net.ssl.SSLHandshakeException: java.security.cert.CertPathValidatorException: Trust anchor for certification path not found. (novel-source#1) |
| tickmao | 一七小说 | https://m.1qxs.com | 未通过 | 正文 · 小说网页请求失败（HTTP 403）。 |
| tickmao | 一七小说 | https://m.1qxs.com#🎃 | 未通过 | 正文 · 小说网页请求失败（HTTP 403）。 |
| tickmao | 一流书院 | http://www.1616ys.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 一流书院 | http://www.1616ys.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 一起看看 | http://m.17kankan.org#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 万层中文 | http://www.oneceng.com | 未通过 | 搜索 · Read timed out |
| tickmao | 万层中文 | http://www.oneceng.com/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 万象书城 | https://www.wxscs.com# | 未通过 | 详情与目录 · Could not parse query 'put:{n:"[property$=book_name]': unexpected token at '{n:"[property$=book_name]' |
| tickmao | 万象书城吧 | https://www.wxscs.com | 未通过 | 详情与目录 · Could not parse query 'put:{n:"[property$=book_name]': unexpected token at '{n:"[property$=book_name]' |
| tickmao | 三九九小说-夜明空 | http://www.399xs.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 三五小说 | http://www.35ge.info | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 三五小说 | http://www.swsk.org#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 三六五小说网 | http://wap.shukuge.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 下书看 | http://www.xiashukan.com/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 东滩小说 | http://www.dongtanxs.com## | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 中华典藏 | https://www.zhonghuadiancang.com | 未通过 | 搜索 · connection closed |
| tickmao | 中文看 | http://wap.zwkan.cc/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 久久书院 | http://www.99sy.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 久久小说网 | https://www.aijjxs.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 九一版主 | http://m.91banzhu.store/book/index.html | 通过 | 完成 · 搜索、详情、目录及 1 个正文样本通过 |
| tickmao | 九五五九小说 | https://www.95590.org | 未通过 | 搜索 · connection closed |
| tickmao | 乡土小说 | https://www.xiangtxsw.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 书斋阁 | https://www.shuzhaige.com | 未通过 | 正文 · 小说源正则递归过深，请更新书源。 |
| tickmao | 书满屋小说网 | https://m.shumw.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 书满屋网 | https://m.shumanwu.net | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 书满屋网 | https://m.shumanwu.net#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 书满屋网 | https://m.shumw.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 云轩阁小说网 | http://www.yunxuange.cc/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 五二 | https://www.52shuku.net/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 五二书库 | https://po.net | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 五二书库 | https://www.52shuku123.org | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 五二书库 | https://www.po.net | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 五二书库 | https://www.po5.net | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 五二书库 | https://www.po5.net/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 五二小说 | http://www.xs52.info#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 五八书阁 | http://m.5859ycdh.com | 未通过 | 正文 · 小说网页文字解码失败，请更新书源后重试。 |
| tickmao | 亲亲小说 | http://www.qq787.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 何以笙箫默 | http://www.yetianlian.net#🎃 | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 何以笙箫默小说电脑 | http://www.yetianlian.net | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 偶遇小说 | https://m.oue8.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 傲世中文网 | http://www.23zw.org | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 元尊笔趣阁 | http://www.yuanzunge.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 元尊阁小说网 | http://www.yuanzunge.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 免费小说 | https://cn.zhys.tw | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 免费追书 | https://cxb-pro.cread.com##@遇知 | 未通过 | 详情与目录 · net.minidev.json.parser.ParseException: Unexpected End Of File position 646: null |
| tickmao | 免费追书 | https://cxb-pro.cread.com/##@遇知 | 未通过 | 详情与目录 · net.minidev.json.parser.ParseException: Unexpected End Of File position 648: null |
| tickmao | 全免小说 | http://qmbook.taoyuewenhua.net#🎃 | 未通过 | 详情与目录 · net.minidev.json.parser.ParseException: Unexpected End Of File position 902: null |
| tickmao | 全免小说 | http://qmbook.taoyuewenhua.net/#@遇知 | 未通过 | 详情与目录 · net.minidev.json.parser.ParseException: Unexpected End Of File position 902: null |
| tickmao | 全本同人 | https://www.qbtr.cc##喜静 | 未通过 | 搜索 · 这个小说源需要浏览器验证，当前尚未支持。 |
| tickmao | 全本同人小说 | https://www.qbtr.cc | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 全本小说网 | https://quanben.io | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 八六读书 | http://www.86dushu.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 八六读书 | http://www.86dushu.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 八块小说 | http://www.8kbook.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 八块小说网 | http://www.8kbook.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 八零电子 | http://wap.80zw.la#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 八零电子 | http://wap.80zw.la/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 六 | http://wap.shukuge.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 关于小说 | http://www.rulianshi.org# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 关于小说 | http://www.rulianshi.org#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 十八书屋 | https://www.shibashuwu.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 十八书屋 | https://www.shibashuwu.net# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 去看小说 | http://www.7kbook.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 去看小说网 | http://www.7kbook.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 台湾小说网 | https://twshux.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 品书小说网 | http://www.pinshu8.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 品书斋子 | https://www.pinshuzhai.com  | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 品书楼小说网 | http://www.pinshulou.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 品书楼小说网 | http://www.pinshulou.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 四月天 | https://www.4yt.net/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 四月天网 | https://www.4yt.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 圣武书屋 | https://www.sswsk.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 多看阅读 | https://www.dkmi.com# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 多看阅读 | https://www.dkmi.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 多看阅读 | https://www.dkmi.com/#wenmoux | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 多看阅读 | https://www.duokan.com# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 多看阅读 | https://www.duokan.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 夜伴书屋 | https://www.ybswa.com | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 夜伴书屋 | https://www.ybswa.com/ | 未通过 | 详情与目录 · Could not parse query 'put:{n:"[property$=book_name]': unexpected token at '{n:"[property$=book_name]' |
| tickmao | 夜伴书屋 | https://www.yybsw.com#🎃 | 未通过 | 详情与目录 · Could not parse query 'put:{n:"[property$=book_name]': unexpected token at '{n:"[property$=book_name]' |
| tickmao | 夜天连看 | http://m.yetianlian.org/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 大唐小说 | https://www.dtxsw.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 天下书盟 | http://www.fbook.net# | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 天书小说网 | http://www.tshu8.com | 未通过 | 详情与目录 · ReferenceError: "org" is not defined. (novel-source#3) |
| tickmao | 天书小说网 | http://www.tshu8.com#🎃 | 未通过 | 详情与目录 · ReferenceError: "org" is not defined. (novel-source#3) |
| tickmao | 天地中文 | http://m.tiandizw.com | 未通过 | 搜索 · ReferenceError: "cookie" is not defined. (novel-source#3) |
| tickmao | 天堂向左深圳往右 | http://tiantangxiangzuoshenzhenwangyou.qwyd.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 天堂深圳 | http://tiantangxiangzuoshenzhenwangyou.qwyd.net/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 太子爷 | http://www.tzy2.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 太子爷网 | http://www.tzy2.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 奇书网 | http://m.xqishuta.net | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 奇人小说 | http://www.qirenxing.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 奇人小说网 | http://www.qirenxing.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 小说 | http://www.xsabc.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 小说屋 | https://www.xiaoshuo5.com | 未通过 | 详情与目录 · ReferenceError: "org" is not defined. (novel-source#3) |
| tickmao | 小说阅读 | http://www.xyetianlian.com | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 平板小说网 | http://wap.qiuyelou.com##旅途 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 平板电子书网 | http://wap.qiqixs.net#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 平板电子网 | http://wap.qiqixs.la/  | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 平板电子网 | http://wap.qiqixs.net/  | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 开心文学网 | https://www.kxwx.org#🎃 | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 得奇小说网 | https://www.deqitxt.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 御书房 | https://www.neomice.net | 未通过 | 正文 · TypeError: Cannot find function base64DecodeToByteArray in object [object Object]. (novel-source#9) |
| tickmao | 必读居 | http://www.biduju.net | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 必读居 | http://www.biduju.net/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 快眼看书 | http://www.xbotaodz.com# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 快眼看书 | http://www.xbotaodz.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 快读网 | http://www.kuuai.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 思兔阅读 | https://www.sto66.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 思路客 | http://www.isiluke.org/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 思路客 | http://www.siluke.cc#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 思路客 | http://www.siluke.cc/#pb1101 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 思路客 | http://www.silukezw.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 思路客 | http://www.silukezw.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 情人游戏 | https://m.qingrenyouxi.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 情人游戏 | https://m.qingrenyouxi.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 手机版平板电子书网 | http://wap.qiqixs.info | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 手机版平板电子书网 | http://wap.qiqixs.info#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 手机看书 | https://sjks8.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 手机看书 | https://www.sjks88.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 手机看书 | https://www.sjks88.com# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 抖音小说 | https://www.douyinxs.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 抖音小说二零二六零九一九 | https://www.douyinxs.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 搜读小说 | http://www.sdxs.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 放肆文学 | https://funs.me | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 文学资料 | http://wenxuebank.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 文学资料网 | http://wenxuebank.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 新御宅 | https://m.xyuzhaiwu10.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 新御宅屋 | https://m.xyuzhaiwu10.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 新御宅屋 | https://www.layuzhaiwu.xyz | 未通过 | 搜索 · Could not parse query '_src': unexpected token at '_src' |
| tickmao | 新暖才文学网 | https://m.xncwxw.net | 通过 | 完成 · 搜索、详情、目录及 1 个正文样本通过 |
| tickmao | 新百强小说 | http://m.iinbqg.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 新百强小说 | http://m.xinbqg.com | 未通过 | 搜索 · 这个小说源需要浏览器验证，当前尚未支持。 |
| tickmao | 新百强小说 | http://www.xinbqg.la/ | 未通过 | 搜索 · illegal character: # (novel-source#1) |
| tickmao | 无奈书库 | https://www.52shuku123.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 无奈书库 | https://www.52shuku123.org#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 无影小说网 | https://www.seying.cc/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 无极书院 | http://m.wjzxchina.com | 未通过 | 正文 · 小说网页文字解码失败，请更新书源后重试。 |
| tickmao | 无错 | https://www.wcshuba.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 望书阁网 | http://wap.wangshugu.info | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 格格党 | https://www.haogushi88.cc#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 格格党八八 | https://www.haogushi8.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 格格党小说 | https://www.alshuan.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 梦想家文学 | http://www.mxjtedu.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 梦想家文学 | http://www.mxjtedu.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 梦远书城 | https://www.mengyuanshuchengcn.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 梧桐中文 | https://www.wtzw.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 梧桐中文 | https://www.wtzw.com#♤yc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 梧桐中文 | https://www.wtzw.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 梧桐中文网 | https://www.wtzw.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 森林 | http://23.224.242.55/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 殓师灵异 | http://www.rulianshi.cc/#pb1101 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 殓师灵异 | http://www.rulianshi.org/#♤pb | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 永久小说 | http://www.yjxsw.com | 未通过 | 详情与目录 · 小说网页请求失败（HTTP 404）。 |
| tickmao | 永久小说网 | http://www.yjxsw.com#🎃 | 未通过 | 详情与目录 · 小说网页请求失败（HTTP 404）。 |
| tickmao | 海棠文学 | https://m.haitangwx.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 海棠文学 | https://m.haitangwx.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 涩趣阁 | http://www.sequge.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 滋滋读书 | http://www.zzdushu.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 滋滋读书 | http://www.zzdushu.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 灰色小说网 | http://www.huigre.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书 | http://www.aixiashu.la#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书吧 | http://www.aixiashu.la/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书吧 | https://www.aixiawx.com/#pb1101 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书嘚 | http://www.aixiawx.com/#嘚嘚21.0201 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书小说网 | http://www.aixiashu.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书小说网 | https://www.aixiaxsw.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱下书看 | http://www.xiashukan.com | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 爱下书网 | http://www.aixiashu.info | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| tickmao | 爱下书网 | http://www.aixiashu.info# | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 爱下书网 | http://www.aixiashu.org | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 爱下书网 | https://www.aixiaxsw.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱久久小说 | https://m.ijjxsxzw.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱书小说网 | http://www.aishu8.org | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱看小说 | http://m.2kk.la#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 爱看小说 | http://m.ixsw.cc | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 爱看小说 | http://m.ixsw.info | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 独步小说 | https://www.dbxsd.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 独步小说 | https://www.dbxsn.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 玩文学 | http://www.baowx.com | 未通过 | 正文 · 小说源返回了无效的网页地址。 |
| tickmao | 玩文学 | http://www.wanwx.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 玩文学 | https://www.wanwx.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 电子书五二零 | http://txt520.com/ | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 电子书五二零小说 | http://txt520.com#🎃 | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 电子书图书 | http://www.bookshuku.org | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 疯情书库 | https://fqbook.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 疯情书库 | https://www.aabook.cyou/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 疯读小说 | https://fiction.fengduxiaoshuo.com# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 疯读小说 | https://fiction.fengduxiaoshuo.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 疯读小说 | https://touchlife.cootekservice.com#温暖满怀 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 疯读小说 | https://touchlife.cootekservice.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 看书 | https://uukanshu.cc | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 看书 | https://www.uukan.org | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 看小说网 | https://kanxiaoshuo.org/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 眼小说网 | http://www.eyxsw.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 破万卷吧 | http://www.powanjuan.cc/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 破万卷网 | http://www.powanjuan.cc | 未通过 | 正文 · 小说网页文字解码失败，请更新书源后重试。 |
| tickmao | 神马小说 | http://www.samsbook.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 神马小说网 | http://www.samsbook.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 稻草书屋 | https://www.dcrbk.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 稻草书屋 | https://www.iidcr.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 稻草人书屋 | https://www.dcrbk.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 空白小说 | http://www.swsk.org# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 穿越小说 | http://m.kk169.com | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| tickmao | 穿越小说 | http://m.kk169.com/##@遇知 | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| tickmao | 穿越小说 | http://m.kk169.net | 未通过 | 搜索 · illegal character: # (novel-source#1) |
| tickmao | 穿越小说 | http://www.kk169.la/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 穿越小说 | http://www.kk169.net#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 穿越小说 | http://www.kk169.net/ | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| tickmao | 笔仙阁子 | https://www.bixiange.top# | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔尚小说 | https://www.bsxiaoshuo.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔尚小说 | https://www.bsxiaoshuo.com#yc1101 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔尚小说 | https://www.bsxiaoshuo.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣网 | http://www.bqgu.cc | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://8biquge.com | 未通过 | 详情与目录 · timeout |
| tickmao | 笔趣阁 | http://m.bqgcn.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://m.bqgcn.net#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://m.woaisaicheshou.com/##@遇知 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://m.xinbqg.com#🎃 | 未通过 | 搜索 · 这个小说源需要浏览器验证，当前尚未支持。 |
| tickmao | 笔趣阁 | http://m.xinbqg.la#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | http://wap.biqige.info | 未通过 | 详情与目录 · 小说网页请求失败（HTTP 404）。 |
| tickmao | 笔趣阁 | http://wap.biqige.info/ | 未通过 | 详情与目录 · 小说网页请求失败（HTTP 404）。 |
| tickmao | 笔趣阁 | http://www.8biquge.com | 未通过 | 详情与目录 · 小说网页请求失败（HTTP 404）。 |
| tickmao | 笔趣阁 | http://www.biquge.xin | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://www.biqugem.cc | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://www.biquluo.com#🎃 | 未通过 | 搜索 · timeout |
| tickmao | 笔趣阁 | http://www.biquluo.com/ | 未通过 | 搜索 · 小说网页请求失败（HTTP 502）。 |
| tickmao | 笔趣阁 | http://www.biquy.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://www.bqgz.cc | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | http://www.miaobige.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | http://www.miaobige.cc#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://m.biquge.hk/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | https://m.biquge.tv#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://m.qbiqus.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://m.qbiqus.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.biquge5200.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.biquge5200.cc#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.biquge7.xyz# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.biquge7.xyz#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.biquges123.com | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 笔趣阁 | https://www.biquges123.com#🎃 | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 笔趣阁 | https://www.biqvge99.cc/#pb1101 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.bixiange.top#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | https://www.bqquge.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | https://www.bqquge.org | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | https://www.byqg.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.conglianhao.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 笔趣阁 | https://www.seekchimp.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁 | https://www.seekchimp.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁子 | http://www.biquge.site | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔趣阁江 | http://m.bqgcn.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 笔迷读 | https://www.bimidu.com/##@遇知 | 未通过 | 搜索 · illegal character: # (novel-source#1) |
| tickmao | 笔迷读吧 | https://www.bimidu.com/ | 未通过 | 搜索 · illegal character: # (novel-source#1) |
| tickmao | 第一小说 | http://www.dyxs.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 第一版主 | http://m.diyibanzhu.quest/#🚀 | 未通过 | 正文 · unterminated regular expression literal (novel-source#180) |
| tickmao | 第九中文网 | http://www.d9cn.cc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 米读 | https://api.midureader.com/##@遇知 | 未通过 | 搜索 · Unclosed group near index 9 (getBrief |
| tickmao | 米读小说 | https://api.midureader.com# | 未通过 | 搜索 · Unclosed group near index 9 (getBrief |
| tickmao | 精华书阁 | https://m.jhssd.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 精华书阁 | https://m.jhsssd.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 精华书阁 | https://wap.jhssd.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 精品小说 | https://jpxs123.cc/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 精品小说网 | http://www.jpxs123.cc/##@遇知 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 红叶书斋 | https://m.shufahouse.com | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 红叶书斋 | https://m.shufahouse.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 肉电子书 | http://www.rstxt.com/#🍖 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 肉电子书-δ | http://www.rstxt.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 脸红心跳 | https://wap.powenwu3.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 脸红心跳 | https://wap.powenwu3.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 腾讯浏览器 | https://novel.html.qq.com/ | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 腾讯阅读 | https://novel.html5.qq.com/ | 未通过 | 详情与目录 · 没有解析到小说章节，请检查书源规则或站点状态。 |
| tickmao | 艾克小说网 | http://www.ik258.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 芙蓉小说 | http://www.frtxt.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 芙蓉小说-δ | http://www.frtxt.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 茶马小说 | https://www.chamabooks.net | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 茶马小说 | https://www.chamabooks.net#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 菠萝包轻小说 | https://api.sfacg.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 菠萝包轻小说 | https://api.sfacg.com已校验 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 菠萝包轻小说 | https://minipapi.sfacg.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 菠萝包轻小说 | https://minipapi.sfacg.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 落霞小说 | https://luoxiadushu.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 落霞小说 | https://www.luoxia123.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 落霞小说 | https://www.luoxia123.com# | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 落霞读书 | https://luoxiadushu.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 话本小说 | https://www.ihuaben.com | 未通过 | 搜索 · TypeError: Cannot find function getWebViewUA in object [object Object]. (novel-source#2) |
| tickmao | 话本小说 | https://www.ihuaben.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 读乐星空 | https://z.dlxk.com#🎃 | 未通过 | 搜索 · connection closed |
| tickmao | 读乐星空 | https://z.dlxk.com/ | 未通过 | 搜索 · connection closed |
| tickmao | 读书网 | https://www.dushu.com | 未通过 | 搜索 · Value Referer of type java.lang.String cannot be converted to JSONObject |
| tickmao | 豆花文学 | https://www.douhuawenxue.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 豆花文学 | https://www.douhuawenxue.com/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 轻之文库 | https://www.linovel.net | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 轻之文库 | https://www.linovel.net#mitm | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 辣小说网 | https://www.txt520.org/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 追书网 | https://www.zhuishu.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 酷书阁 | https://www.kushuge.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 酷书阁小说网 | https://www.kushuge.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 酷我小说 | http://appi.kuwo.cn | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 酷我小说 | http://appi.kuwo.cn#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 错层小说 | https://m.cuoceng.com/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 错层小说 | https://www.cuoceng.org/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 错层小说网 | https://m.cuoceng.org/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阅友小说 | http://m.suixkan.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阅友小说 | http://m.suixkan.com# | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阅友小说 | http://m.suixkan.com#♤yc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阅友小说 | https://sma.yueyouxs.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阅友小说 | https://sma.yueyouxs.com#♤yc | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阳光中文网 | http://www.ygzww.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 阳光中文网 | http://www.ygzww.com#🎃 | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 青藤文学网 | https://www.deanmachine.net | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 非凡看书 | https://www.feifanks.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 靠谱小说 | http://www.kptxt.com | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 靠谱小说 | http://www.kptxt.com#🎃 | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 顶点 | https://www.biquge345.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 顶点小说 | https://www.dingdian888.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 顶点小说 | https://www.dingdian888.com/ | 未通过 | 搜索 · 测试关键词没有匹配作品。 |
| tickmao | 顶点小说网 | http://www.dingdianxiaoshuo.com | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 顶点小说网 | https://www.biquge99.cc/ | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
| tickmao | 飞卢小说 | https://b.faloo.com##男生标签 | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 飞卢小说 | https://b.faloo.com#🎃 | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 飞卢小说 | https://b.faloo.com/ | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 飞卢男生标签 | https://b.faloo.com男生标签 | 未通过 | 正文 · 没有解析到章节正文，请检查书源或完成站点授权。 |
| tickmao | 饭饭小说 | https://www.ffxs8.com/ | 未通过 | 正文 · 小说网页文字解码失败，请更新书源后重试。 |
| tickmao | 饿狼小说 | http://m.elkoparts.info | 通过 | 完成 · 搜索、详情、目录及 2 个正文样本通过 |
