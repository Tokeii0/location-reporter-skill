# 📍 Location Reporter - 实时位置追踪系统

一套可以让你的openclaw agent实时获取你个人位置的系统，包含安卓APP、云端服务和查询脚本。

## 架构

```
┌──────────────┐     POST      ┌──────────────┐      GET      ┌──────────────┐
│  Android APP │ ──────────→  │  云端服务器    │  ←────────── │  查询端       │
│  (GPS定位)   │   /api/loc   │  (Python HTTP) │   /api/loc   │  (脚本/Skill) │
└──────────────┘              └──────────────┘              └──────────────┘
```

## 目录结构

```
location-reporter/
├── android-app/          # Android APP 源码
│   ├── app/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradle/
│   ├── gradlew
│   └── gradlew.bat
├── server/               # 云端接收服务
│   ├── server.py
│   └── location-api.service
├── query/                # 位置查询脚本
│   └── get_location.py
├── skill/                # OpenClaw Skill
│   ├── SKILL.md
│   └── scripts/
│       └── get_location.py
└── README.md
```

## 快速开始

### 1. 部署服务端

```bash
# 上传到你的云服务器
scp server/server.py root@YOUR_SERVER_IP:/opt/location-api/
scp server/location-api.service root@YOUR_SERVER_IP:/etc/systemd/system/

# SSH 登录服务器执行
systemctl daemon-reload
systemctl enable location-api
systemctl start location-api
```

验证：
```bash
curl http://YOUR_SERVER_IP:8099/api/ping
# 返回 {"status": "pong"}
```

默认端口 `8099`，可在 `server.py` 中修改。

### 2. 安装 Android APP

**方式一：直接编译**
```bash
cd android-app
# 需要 JDK 21 + Android SDK 34
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```

**方式二：用 Android Studio 打开 `android-app/` 目录编译**

**使用：**
1. 安装 APK，打开 APP
2. 授权位置权限，确认能看到经纬度
3. 填写服务器地址：`http://YOUR_SERVER_IP:8099/api/location`
4. 设置上报间隔（默认 5 分钟）
5. 点击"开始上报"

### 3. 查询位置

```bash
# 设置环境变量
export LOCATION_API="http://YOUR_SERVER_IP:8099/api/location"
export AMAP_KEY="YOUR_AMAP_KEY"

python3 query/get_location.py
```

### 4. OpenClaw Skill（可选）

将 `skill/` 目录复制到 OpenClaw workspace 的 skills 目录：

```bash
cp -r skill ~/.openclaw/workspace/skills/location-tracker
```

然后在 `skill/scripts/get_location.py` 中配置你的 `LOCATION_API` 和 `AMAP_KEY`。

## API 说明

### POST /api/location
接收位置上报。

请求体：
```json
{
  "lat": xx.xxxxxx,
  "lng": xx.xxxxxx,
  "timestamp": 1773616504,
  "accuracy": 3.79,
  "device": "我的手机"
}
```

响应：`{"status": "ok"}`

### GET /api/location
返回最新位置数据。

响应：
```json
{
  "lat": xx.xxxxxx,
  "lng": xx.xxxxxx,
  "timestamp": 1773616504,
  "accuracy": 3.79,
  "device": "我的手机",
  "received_at": 1773616504
}
```

### GET /api/ping
健康检查。响应：`{"status": "pong"}`

## 配置项

| 配置 | 说明 | 位置 |
|------|------|------|
| 服务端端口 | 默认 8099 | `server/server.py` |
| 上报间隔 | APP 内设置，默认 5 分钟 | Android APP |
| 高德 API Key | 逆地理编码用 | 环境变量 `AMAP_KEY` |
| 服务器地址 | 位置数据接口 | 环境变量 `LOCATION_API` |

### 获取高德 API Key
1. 访问 https://lbs.amap.com/ 注册
2. 控制台 → 应用管理 → 创建应用 → 添加 Key（Web 服务）

## 注意事项

- Android 9+ 默认禁止 HTTP 明文请求，APP 已配置 `networkSecurityConfig` 允许
- GPS 坐标为 WGS84 格式，查询脚本会自动转换为 GCJ02 后调用高德 API
- 建议服务端配合防火墙限制访问来源
- APP 后台运行需授权"后台位置权限"和"通知权限"
- 国内安卓手机无需 Google Play Services，使用原生 LocationManager

## 技术栈

- **Android**: Kotlin, LocationManager, OkHttp, Foreground Service
- **Server**: Python 3 标准库（零依赖）
- **Query**: Python 3, 高德地图 Web API
- **Skill**: OpenClaw AgentSkill 规范

## License

MIT
