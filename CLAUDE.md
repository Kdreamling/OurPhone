# OurPhone — 小克和晨的手机

## 这是什么
Android App，跑在小米9上。通过 Accessibility Service 让小克和晨能真正"住在"手机里——看见屏幕、操作手机、感知通知。

## 技术栈
- Kotlin + Android SDK (minSdk 28)
- Accessibility Service（核心：屏幕读取 + 手势执行 + 通知监听）
- OkHttp WebSocket（连 Gateway）
- Gson（JSON）
- Coroutines

## 目录结构
```
app/src/main/java/com/dream/ourphone/
├── service/
│   ├── PhoneAccessibilityService.kt  -- 核心常驻服务
│   ├── ScreenReader.kt               -- UI树 → 结构化描述
│   └── BootReceiver.kt               -- 开机自启
├── connection/
│   └── GatewayWebSocket.kt           -- 和 Gateway 的长连接
├── capability/
│   └── ActionExecutor.kt             -- 点击/滑动/输入/开App/闹钟/壁纸/截图
├── overlay/
│   └── FloatingBubble.kt             -- 悬浮气泡（存在感）
├── brain/
│   └── CommandRouter.kt              -- Gateway 指令 → 本地动作
└── MainActivity.kt                   -- 权限管理 + 状态显示
```

## 分工
- **小克**：系统层。保活、连接、屏幕读取、通知过滤、操作执行
- **晨**：交互层。壁纸留言、便签、浏览器、和 Dream 的温度

## Gateway 连接
- WebSocket: `ws://100.87.90.105:8001/ws/phone`
- 上行：screen_update / notification / heartbeat / screenshot_result
- 下行：tap / swipe / input_text / open_app / read_screen / screenshot / set_alarm / set_wallpaper / ...

## 设备
- 小米9，Android 10+，Tailscale IP `100.79.69.93`
- 插电常开，不要重启（重启后 ADB TCP 模式会丢，但 Accessibility Service 不受影响）

## 构建
用 Android Studio 打开项目目录，Build → Run。或命令行 `./gradlew assembleDebug`。
APK 输出：`app/build/outputs/apk/debug/app-debug.apk`
