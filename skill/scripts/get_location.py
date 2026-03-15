#!/usr/bin/env python3
"""查询实时位置并解析为具体地址"""

import json
import math
import os
import sys
import time
import urllib.request

# 配置项 - 通过环境变量设置
LOCATION_API = os.environ.get("LOCATION_API", "http://YOUR_SERVER_IP:8099/api/location")
AMAP_KEY = os.environ.get("AMAP_KEY", "YOUR_AMAP_KEY")


def wgs84_to_gcj02(lng, lat):
    """WGS84 坐标转 GCJ02（高德/腾讯地图坐标系）"""
    a = 6378245.0
    ee = 0.00669342162296594323
    dlat = _transformlat(lng - 105.0, lat - 35.0)
    dlng = _transformlng(lng - 105.0, lat - 35.0)
    radlat = lat / 180.0 * math.pi
    magic = math.sin(radlat)
    magic = 1 - ee * magic * magic
    sqrtmagic = math.sqrt(magic)
    dlat = (dlat * 180.0) / ((a * (1 - ee)) / (magic * sqrtmagic) * math.pi)
    dlng = (dlng * 180.0) / (a / sqrtmagic * math.cos(radlat) * math.pi)
    return lng + dlng, lat + dlat


def _transformlat(lng, lat):
    ret = -100.0 + 2.0*lng + 3.0*lat + 0.2*lat*lat + 0.1*lng*lat + 0.2*math.sqrt(abs(lng))
    ret += (20.0*math.sin(6.0*lng*math.pi) + 20.0*math.sin(2.0*lng*math.pi)) * 2.0/3.0
    ret += (20.0*math.sin(lat*math.pi) + 40.0*math.sin(lat/3.0*math.pi)) * 2.0/3.0
    ret += (160.0*math.sin(lat/12.0*math.pi) + 320*math.sin(lat*math.pi/30.0)) * 2.0/3.0
    return ret


def _transformlng(lng, lat):
    ret = 300.0 + lng + 2.0*lat + 0.1*lng*lng + 0.1*lng*lat + 0.1*math.sqrt(abs(lng))
    ret += (20.0*math.sin(6.0*lng*math.pi) + 20.0*math.sin(2.0*lng*math.pi)) * 2.0/3.0
    ret += (20.0*math.sin(lng*math.pi) + 40.0*math.sin(lng/3.0*math.pi)) * 2.0/3.0
    ret += (150.0*math.sin(lng/12.0*math.pi) + 300.0*math.sin(lng/30.0*math.pi)) * 2.0/3.0
    return ret


def main():
    if AMAP_KEY == "YOUR_AMAP_KEY":
        print("请设置 AMAP_KEY 环境变量（高德地图 Web API Key）")
        print("  export AMAP_KEY='你的Key'")
        sys.exit(1)

    if "YOUR_SERVER_IP" in LOCATION_API:
        print("请设置 LOCATION_API 环境变量")
        print("  export LOCATION_API='http://你的服务器IP:8099/api/location'")
        sys.exit(1)

    try:
        with urllib.request.urlopen(LOCATION_API, timeout=10) as resp:
            data = json.loads(resp.read())
    except Exception as e:
        print(json.dumps({"error": f"无法获取位置数据: {e}"}, ensure_ascii=False))
        sys.exit(1)

    if "lat" not in data or "lng" not in data:
        print(json.dumps({"error": "没有位置数据，APP可能未上报"}, ensure_ascii=False))
        sys.exit(1)

    lat, lng = data["lat"], data["lng"]
    accuracy = data.get("accuracy", 0)
    timestamp = data.get("timestamp", 0)
    age_seconds = int(time.time()) - timestamp

    gcj_lng, gcj_lat = wgs84_to_gcj02(lng, lat)
    location_str = f"{gcj_lng:.6f},{gcj_lat:.6f}"

    url = (
        f"https://restapi.amap.com/v3/geocode/regeo?output=json"
        f"&location={location_str}&key={AMAP_KEY}&radius=1000&extensions=all"
    )
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=10) as resp:
            result = json.loads(resp.read())
    except Exception as e:
        print(json.dumps({"error": f"地址解析失败: {e}"}, ensure_ascii=False))
        sys.exit(1)

    rc = result.get("regeocode", {})
    comp = rc.get("addressComponent", {})
    sn = comp.get("streetNumber", {})
    pois = rc.get("pois", [])

    nearby = []
    for p in pois[:3]:
        name = p.get("name", "")
        dist = p.get("distance", "")
        if name:
            nearby.append(f"{name} ({dist}米)")

    output = {
        "address": rc.get("formatted_address", ""),
        "province": comp.get("province", ""),
        "city": comp.get("city", ""),
        "district": comp.get("district", ""),
        "township": comp.get("township", ""),
        "street": f"{sn.get('street', '')} {sn.get('number', '')}".strip(),
        "nearby": nearby,
        "lat": lat,
        "lng": lng,
        "accuracy": accuracy,
        "timestamp": timestamp,
        "age_seconds": age_seconds,
    }

    print(json.dumps(output, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
