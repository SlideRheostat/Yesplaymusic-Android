# LiteMusic Android — Android 4.4（API 19）兼容性适配报告

## 1. 目标与结论

原始需求为最低支持 Android 4.0（API 14）。经实际构建探测确认：

- `com.google.android.material:material:1.12.0` 最低要求 API 19；
- `androidx.core:core:1.13.1` 等 AndroidX 组件同样以 API 19 为基线；
- Material Design 3 主题无法在 API 14 上保留；
- 若强行降到 API 14，需要降级到 Material 2/旧版 AndroidX，UI 与控件样式会发生变化。

经确认，本次目标调整为 **Android 4.4（API 19）**：在保留 Material Design 3 UI、全部页面、
交互动画和现有功能的前提下完成兼容适配。项目未引入任何新的第三方依赖。

## 2. 交付物

| 项 | 值 |
|---|---|
| 安装包 | `YesPlayMusic-v3.3.13-api19-debug.apk`（仓库根目录） |
| 构建产物 | `app/build/outputs/apk/debug/app-debug.apk` |
| 版本 | `versionName 3.3.13` / `versionCode 65` |
| applicationId | `com.yesplaymusic.mobile` |
| minSdk | 19（Android 4.4） |
| targetSdk / compileSdk | 34 / 36 |
| 签名 | v1（JAR）+ v2，均可验证通过 |
| SHA-256 | `2DF3B589C2CA2DF72E88BA6F33828117198CD3BF0E006F5A366C92EEE30C1CD2` |
| Dex | 单 `classes.dex`，未引入 multidex 依赖 |
| Lint | 0 errors，92 warnings（均为非阻塞性建议） |

## 3. 适配改动清单

### 3.1 构建与打包

- `minSdk 21 → 19`，版本号升至 3.3.12 / 64。
- 开启 `vectorDrawables.useSupportLibrary = true`，API 19/20 由 AppCompat 运行时解析矢量图标。
- minSdk 19 不支持原生 multidex，`debug/release` 开启 R8 收缩以保持单 dex（方法数低于 64K）。
- `proguard-rules.pro` 保留 `PlayerCompat21/26/29`，避免 R8 内联后把高版本 API 调用带回低版本路径。

### 3.2 代码兼容

- `android.view.animation.PathInterpolator`（API 21）全部替换为
  `androidx.core.view.animation.PathInterpolatorCompat`，动画曲线与时序不变。
- `ImageView.imageTintList`（API 21）替换为 `ImageViewCompat.setImageTintList`。
- `Activity.requestPermissions` 替换为 `ActivityCompat.requestPermissions`。
- `HashMap.putIfAbsent`（API 24）替换为 `containsKey + put`。
- `Canvas.drawRoundRect(6 参数)`（API 21）替换为 `RectF + drawRoundRect`。
- `NotificationChannel`、渠道化 `Notification.Builder`、`startForegroundService` 隔离到
  `PlayerCompat26`；带类型 `startForeground` 隔离到 `PlayerCompat29`。
- API 19/20 通知栏增加上一首/播放暂停/下一首三个操作按钮，使用系统媒体图标与
  5 套密度 PNG 小图标 `ic_stat_music`，并支持异步大图封面。
- API 19/20 新增 `MediaButtonReceiver`，通过 `AudioManager.registerMediaButtonEventReceiver`
  接管耳机/蓝牙播放、暂停、上一首、下一首；API 21+ 仍由 MediaSession 接管。
- `App` 开启 `AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)`，兼容菜单/资源引用中的矢量图。

### 3.3 资源与主题

- 所有布局的 `android:src` → `app:srcCompat`，`android:tint` → `app:tint`（29 个矢量图标）。
- 系统栏着色从基础 `values/` 拆到 `values-v21` / `values-night-v21`；
  `values-v23` / `values-v27` 及其夜间版本保持原有 23+/27+ 行为；API 19 不做无效着色。
- `<shape>` 中的 `?attr/colorXxx` 改为 `@color/md_xxx`，利用已有 `values-night/colors.xml`
  自动适配深浅色，避免 API 19 的 GradientDrawable 无法解析主题属性。
- 圆形 Ripple 放入 `drawable-v21/`；基础 `drawable/bg_circle_ripple.xml` 使用圆形按压高亮近似。
- 转场动画：`anim-v21/` 保留 `fast_out_slow_in` / `fast_out_linear_in`；
  基础 `anim/` 使用平台 `decelerate_quint` / `decelerate_quad` 近似。
  **时长（300ms / 200ms）与缩放幅度（96% / 94%）完全一致**。

## 4. 运行验证

### 4.1 环境

- 模拟器：Android 4.4（API 19），x86 默认系统镜像，1080×1920。
- 安装包：本报告第 2 节所列 APK。
- 检查手段：`adb install`、`am start`、`uiautomator dump`、`screencap`、`logcat`、`dumpsys notification`。

### 4.2 实测结果

| 场景 | 结果 |
|---|---|
| 首次启动 / 首页 | 正常；每日推荐、推荐歌单与网络封面加载成功 |
| 底部三页签 | 首页 / 搜索 / 我的切换正常，M3 胶囊指示器与图标着色正常 |
| 搜索 | 输入 `jay` 后返回网易云搜索结果，列表与红心按钮正常 |
| 歌单详情 | 330 首歌曲列表、封面、播放全部、收藏按钮正常 |
| 播放 | `MediaPlayer` 起播成功；音频焦点请求正常；迷你播放器显示歌曲/歌手/进度 |
| 通知栏 | 标题、歌手-专辑、3 个操作按钮、栅格化小图标均正常（`dumpsys notification` 验证） |
| 歌词页 | 歌词加载、当前行高亮、滚动、封面/歌词切换、播放控制正常 |
| 我的页 | 登录卡、设置入口、音乐库分类、历史记录歌单正常 |
| 设置页 | 开关、缓存统计、缓存上限对话框正常 |
| 横屏 | 播放页横屏（封面+歌词+控制）与「我的」音乐库横屏均正常；Navigation Rail 正常 |
| 扫码登录 | 登录页打开、二维码生成、轮询状态正常 |
| 缓存 | 播放后缓存统计更新（2 首 / 8 MB），音频缓存写入成功 |
| 异常 | 未出现 `FATAL EXCEPTION`、`InflateException`、`SSLException`、`Unknown interpolator` |

> 说明：API 19 模拟器的 `screencap` 偶发返回上一帧画面，因此界面结构同时通过
> `uiautomator dump` 与 `dumpsys activity` 校验，二者均确认目标页面已处于前台并完成渲染。

### 4.3 3.3.13 修复回归（连接设备，Android 16）

| 场景 | 结果 |
|---|---|
| 横屏分类条点击「排行」 | 逐帧抓取 5 帧：动画中仅原「歌单」保持高亮，「专辑/艺人」全程未点亮；最后一帧「排行」与指示条同时到位 |
| 随机模式下一首/上一首 | 记录通知栏歌名：S0 老人と海 → 下一首 S1 绰巡林风 → 下一首 S2 人们向何处去 → 上一首回到 S1 → 再上一首回到 S0，全部符合实际播放顺序 |

### 4.4 API 21+ 回归

- 所有 21+ 专属逻辑仍走原有 `PlayerCompat21` / MediaSession / MediaStyle 路径；
- `anim-v21`、`values-v21/v23/v27` 与原资源内容一致；
- `app:srcCompat` / `app:tint` 在 21+ 由 AppCompat 走原生等价实现；
- Lint 0 error，构建通过。
- 受本机模拟器资源限制，API 20/21/23/29/36 未逐一进行运行时回归；建议在真机或
  对应系统镜像上补一次冒烟测试。

## 5. 已知限制（系统能力边界）

1. **API 19/20 无 MediaSession**：锁屏媒体控制面板不可用，这是系统 API 缺失，无法通过应用层补齐；
   已用通知栏三键 + 耳机/蓝牙媒体按键覆盖播放控制。
2. **API 19 的 Activity 转场曲线**：平台 XML 不支持 `PathInterpolator`，使用 decelerate 曲线近似；
   时长、缩放幅度、页面内容不变，仅缓动曲线略有差异。21+ 保持原 M3 曲线。
3. **API 19 无 Ripple**：圆形涟漪降级为圆形按压高亮，点击热区与几何形状不变。
4. **API 19 状态栏无法着色**：系统不提供 `statusBarColor`，保持系统默认黑色状态栏。
5. **API 14 不在支持范围**：Material 3 与当前 AndroidX 依赖最低 API 19；如需 API 14，
   必须降级 UI 体系并接受视觉差异，这与「完整保留现有 UI」冲突。
6. **真机差异**：不同 OEM 的 4.4 ROM 对 TLS、通知栏样式可能有差异；本次已在标准 API 19
   模拟器验证，建议在目标真机上补测。

## 6. 复现验证命令

```powershell
# 构建
.\gradlew :app:assembleDebug

# Lint
.\gradlew :app:lintDebug

# 校验 minSdk / 版本
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\aapt2.exe" dump badging app\build\outputs\apk\debug\app-debug.apk

# 校验签名（v1/v2 均应为 true）
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify -v app\build\outputs\apk\debug\app-debug.apk

# 安装到 API 19 设备/模拟器
adb -s emulator-5556 install -r YesPlayMusic-v3.3.13-api19-debug.apk
adb -s emulator-5556 shell am start -n com.yesplaymusic.mobile/com.litemusic.ui.MainActivity
```

## 7. 结论

在目标调整为 **API 19（Android 4.4）** 后，应用保留了 Material Design 3 界面、全部现有页面、
播放链路、歌词、搜索、登录、缓存、横竖屏布局与交互动画；API 19 模拟器上的主要用户路径已实测通过。
API 21+ 的专属能力保持原实现，未做降级。

> 3.3.13 在保持本兼容结论不变的前提下，修复了横屏分类条点击高亮时序与随机播放上一首回溯两个问题。
