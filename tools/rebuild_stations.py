#!/usr/bin/env python3
"""
Menyegarkan app/src/main/assets/stations.json dari API aplikasi radio referensi
(Radio Indonesia FM 4.9.508) — endpoint & Basic auth ditemukan pada Tahap 1.

Pemakaian:
    AUTH=$(printf 'androides:aceroparalosbarcos' | base64 -w0)   # tidak diperlukan, skrip ini urus sendiri
    python3 tools/rebuild_stations.py

Hasil ditulis langsung ke app/src/main/assets/stations.json (dan salinan mentah
ke data/stations-raw.json bila folder data/ ada).
"""
import base64
import json
import pathlib
import urllib.request

API = "https://api.radiofmapp.com/v2/pais/id/emisoras"
USER, PASS = "androides", "aceroparalosbarcos"
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "assets" / "stations.json"


def fetch() -> dict:
    token = base64.b64encode(f"{USER}:{PASS}".encode()).decode()
    req = urllib.request.Request(
        API,
        headers={"Authorization": f"Basic {token}", "User-Agent": "OtoRadio-catalog/1.0"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def convert(raw: dict) -> dict:
    out = []
    for s in raw.get("data", []):
        url = (s.get("url") or "").strip()
        name = (s.get("nombre") or "").strip()
        if not url or not name:
            continue
        alts = [u.strip() for u in (s.get("url2"), s.get("url3")) if u and u.strip()]
        out.append(
            {
                "id": s["id"],
                "name": name,
                "stream_url": url,
                "stream_url_alt": alts,
                "logo_file": s.get("imagen"),
                "logo_url": None,          # server logo 404 (diverifikasi 2026-10-22)
                "frequency": s.get("frecuencia") or None,
                "city": s.get("ciudad") or None,
                "country": "Indonesia",
                "country_code": "id",
                "website": s.get("www") or None,
                "geoblocked": bool(s.get("geoblocked")),
                "enabled": bool(s.get("enabled", 1)),
                "genre": None,
                "source": "radiofmapp-api-v2",
            }
        )
    out.sort(key=lambda x: x["name"].lower())
    return {
        "_meta": {
            "generated_at": "auto",
            "source": API,
            "auth": "HTTP Basic (hard-coded di APK referensi)",
            "server_last_update": raw.get("last_update"),
            "count": len(out),
            "notes": [
                "logo_url null: /images/stations/<cc>/<imagen> mengembalikan 404",
                "genre null: API sumber tidak punya field genre",
            ],
        },
        "stations": out,
    }


def main() -> None:
    raw = fetch()
    data = convert(raw)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    rawdir = ROOT.parent / "data"
    if rawdir.is_dir():
        (rawdir / "stations-raw.json").write_text(json.dumps(raw, ensure_ascii=False), encoding="utf-8")
    print(f"OK: {len(data['stations'])} stasiun -> {OUT}")


if __name__ == "__main__":
    main()
