#!/usr/bin/env python3
"""
Mengumpulkan logo/thumbnail stasiun untuk katalog bawaan.

Sumber (berurutan, kualitas menurun):
  1. Radio Browser  -> field "favicon" (logo stasiun asli, 80%+ terisi untuk ID).
     Dicocokkan HANYA bila token nama sama persis (set token), agar tidak salah pasang.
  2. Google favicon service (s2) -> ikon situs resmi stasiun (resolusi kecil, 128 px).
  3. Tidak ada -> aplikasi memakai tile gradien + inisial (fallback di kode).

Hasil:
  app/src/main/assets/logos/<station_id>.<ext>   (maks 256x256)
  app/src/main/assets/stations.json               (+ field logo_asset & logo_source)

Pemakaian:
  python3 tools/fetch_logos.py            # unduh yang belum ada
  python3 tools/fetch_logos.py --refetch  # paksa unduh ulang
"""
import argparse
import concurrent.futures as cf
import io
import json
import pathlib
import re
import unicodedata
import urllib.parse
import urllib.request

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
LOGO_DIR = ASSETS / "logos"
CATALOG = ASSETS / "stations.json"
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) OtoRadio-logo/1.0"}
MAX_PX = 256
MIN_BYTES = 900          # buang ikon kosong / 1x1
RB_URL = ("https://de1.api.radio-browser.info/json/stations/search"
          "?limit=4000&countrycode=ID&hidebroken=true")


def norm_tokens(name: str) -> frozenset:
    s = unicodedata.normalize("NFKD", name).lower()
    s = re.sub(r"[,.;:_/\\-]", " ", s)
    s = re.sub(r"\b(fm|am|streaming|online|dot|com)\b", " ", s)
    s = re.sub(r"[^a-z0-9]+", " ", s)
    tokens = [t for t in s.split() if t]
    return frozenset(tokens)


def http_get(url: str, timeout: int = 20) -> bytes | None:
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            if r.status != 200:
                return None
            return r.read(3_000_000)
    except Exception:
        return None


def fetch_radio_browser_map() -> dict:
    raw = http_get(RB_URL, timeout=90)
    if not raw:
        print("! Radio Browser tidak dapat dihubungi, lanjut ke favicon situs saja")
        return {}
    arr = json.loads(raw.decode("utf-8"))
    mapping = {}
    for st in arr:
        fav = (st.get("favicon") or "").strip()
        name = (st.get("name") or "").strip()
        if not fav or not name:
            continue
        if not fav.lower().startswith(("http://", "https://")):
            continue
        mapping.setdefault(norm_tokens(name), []).append(fav)
    print(f"Radio Browser: {len(arr)} stasiun ID, {len(mapping)} nama unik punya favicon")
    return mapping


def domain_of(url: str | None) -> str | None:
    if not url:
        return None
    try:
        host = urllib.parse.urlparse(url).netloc.lower()
    except Exception:
        return None
    if host.startswith("www."):
        host = host[4:]
    return host or None


def process_image(data: bytes) -> tuple[bytes, str] | None:
    try:
        im = Image.open(io.BytesIO(data))
        im.load()
    except Exception:
        return None
    if im.width < 16 or im.height < 16:
        return None
    im = im.convert("RGBA")
    im.thumbnail((MAX_PX, MAX_PX), Image.LANCZOS)
    out = io.BytesIO()
    im.save(out, format="PNG", optimize=True)
    blob = out.getvalue()
    if len(blob) < MIN_BYTES:
        return None
    return blob, "png"


def resolve_one(station: dict, rb_map: dict, refetch: bool) -> dict:
    sid = str(station["id"])
    existing = station.get("logo_asset")
    if existing and not refetch and (ASSETS / existing).exists():
        return station

    # 0) override manual: taruh file app/src/main/assets/logos/<id>.webp atau <id>.png
    for manual in (LOGO_DIR / f"{sid}.webp", LOGO_DIR / f"{sid}.png"):
        if manual.exists() and not refetch:
            station = dict(station)
            station["logo_asset"] = f"logos/{manual.name}"
            station["logo_source"] = "manual"
            return station

    candidates: list[tuple[str, str]] = []          # (url, source)
    for fav in rb_map.get(norm_tokens(station["name"]), []):
        candidates.append((fav, "radio-browser"))
    host = domain_of(station.get("website"))
    if host:
        candidates.append(
            (f"https://www.google.com/s2/favicons?domain={host}&sz=128", "site-favicon")
        )

    for url, source in candidates:
        raw = http_get(url)
        if not raw:
            continue
        processed = process_image(raw)
        if not processed:
            continue
        blob, ext = processed
        rel = pathlib.Path("logos") / f"{sid}.{ext}"
        (ASSETS / rel).parent.mkdir(parents=True, exist_ok=True)
        (ASSETS / rel).write_bytes(blob)
        station = dict(station)
        station["logo_asset"] = str(rel)
        station["logo_url"] = url.split("?")[0] if source == "radio-browser" else url
        station["logo_source"] = source
        return station

    return station


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--refetch", action="store_true")
    ap.add_argument("--workers", type=int, default=10)
    args = ap.parse_args()

    LOGO_DIR.mkdir(parents=True, exist_ok=True)
    doc = json.loads(CATALOG.read_text(encoding="utf-8"))
    rb_map = fetch_radio_browser_map()

    stations = doc["stations"]
    with cf.ThreadPoolExecutor(max_workers=args.workers) as ex:
        resolved = list(ex.map(lambda s: resolve_one(s, rb_map, args.refetch), stations))

    have = [s for s in resolved if s.get("logo_asset")]
    by_src: dict[str, int] = {}
    for s in have:
        by_src[s.get("logo_source", "?")] = by_src.get(s.get("logo_source", "?"), 0) + 1

    doc["stations"] = resolved
    doc.setdefault("_meta", {})["logos"] = {
        "total": len(stations),
        "with_logo": len(have),
        "by_source": by_src,
        "note": "logo_asset menunjuk file di assets/logos/; logo_url = sumber asli. Override manual: taruh logos/<id>.webp lalu jalankan skrip ini.",
    }
    CATALOG.write_text(json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"logo terpasang: {len(have)}/{len(stations)}  {by_src}")
    total_kb = sum(p.stat().st_size for p in LOGO_DIR.glob("*.*")) // 1024
    print(f"ukuran folder logos: {total_kb} KB")


if __name__ == "__main__":
    main()
