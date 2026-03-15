---
name: location-tracker
description: "查询用户的实时位置。当用户问'我在哪'、'我的位置'、'我在哪里'、'我现在在哪'等位置相关问题时使用此技能。"
metadata:
  openclaw:
    requires:
      env: ["LOCATION_API", "AMAP_KEY"]
      bins: ["python3"]
---

# 位置追踪

查询用户的实时 GPS 位置，解析为具体街道地址。

## 使用场景

当用户问以下类似问题时触发：
- "我在哪"、"我在哪里"、"我现在在哪"
- "我的位置"、"查一下我的位置"
- "定位"、"我的坐标"

## 前提条件

### 1. 部署服务端
参考项目根目录 README.md 部署 `server/server.py` 到云服务器。

### 2. 安装 Android APP
编译并安装 `android-app/`，配置服务器地址并开启上报。

### 3. 配置环境变量

```bash
# 添加到 ~/.zshrc 或 ~/.openclaw/.env
export LOCATION_API="http://YOUR_SERVER_IP:8099/api/location"
export AMAP_KEY="YOUR_AMAP_KEY"
```

## 使用方法

```bash
python3 {baseDir}/scripts/get_location.py
```

输出 JSON：
```json
{
  "address": "北京市xx区xxx街道xxxxx号",
  "province": "北京市",
  "city": "北京市",
  "district": "xx区",
  "township": "xxx街道",
  "street": "xxxxx号",
  "nearby": ["某某大厦 (91米)"],
  "lat": xx.xxxxxx,
  "lng": xx.xxxxxx,
  "accuracy": 3.79,
  "timestamp": 1773616504,
  "age_seconds": 120
}
```

## 回复风格

- **不要提及** API、GPS、逆地理编码等技术细节
- **要说** 类似"我用神秘能力感知到了你的位置～"
- 用可爱、俏皮的方式告诉用户他在哪
- 提及附近的标志性建筑/地点
- 如果 `age_seconds > 3600`，提醒用户数据可能不是最新的
