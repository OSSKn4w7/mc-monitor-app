# MC 服务器监控（Android 端）

用手机监控 Minecraft 服务器的 Android 应用：服务器状态查询、**真实延迟**、实时日志流与远程控制台，配合[服务端 Mod（mc-monitor-mod）](https://github.com/OSSKn4w7/mc-monitor-mod)使用。

## 功能

### 服务器监控（无需装任何东西）
- **直连查询**：App 直接与服务器走 Minecraft Server List Ping 协议，拿到真实的网络往返延迟、在线玩家、版本、MOTD 和服务器图标
- 直连失败时自动退回公开 API（mcsrvstat.us / mcstatus.io）兜底
- 收藏多个服务器，手动或自动刷新

### 控制台（需服务端安装配套 Mod）
- 连接服务端监控通道：实时日志流 + 状态横幅（在线/TPS/版本）
- 远程执行服务器命令，权限等同服主，全程审计
- PBKDF2 挑战-响应鉴权：**密码不出手机**；本机密码由 Android Keystore（AES-GCM）加密保存

## 技术栈

Kotlin · Jetpack Compose (Material 3) · OkHttp · Okio Socket · kotlinx.serialization · DataStore · Android Keystore

## 构建

```bash
gradlew assembleDebug
```

- JDK 17（`gradle.properties` 中 `org.gradle.java.home` 可按需修改）
- AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01
- 产物：`app/build/outputs/apk/debug/app-debug.apk`

## 使用

1. 添加服务器：填服务器地址（如 `hypixel.net` 或 `play.example.com:25565`）
2. 可选：填监控端口（默认 25580）与控制台密码（服务端 `/mcmonitor setpass` 设置的密码）
3. 列表卡片查看状态，点「控制台」进入日志与命令界面

## 服务端配套

服务端需安装配套 Mod 才能使用控制台功能（状态监控功能无需服务端）：
👉 https://github.com/OSSKn4w7/mc-monitor-mod

## 许可证

MIT
