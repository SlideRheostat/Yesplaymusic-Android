# API 19 单 dex 约束下启用 R8：保留行号便于崩溃定位。
# 自定义 View、Activity、Service、Receiver 由 AAPT/清单规则自动保留。
-keepattributes SourceFile,LineNumberTable

# 保留按 API 级别隔离的兼容类，避免 R8 内联后把 21+/26+/29+ 调用带进低版本路径
-keep class com.litemusic.player.PlayerCompat21 { *; }
-keep class com.litemusic.player.PlayerCompat26 { *; }
-keep class com.litemusic.player.PlayerCompat29 { *; }
