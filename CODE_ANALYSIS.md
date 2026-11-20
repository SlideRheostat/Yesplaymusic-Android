# LiteMusicAndroid 项目代码分析报告

> 本文档以当前仓库源码（versionName 3.3.13 / versionCode 65，minSdk 19）为准重新校对，
> 已修正旧版文档中与实际实现不符的内容（旧版误写为 ExoPlayer / Room / Retrofit /
> Glide / Lottie 等依赖，以及 model/net/data 分包，实际均不存在）。
>
> **校对方式**：逐文件阅读 `app/src/main/java/**` 全部 36 个 Kotlin 文件（共约 7,853 行），
> 以及 `app/src/main/res` 全部 XML；并本地执行 `gradlew assembleDebug` 与
> `lintDebug`，在 Android 4.4（API 19）模拟器上完成主要页面与播放链路的运行验证。

---

## 一、项目概览

| 项 | 实际值 |
|---|---|
| 工程名 | `LiteMusic`（仓库目录名 LiteMusicAndroid，产品名 YesPlayMusic Mobile） |
| 源码包名 / namespace | `com.litemusic` |
| applicationId（安装身份） | `com.yesplaymusic.mobile` |
| 版本 | `versionName = "3.3.13"`，`versionCode = 65` |
| 语言 | Kotlin 100%（36 个 `.kt`，无 Java 源码） |
| 构建 | Gradle Kotlin DSL + AGP 9.4.1 + Gradle 9.9.0 |
| SDK | `minSdk 19` / `targetSdk 34` / `compileSdk 36` |
| UI | XML 布局 + Material Components 1.12（Material Design 3 DayNight） |
| 定位 | 轻量网易云音乐客户端：无 WebView、无重型三方库，兼容 Android 4.4 |

**构建要点**

- `app/build.gradle.kts` 只应用了 `com.android.application`，**没有显式应用 Kotlin 插件**，
  依赖 AGP 9.x 的内置 Kotlin 编译能力；`compileOptions` 为 Java 8。
- `debug/release` 均开启 R8 收缩（`isMinifyEnabled = true`）。minSdk 19 不支持原生 multidex，
  通过 R8 把方法数压到 64K 以内，未引入 `androidx.multidex` 依赖。
- `vectorDrawables.useSupportLibrary = true`，API 19/20 由 AppCompat 运行时解析矢量图标。
- 没有 signingConfig；`assembleDebug` 使用调试签名，同时具备 v1/v2 签名，可安装到 API 19。
- 根目录 `modify_version.py` 是早期手工改版本号脚本，内容仍停留在 3.3.5→3.3.6，
  与实际 3.3.13 脱节（见“八、已知问题”）。
- 仓库中存在多份历史 APK（3.0.8 ~ 3.3.8）与 `yesplaymusic-mobile/`（仅含 `.git`、`.idea`
  等元数据，无源码，可视为废弃目录）。

---

## 二、真实目录结构

```
LiteMusicAndroid/
├── build.gradle.kts                 # 仅声明 com.android.application 9.4.1 apply false
├── settings.gradle.kts              # 仓库配置 + include(":app")
├── gradle.properties                # AndroidX、并行构建、Xmx2048m
├── gradle/wrapper/                  # Gradle 9.9.0
├── local.properties                 # SDK 路径
├── modify_version.py                # 过期的版本号脚本
├── README.md / CHANGELOG.md
├── CODE_ANALYSIS.md                 # 本文件
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/litemusic/
        │   ├── App.kt                     # Application：初始化 + 主题
        │   ├── Models.kt                  # Song / Playlist / LyricLine / PlayRecordItem + Parser
        │   ├── ApiClient.kt               # HTTP 传输、多通道、Cookie、CSRF 恢复
        │   ├── Api.kt                     # 业务接口门面（29 个公开方法）+ 歌词合并算法
        │   ├── Eapi.kt                    # eapi AES-ECB 加解密 + MD5
        │   ├── Weapi.kt                   # weapi AES-CBC 双层 + RSA
        │   ├── Prefs.kt                   # SharedPreferences：登录态/设置/搜索历史/Cookie
        │   ├── AudioCache.kt              # 音频磁盘缓存（LRU + 元数据）
        │   ├── ImageLoader.kt             # 图片内存/磁盘缓存加载器
        │   ├── LrcParser.kt               # LRC 解析
        │   ├── Kana.kt                    # 罗马音 → 平假名
        │   ├── HistoryStore.kt            # 最近播放（50 首）本地歌单
        │   ├── player/
        │   │   ├── PlayerService.kt           # MediaPlayer 前台播放服务
        │   │   ├── PlayerCompat21.kt          # API 21+ MediaSession/MediaStyle 隔离
        │   │   ├── PlayerCompat26.kt          # API 26+ 通知渠道/前台服务隔离
        │   │   ├── PlayerCompat29.kt          # API 29+ 前台服务类型隔离
        │   │   └── MediaButtonReceiver.kt      # API 19/20 耳机/蓝牙媒体按键
        │   └── ui/
        │       ├── MainActivity.kt        # 三页签框架 + 自定义导航
        │       ├── HomeFragment.kt        # 首页推荐
        │       ├── SearchFragment.kt      # 搜索（全站/本地收藏范围）
        │       ├── MineFragment.kt        # 我的（竖屏列表 / 横屏音乐库）
        │       ├── PlayerActivity.kt      # 播放页（封面/歌词/进度）
        │       ├── PlaylistActivity.kt    # 歌单/每日推荐/我喜欢的/专辑 通用列表页
        │       ├── ArtistActivity.kt      # 歌手主页
        │       ├── LoginActivity.kt       # 扫码登录
        │       ├── SettingsActivity.kt    # 设置
        │       ├── HistoryActivity.kt     # 本地历史记录
        │       ├── CachedSongsActivity.kt # 已缓存歌曲
        │       ├── SongAdapter.kt         # 通用歌曲行适配器
        │       ├── MiniPlayerController.kt# 迷你播放条控制器
        │       ├── MiniProgressRing.kt    # 迷你播放器环形进度
        │       ├── NavDragLayout.kt       # 可拖拽导航容器
        │       ├── LoadingBar.kt          # M3 不确定线性加载条
        │       ├── SearchRootLayout.kt    # 点击搜索框外收起键盘
        │       ├── CenteredRippleLayout.kt# 涟漪热区居中
        │       └── LikedStore.kt          # 红心 id 内存集合
        └── res/
            ├── layout/       24 个（竖屏）
            ├── layout-land/   4 个（横屏：activity_main / activity_player / fragment_mine / item_song）
            ├── values/ + values-night/ + values-v21 / v23 / v27（M3 配色、主题、字符串 136 行）
            ├── drawable/ anim/ menu/ mipmap-*/ xml/
```

**分层事实**：项目并未创建 `model/`、`net/`、`data/` 包；数据模型与 `Parser` 合并在
`Models.kt`，网络层为根包下的 `ApiClient/Eapi/Weapi`，持久化为根包下的
`Prefs/HistoryStore/LikedStore/AudioCache/ImageLoader`。

---

## 三、技术栈与依赖（准确清单）

```kotlin
// app/build.gradle.kts（全部依赖）
implementation("androidx.core:core:1.13.1")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("androidx.viewpager2:viewpager2:1.0.0")
implementation("com.google.zxing:core:3.5.3")
```

| 能力 | 实际方案 |
|---|---|
| HTTP | `HttpURLConnection`（无 OkHttp / Retrofit，无连接池复用） |
| JSON | `org.json`（`JSONObject/JSONArray`，手动解析） |
| 异步 | `Executors` 线程池 / `Thread` + `Handler(Looper.getMainLooper())`，**无 Kotlin 协程** |
| 播放 | `MediaPlayer` + `AudioManager` 音频焦点 + `MediaSession`（API 21+） |
| 通知 | `Notification.Builder` + `Notification.MediaStyle`（API 21+）；19/20 用系统媒体图标三键 |
| 图片 | 自研 `ImageLoader`：`LruCache` 内存 + `cacheDir/images` 磁盘 LRU（96MB） |
| 音频缓存 | 自研 `AudioCache`：`filesDir/audio`，`{id}-{level}.aud` + `.meta`，按体积 LRU |
| 持久化 | `SharedPreferences`（无 Room / SQLite） |
| 二维码 | ZXing Core 生成位图（未使用 zxing-android-embedded） |
| 架构组件 | **无 ViewModel / LiveData / Navigation / DataBinding** |

**明确未使用**：ExoPlayer / media3、Room、Retrofit、OkHttp、Glide / Coil、Lottie、
PhotoView、Kotlin Coroutines、任何 DI 框架。旧版 `CODE_ANALYSIS.md` 中列出的这些依赖均不存在。

---

## 四、整体架构

```
┌──────────────────────────── UI 层（Activity / Fragment / Adapter / 自定义 View）────────────────────────────┐
│ MainActivity + Home/Search/Mine Fragment；PlayerActivity；PlaylistActivity；ArtistActivity；Settings…        │
│ 直接调用 Api.xxx{} 回调；MiniPlayerController 通过静态 listeners 监听播放服务                                  │
└───────────────────────────────────────┬───────────────────────────────────────────────────────────────────┘
                                        │ 直接调用（无 ViewModel 中介）
┌───────────────────────────────────────▼───────────────────────────────────┐   ┌──────────────────────────┐
│ Api.kt（业务门面：参数拼装、分页/分批、歌词合并、会话保存）                 │   │ PlayerService            │
│ Models.kt（Parser：JSONObject → Song/Playlist）                            │◄──┤ MediaPlayer + 队列 + 通知 │
└───────────────────────────────────────┬───────────────────────────────────┘   │ + MediaSession(21+)      │
                                        │                                        └───────────┬──────────────┘
┌───────────────────────────────────────▼───────────────────────────────────┐               │
│ ApiClient.kt：eapi / weapi / linuxapi / clientlog 四通道 + Cookie + CSRF 恢复│               │
│ Eapi.kt（AES-ECB）  Weapi.kt（AES-CBC + RSA）                               │               ▼
└───────────────────────────────────────┬───────────────────────────────────┘   ┌──────────────────────────┐
                                        │                                        │ AudioCache / ImageLoader │
                                        ▼                                        │ Prefs / HistoryStore     │
                              NetEase 服务端                                     └──────────────────────────┘
```

**架构特征**

1. **回调驱动**：所有 `Api` 方法签名为 `(Result<T>) -> Unit`，在 `ApiClient` 内切回主线程。
2. **单例 object**：`Api`、`ApiClient`、`Prefs`、`AudioCache`、`ImageLoader`、`HistoryStore`、`LikedStore`。
3. **播放服务静态持有**：`PlayerService.instance` + `PlayerService.listeners`（进程内、单 Activity 栈）。
4. **无状态持久层**：登录态、设置、搜索历史、历史歌单都在 `SharedPreferences`。

---

## 五、核心模块详解

### 5.1 数据模型与解析（`Models.kt`）

```kotlin
data class Song(
    val id: Long,
    val name: String,
    val artists: String,                 // "A/B" 拼接后的展示串
    val artistId: Long,                  // 首位歌手 id
    val artistList: List<Pair<Long, String>>, // 全部歌手 (id, 名称)，长按歌手选择用
    val album: String,
    val albumId: Long,
    val duration: Long,                  // 毫秒
    val cover: String?,
    val fee: Int,                        // 0 免费 1 VIP 2 付费 3 低音质免费（needVip 还包含 4）
    val offline: Boolean,                // privilege.st == -200/-101
) {
    val needVip: Boolean get() = fee == 1 || fee == 4
    val playable: Boolean get() = !offline   // 目前未被使用
}

data class Playlist(
    val id: Long, val name: String, val cover: String?,
    val playCount: Long, val trackCount: Int,
    val subtitle: String? = null,        // 歌手名 / 年份等
    var subscribed: Boolean = false,
    val albumCount: Int = 0,
    val localCover: Int = 0,             // 本地 drawable（历史记录封面）
    val history: Boolean = false,
)

data class LyricLine(val time: Long, val text: String, val trans: String? = null, val roma: String? = null)
data class PlayRecordItem(val song: Song, val playCount: Int)
```

`Parser`（同文件）兼容 eapi/weapi 两种字段命名：

- `song()`：`ar`/`artists`、`al`/`album`、`dt`/`duration`、`privilege.st` 判下架。
- `playlist()`：`picUrl`/`coverImgUrl`、`trackCount`/`trackNumber`、`subscribed`/`sub`。
- 过滤规则：`id <= 0` 或名称为空的条目直接丢弃。

### 5.2 网络层（`ApiClient.kt` + `Eapi.kt` + `Weapi.kt`）

**四个通道**

| 通道 | 地址 | 用途 | 加密 |
|---|---|---|---|
| eapi | `https://interface3.music.163.com/eapi/` | 默认业务通道（推荐/搜索/详情/播放地址/歌词/收藏/账号/二维码） | `Eapi`：`nobody{url}use{json}md5forencrypt` → MD5 → AES-ECB(`e82ckenh8dichen8`) |
| weapi | `https://music.163.com/weapi/` | 收藏列表/排行/关注列表的**显式失败回退**，以及 CSRF 会话恢复 | `Weapi`：AES-CBC 双层（预设 key + 随机 16 位） + RSA(encSecKey) |
| linuxapi | `https://music.163.com/api/linux/forward` | 艺术家关注/取关（风控最宽松） | AES-ECB(`rFgB&h#%2?^eDg:Q`) |
| clientlog | `https://clientlog.music.163.com/eapi/feedback/weblog` | 听歌打卡（startplay / play） | eapi 同款 |

> 注意：**不存在“eapi → weapi → linuxapi 自动三级降级”**。`ApiClient.get()` 只走 eapi；
> 需要回退的接口在 `Api.kt` 中显式判断 `isFailure` 后调用 `getWeapi()`；关注艺术家固定走 `getLinuxApi()`。

**关键实现**

- 执行器：`Executors.newSingleThreadExecutor()`（**所有 API 请求串行**），结果投递回主线程。
- 请求头：iPhone 客户端 JSON header 作为 Cookie 回传（`MUSIC_U` / `MUSIC_A` / `__csrf` / `deviceId` 等）。
- `captureCookies()`：抓 `Set-Cookie`，eapi 通道拒绝 `__csrf`（interface3 下发的 csrf 与 web 会话不绑定）。
- CSRF 自愈：`ensureCsrf()` → `seedSiteCookies()`（GET music.163.com）→ `refreshCsrf()`（账号接口重发）；
  15 秒节流，避免 429。
- 错误模型：`ApiException(code, message)`；`checkCode()` 放行 200/201/800~803/302。
- eapi 响应为密文（非 `{` 开头）时用 `Eapi.decrypt()` 兜底。
- `sendWeblog()` 单独构建 header（macOS 客户端参数），失败仅 `Log.w`，不影响播放。

### 5.3 业务接口（`Api.kt`，29 个公开方法）

| 功能域 | 方法 → eapi 路径 | 说明 |
|---|---|---|
| 推荐 | `personalized` → `personalized/playlist` | 无分页，仅首屏 |
| 歌单广场 | `playlistSquare` → `playlist/list` | 支持 offset，回传 `more` |
| 每日推荐 | `recommendSongs` → `v3/discovery/recommend/songs` | `data.dailySongs` |
| 搜索 | `search/searchPlaylists/searchAlbums/searchArtists` → `cloudsearch/pc` | type=1/1000/10/100 |
| 歌单详情 | `playlistDetail` → `v6/playlist/detail` | tracks 不足时按 `trackIds` 分批补全，封顶 300 |
| 歌曲详情 | `songDetail` → `v3/song/detail` | 每批 100 首串行合并 |
| 播放地址 | `songUrl` → `song/enhance/player/url/v1` | 读取 `Prefs.level` 与 encodeType |
| 歌词 | `lyric` → `song/lyric` | lrc + tlyric + romalrc 合并 |
| 收藏单曲 | `like` → `radio/like` / `likelist` → `song/like/get` | 需要登录 |
| 用户歌单 | `userPlaylist` → `user/playlist` | `specialType==5` 摘出“我喜欢的音乐” |
| 收藏专辑 | `albumSublist` → `album/sublist`（失败回退 weapi） | 多结构兼容解析 |
| 专辑详情 | `albumDetail` → `v1/album/{id}` | 同时返回 Playlist 与歌曲 |
| 歌手 | `artistTopSongs` → `artist/top/song`<br>`artistFollowed` → `v1/artist/{id}`<br>`artistAlbums` → `artist/albums/{id}`（50/页，封顶 2000）<br>`followedArtists` → `artist/sublist`（失败回退 weapi） | |
| 写操作 | `artistSub` → linuxapi `artist/sub|unsub`<br>`playlistSubscribe` → `playlist/subscribe|unsubscribe`（收藏带 `X-antiCheatToken`）<br>`albumSub` → `album/sub|unsub` | |
| 登录 | `qrKey` → `login/qrcode/unikey`；`qrCheck` → `login/qrcode/client/login`；`account` → `w/nuser/account/get`；`logout` → `logout` | 803 时自动 `account()` 保存资料 |
| 排行/打卡 | `playRecord` → `v1/play/record`（失败回退 weapi）；`scrobble` → clientlog `startplay` + `play` | |

### 5.4 歌词系统

- `LrcParser.parse()`：正则 `\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]`，
  支持一行多时间标签、1~3 位小数（按百分秒/十毫秒/毫秒换算），解析后按时间排序。
- `Api.mergeTranslation()`：
  1. 时间戳精确匹配（`alignAux`），制作信息行（`CREDIT_RE`：作词/作曲/编曲…）不参与配对；
  2. 剩余行数量一致 → 按序对齐；数量不一致 → 单调就近配对，容差 `1000ms`，每行只消费一次；
  3. 罗马音经 `Kana.romajiToHiragana()` 转平假名后挂到 `roma`。
- `Kana`：约 100 条映射表，支持拗音、促音（重叠辅音）、长音符；未识别字符原样保留。
- **渲染**：`PlayerActivity` 用 `ScrollView + LinearLayout` 动态创建每行两个 `TextView`，
  不是 RecyclerView；高亮行放大 1.0/透明度 0.88，其余 0.95/0.58（250~300ms M3 曲线）。

### 5.5 播放引擎（`player/`）

`PlayerService` 是 `Service`（前台、`mediaPlayback`），**不是 ExoPlayer**：

- 队列：`queue: List<Song>`、`index`、`mode`（0 顺序 / 1 单曲 / 2 随机，持久化到 `Prefs.playMode`）。
- 随机回溯：`shuffleHistory` 记录实际播放顺序；随机下一首前压入当前索引，
  上一首按栈回退（无历史时退回队列上一首）；切换播放模式或更换队列时清空。
- 静态入口：`PlayerService.play(ctx, songs, index)` / `playCached(...)`；服务未起时存入
  `pending`，`ensureStarted()` 后 `onCreate` 消费。
- 播放优先级：命中 `AudioCache` → 直接本地播放；否则 `Api.songUrl()` → `AudioCache.fetch()`
  （**整首下载完**）→ `MediaPlayer.setDataSource(path)` → `prepareAsync()`。
- 失败保护：`consecutiveFailures >= queue.size` 时停止并提示，防止 VIP/无版权整队时后台死循环跑流量；
  单首失败延时 1.5s `next()`。
- 上报：`scrobbleCurrent()` 在切歌/退出（`completed=false`）与自然播完（`true`）时调用
  `Api.scrobble()`，同一首只报一次；单曲循环播完重置标记再上报。
- 音频焦点：**单例** `OnAudioFocusChangeListener`（避免切歌时旧 listener 收到 LOSS_TRANSIENT 误暂停）；
  耳机拔出 `ACTION_AUDIO_BECOMING_NOISY` 自动暂停。
- 通知：`Notification.Builder` 基础字段 + API 21+ `MediaStyle`、上一首/播放/下一首三键、
  异步封面；`PlayerCompat21` 用 `Any` 隔离 `android.media.session.*`，避免低版本类加载崩溃。
  19/20 没有 MediaStyle：使用基础通知 + 系统媒体图标三键 + 栅格化小图标，并监听大图更新。
- API 26/29 的通知渠道、渠道化 Builder、`startForegroundService`、带类型 `startForeground`
  分别隔离在 `PlayerCompat26` / `PlayerCompat29`，避免 API 19 的 Dalvik 校验到新平台类型。
- 19/20 没有 MediaSession：`MediaButtonReceiver` + `AudioManager.registerMediaButtonEventReceiver`
  接管耳机/蓝牙播放、暂停、上一首、下一首；21+ 仍由 MediaSession 处理。
- `onStartCommand` 支持 `ACTION_PLAY/RESUME/PAUSE/PREV/NEXT/STOP`，返回 `START_STICKY`。

`PlayerCompat21` 仅包含：`createSession/updateMeta/updateState/release/decorate/buildWithArt`，
通过 `PlayerService.compatXxx()` 桥接，**没有“MediaPlayer 降级路径”**（MediaPlayer 本身就是唯一播放器）。

### 5.6 缓存

**`AudioCache`**

- 目录：`filesDir/audio`（避免 cacheDir 被系统整体回收）；旧版 `cacheDir/audio` 自动迁移。
- 文件名 `{songId}-{level}.aud`，同目录 `{name}.meta` 保存歌名/歌手/专辑/封面等离线信息。
- 命中刷新 `lastModified`；`entries()` 按最近使用倒序；`cachedAny(id)` 用于缓存歌单离线播放。
- `fetch(song,url,isCancelled,cb)`：单线程下载 → `.tmp` → 重命名；切歌时中止并删除临时文件；
  写 meta 后 `trim(keep=target)` 按 `Prefs.cacheMB`（默认 256MB）淘汰最久未使用文件。
- `clearAll(keepId)` / `applyLimit(keepId)` 可保留正在播放的文件。

**`ImageLoader`**

- 内存：`LruCache<String, Bitmap>`，容量为 `maxMemory/8`（以 KB 计）。
- 磁盘：`cacheDir/images/{md5(url)}`，上限 96MB，按 `lastModified` LRU。
- 音乐 CDN 自动追加 `param={px}y{px}q75` 压缩；下载后按目标尺寸 `inSampleSize` 解码。
- 两个线程的固定线程池；`ImageView.tag` 防止复用错图。

### 5.7 持久化

**`Prefs`（`lite` 命名空间）**：`deviceId`、`musicU/musicA`、`csrf`、`userId`、`nickname`、
`avatar`、`vipType`、`level`（standard/exhigh/lossless，旧值 higher 归一化）、`cacheMB`、
`auxMode`（0 无 / 1 翻译 / 2 注音）、`lyricSize`、`playMode`、`themeMode`、`keepScreenOn`、
`playerFullscreen`、`searchHistory`（JSON 数组，最近 50 条去重）、`extraCookies`（全量 Cookie JSON）。
`loggedIn` 直接等于 `musicU.isNotEmpty()`。

**`HistoryStore`（`history` 命名空间）**：最近 50 首不重复歌曲，最新在前；
`PLAYLIST_ID = -1001`，作为本地私人歌单固定排在歌单列表最后，封面为 `cover_history`。

**`LikedStore`**：内存 `HashSet<Long>`，登录后 `refresh()` 拉 `likelist`；横屏音乐库会用
歌单详情批量 `replace()`。

### 5.8 UI 层

| 组件 | 职责 / 关键实现 |
|---|---|
| `MainActivity` | 单 Activity + ViewPager2 承载 Home/Search/Mine（`offscreenPageLimit=2`，禁止用户滑页）；自定义 `NavDragLayout` 拖拽底栏切换；M3 胶囊指示器（拖拽/点按缩放动画）；竖屏底部 Navigation Bar（无标签），横屏 Navigation Rail（`layout-land`，80dp，图标+标签，指示器纵向平移） |
| `HomeFragment` | 网格卡片（按宽度约 180dp/列，最少 2 列）；头部“每日推荐”卡（首曲封面）；底部“加载更多”从 `playlistSquare` 按 offset 追加并按 id 去重 |
| `SearchFragment` | 范围 `ALL/LIKED/PLAYLISTS/ALBUMS` + 类型 `SONG/PLAYLIST/ALBUM/ARTIST`；本地范围用 `likelist + songDetail(≤300)`、`userPlaylist`、`albumSublist` 后本地过滤；搜索历史 Chip；`SearchRootLayout` 点外部收键盘 |
| `MineFragment` | 竖屏：头部登录卡/我喜欢的音乐/设置/分类选择弹窗 + RecyclerView 行模型；横屏：`AppBarLayout` 折叠头部 + 我喜欢的歌网格（8 首）+ 4 页 `ViewPager2`（歌单/专辑/艺人/排行）+ 可点击/拖拽分类条（`fakeDrag`，切页动画 200~420ms）+ 排行（周/总） |
| `PlayerActivity` | 竖屏封面↔歌词切换；横屏封面+歌词+控制同屏；透明触摸层拖动进度（500ms 线性补间）；歌词二分查找当前行并平滑滚动；`auxMode` 切换翻译/注音；心跳兜底同步播放图标；防熄屏/全屏开关 |
| `PlaylistActivity` | type 0 歌单 / 1 每日推荐 / 2 我喜欢的 / 3 专辑；歌单与专辑显示收藏按钮（`playlistSubscribe/albumSub`），“我喜欢的”隐藏 |
| `ArtistActivity` | 热门歌曲 + 全部专辑（可折叠分区），关注/取关走 linuxapi，多歌手长按弹选择 |
| `LoginActivity` | ZXing `QRCodeWriter` 生成 512px 二维码（RGB_565 一次性写像素）；2 秒轮询扫码状态；`onStop` 暂停轮询 |
| `SettingsActivity` | 账号卡（登录后从“我的”移入）、主题、音质、缓存上限、歌词字号、已缓存歌曲、清除缓存、防熄屏、全屏 |
| `HistoryActivity` / `CachedSongsActivity` | 本地历史；缓存列表（旧缓存无 meta 时按 id 补查并写回） |
| `SongAdapter` | 通用歌曲行：红心切换、长按选歌手；横屏多一列专辑（点击播放/长按进专辑） |
| `MiniPlayerController` | 宿主 XML `<include layout="@layout/view_mini_player"/>`，通过 `PlayerService.listeners` 同步；横屏收窄贴右下 |
| 自定义 View | `NavDragLayout`（touchSlop 后才拖拽）、`MiniProgressRing`（2dp 环形进度 + 缺口）、`LoadingBar`（逐帧 M3 不确定进度）、`CenteredRippleLayout`（涟漪居中）、`SearchRootLayout`（外部抬起收键盘） |

**资源布局**

- 竖屏与横屏是两套 XML：`layout/`（24 个）与 `layout-land/`（4 个）；
  横屏仅覆盖主框架、播放页、我的页、歌曲行。
- 主题：`Theme.Material3.DayNight.NoActionBar`，完整 M3 颜色角色（主/次/第三/错误/
  表面容器/外框/反色）；`values-night` 提供深色；系统栏着色拆到 `values-v21/v23/v27`
  与对应的 `values-night-*`，API 19 不做无效着色。
- 转场：`anim/` 为 API 19 版本（平台 decelerate 曲线近似，时长/缩放一致），
  `anim-v21/` 使用平台 `fast_out_slow_in` / `fast_out_linear_in`，21+ 保持原 M3 曲线。
- 图标：布局统一 `app:srcCompat` + `app:tint`，API 19/20 由 AppCompat 运行时解析矢量；
  通知栏小图标 `ic_stat_music` 提供 5 套密度 PNG。
- 圆形涟漪：`drawable-v21/bg_circle_ripple.xml` 保持 Ripple；基础 `drawable/` 为圆形按压高亮。
- 字符串集中在 `values/strings.xml`（136 行）；`Api.kt`/`ApiClient.kt` 中的错误文案仍为硬编码中文。

---

## 六、关键数据流

### 6.1 点击播放

```
UI（SongAdapter / 播放全部 / 迷你条）
  → PlayerService.play(ctx, songs, index)
      ├─ 服务已存在 → setQueue(songs, index)
      └─ 未启动 → pending = (songs,index) → startForegroundService()
  → playCurrent()
      ├─ 命中 AudioCache → beginPlayback(本地路径)
      └─ 未命中 → Api.songUrl(id) → AudioCache.fetch()（整首下载）
                    → beginPlayback(缓存文件) → HistoryStore.record()
  → 通知栏 / MediaSession / MiniPlayer / PlayerActivity listeners 同步
```

### 6.2 歌词

```
Api.lyric(id) → GET /song/lyric（lrc + tlyric + romalrc）
  → LrcParser.parse ×3
  → mergeTranslation：时间精确匹配 → 跳过制作信息 → 按序/就近配对(±1s)
  → Kana.romajiToHiragana(roma)
  → PlayerActivity.buildLyricRows()（ScrollView 内动态 TextView）
  → 500ms 进度回调 → 二分查找当前行 → 高亮 + 平滑滚动
```

### 6.3 扫码登录

```
LoginActivity.startQr()
  → Api.qrKey()  [eapi login/qrcode/unikey]
  → ZXing 生成二维码 → 每 2s Api.qrCheck(key)
      code 800 过期 / 801 待扫码 / 802 待确认 / 803 成功
  → 803: Api.account() → saveAccount(profile) → Prefs.userId/nickname/avatar/vipType
  → LikedStore.refresh() → finish()
```

### 6.4 收藏/关注

- 单曲红心：`Api.like()` → eapi `radio/like` → `LikedStore` 更新 → 当前可见按钮刷新。
- 歌单：`playlistSubscribe()`（收藏带 `X-antiCheatToken`）。
- 专辑：`albumSub()`。
- 艺术家：`linuxapi artist/sub|unsub`（eapi/weapi 在该场景会被风控）。

---

## 七、关键设计决策

| 决策 | 原因与影响 |
|---|---|
| MediaPlayer 而非 ExoPlayer | 兼容 minSdk 19、零额外依赖、APK 更小；代价是无内置缓存/流式播放能力，自研 `AudioCache` |
| org.json 手写解析 | 无 Gson/Moshi 依赖；但每个接口需手动兼容字段差异 |
| eapi 作为主通道 | 实测部分网络环境 weapi 被风控返回空响应，eapi（iPhone 客户端通道）更稳定 |
| linuxapi 只用于写操作 | 关注/取关等敏感操作风控最宽松；读接口仍走 eapi |
| 先整首下载再播放 | 一首歌只下一次、重播零流量；代价是首次起播等待整首下载 |
| 音频放 `filesDir` | 防止 `cacheDir` 被系统/清理工具整批回收 |
| `PlayerCompat21` 动态隔离 | 4.4 设备不会加载 `android.media.session.*`，避免类验证崩溃 |
| 无 ViewModel | 轻量、代码直观；但配置变更（旋转）会重建页面并重新请求 |
| 单线程网络队列 | 避免并发触发风控/限流；代价是首屏多请求排队 |
| SharedPreferences 存历史 | 数据量小（50 首 / 50 关键词），免去 Room 依赖 |

---

## 八、已知问题、风险与优化候选

> 以下按影响排序，供后续优化排期参考；均基于当前源码，不含旧文档错误。

### P0 — 首要优化（体验 / 性能明显）

1. **首次起播延迟**：`PlayerService.playCurrent()` 先 `AudioCache.fetch()` 整首下载完才 `setDataSource`。
   弱网/长音频下点击到出声可能数秒。可改为“边下边播 + 后台落缓存”，或预取下一首。
2. **所有 API 共用单线程**：`ApiClient.executor` 为 `newSingleThreadExecutor`。首页同时发起
   `personalized`、`recommendSongs`、`account`、`likelist` 时会严格串行，首屏时间叠加。
   建议改为 3~4 线程池，并对写操作单独串行。
3. **旋转/配置变更重建**：`MainActivity` 未声明 `configChanges`，也没有 ViewModel；
   旋转后三个 Fragment 重建并重新发起网络请求，数据返回前列表为空，滚动位置随之丢失。
4. **歌词渲染成本**：`PlayerActivity.buildLyricRows()` 为每行创建 2 个 `TextView`；
   300~500 行歌词即 600~1000 个 View，且 `scrollToLine` 每 500ms 计算布局。
   建议改为 `RecyclerView` 或至少虚拟化/复用。

### P1 — 结构 / 可维护性

5. **`MineFragment` 过大（1,398 行）**：竖屏列表与横屏音乐库两套 UI、两套状态机共存。
   建议拆分为 `MineFragment`（竖屏）+ `LibraryFragment`（横屏）+ 共享数据层。
6. **无统一状态层**：`LikedStore`、`HistoryStore`、页面局部变量分别维护状态，
   收藏后不同页面靠 `onResume` 重新拉取，存在短暂不一致。
7. **`modify_version.py` 过期**：仍执行 3.3.5→3.3.6 的字符串替换，实际版本 3.3.13；
   直接运行会改错版本。建议删除或改为读取/写入 Gradle 属性。
8. **`Song.playable` 未使用**：下架判断仅在 `Parser` 中写入 `offline`，UI 没有禁用不可播歌曲。
9. **错误文案硬编码**：`Api`/`ApiClient` 中中文错误串未进 `strings.xml`，不利于统一调整。
10. **无任何测试**：`app/src` 只有 `main`，没有 `test/androidTest`；歌词合并、LRC 解析、
    Cookie/CSRF 等纯逻辑适合补单元测试。

### P2 — 性能与工程化

11. **`HttpURLConnection` 无连接复用**：每次请求新建连接（TLS 握手成本高）。
    在“不引入 OkHttp”的约束下可考虑自建 `Keep-Alive` 或只在关键链路复用。
12. **图片加载无并发去重**：同一 URL 在内存/磁盘均未命中时可能被多个线程重复下载；
    不同 `targetPx` 还会产生多份缓存。可加 in-flight 合并与 `LruCache` 分层。
13. **本地搜索范围开销大**：`SearchFragment` 每次搜索“我喜欢/收藏的歌单/专辑”都重新拉
    `likelist` + 最多 300 首详情，无缓存；建议内存缓存歌曲详情（带 TTL）。
14. **通知栏进度不刷新**：`updateNotif()` 仅在播放状态/歌曲变化时调用，锁屏进度不随播放前进；
   可在 ticker 中低频更新 `PlaybackState` 位置（500ms 已经很轻）。
15. **发布配置缺失**：release 未配置签名（当前交付为 debug 签名）；历史 APK 直接堆在仓库根目录。
16. **targetSdk 34 落后 compileSdk 36**：升级 target 需处理前台服务类型、通知权限等行为变更。
17. **`CachedSongsActivity` 未解析的缓存条目不计入列表**：`unknownIds` 只计入总数文案，
    在 `resolveUnknown()` 返回前列表里看不到（但文件仍在）；可先插入占位行。
18. **`HistoryStore` 每次记录全量重写 JSON**：50 首规模下可接受，若扩大上限需换存储。
19. **API 19/20 系统限制**：无 MediaSession，锁屏媒体面板不可用；Activity 转场只能用平台
    decelerate 曲线近似 M3 曲线，时长与缩放幅度保持不变。
20. **debug 也开启 R8**：为满足 API 19 单 dex 限制，debug 构建同样是混淆/收缩后的产物，
    调试时类名与堆栈需要结合 `mapping.txt` 阅读。

---

## 九、建议的优化路线（供后续迭代）

1. **基础加固**：修复/替换 `modify_version.py`；补纯逻辑单元测试；统一错误文案。
2. **首屏与网络**：网络执行器分池 + 请求去重；首页并行拉取；图片 in-flight 合并。
3. **播放体验**：边下边播 / 下一首预取；通知与锁屏进度刷新；失败重试策略细化。
4. **UI 性能**：歌词改 RecyclerView；拆分 `MineFragment`；关键页面引入 ViewModel/状态保存。
5. **发布工程**：release 签名与混淆、targetSdk 升级、APK 归档目录整理。

> 版本号约定：每次改动 `versionName` +0.01、`versionCode` +1（当前 3.3.13 / 65）。

---

## 十、附录：源码规模清单

| 文件 | 行数 | 职责 |
|---|---:|---|
| ui/MineFragment.kt | 1398 | 我的页（竖屏 + 横屏音乐库） |
| Api.kt | 664 | 业务接口 + 歌词合并 |
| player/PlayerService.kt | 689 | 播放服务 |
| ui/PlayerActivity.kt | 564 | 播放页 |
| ApiClient.kt | 417 | 网络传输 / 多通道 / Cookie |
| ui/MainActivity.kt | 395 | 主框架 / 导航 |
| ui/SearchFragment.kt | 379 | 搜索 |
| ui/ArtistActivity.kt | 333 | 歌手主页 |
| AudioCache.kt | 280 | 音频缓存 |
| ui/SettingsActivity.kt | 262 | 设置 |
| Prefs.kt | 241 | 偏好与登录态 |
| ui/PlaylistActivity.kt | 195 | 通用歌曲列表 |
| ui/HomeFragment.kt | 185 | 首页 |
| ui/CachedSongsActivity.kt | 171 | 已缓存歌曲 |
| ImageLoader.kt | 159 | 图片加载 |
| ui/MiniPlayerController.kt | 143 | 迷你播放条 |
| ui/LoginActivity.kt | 122 | 扫码登录 |
| HistoryStore.kt | 119 | 本地历史 |
| player/PlayerCompat21.kt | 117 | MediaSession 隔离 |
| player/MediaButtonReceiver.kt | 33 | API 19/20 媒体按键 |
| player/PlayerCompat26.kt | 29 | 通知渠道 / 前台服务隔离 |
| player/PlayerCompat29.kt | 18 | 前台服务类型隔离 |
| Models.kt | 111 | 数据模型 + Parser |
| ui/LoadingBar.kt | 103 | M3 加载条 |
| ui/SongAdapter.kt | 107 | 歌曲行适配器 |
| ui/MiniProgressRing.kt | 100 | 环形进度 |
| ui/NavDragLayout.kt | 98 | 可拖拽导航 |
| Kana.kt | 79 | 罗马音转假名 |
| ui/HistoryActivity.kt | 78 | 历史记录页 |
| Eapi.kt | 53 | eapi 加解密 |
| Weapi.kt | 49 | weapi 加解密 |
| ui/SearchRootLayout.kt | 39 | 收键盘容器 |
| ui/LikedStore.kt | 37 | 红心集合 |
| LrcParser.kt | 34 | LRC 解析 |
| App.kt | 29 | Application |
| ui/CenteredRippleLayout.kt | 21 | 涟漪居中 |
| **合计** | **7,853** | 36 个 Kotlin 文件 |

资源：`layout/` 24 个、`layout-land/` 4 个、`values*` 8 套、`drawable/`、`anim/`、`menu/`、
`mipmap-*`，共 78 个 XML；未配置测试源集。
