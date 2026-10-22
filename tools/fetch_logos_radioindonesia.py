#!/usr/bin/env python3
"""
Mengambil logo stasiun dari radioindonesia.org (data mytuner, JSON-LD RadioStation).

Kenapa situs ini: setiap halaman stasiun memuat
    {"@type":"RadioStation","name":..,"image":"https://static.mytuner.mobi/media/tvos_radios/xxx.png",
     "sameAs":["https://situsresmi.com/"],"areaServed":"Jawa Barat", ...}
sehingga kita mendapat 4 sinyal sekaligus: nama, LOGO, situs resmi, dan wilayah.
`samaAs` (domain situs resmi) adalah kunci pencocokan paling andal ke katalog kita.

Alur:
  1. sitemap-radio-detail-1.xml.gz -> 724 URL halaman stasiun
  2. unduh tiap halaman (di-cache di work/ri/pages/<slug>.html, jadi bisa dijalankan ulang tanpa unduh)
  3. parse JSON-LD -> kumpulan {name, logo, domain, area}
  4. cocokkan ke assets/stations.json: domain -> nama persis -> nama+kota (harus unik)
  5. unduh logo, kecilkan ke 192 px, simpan WebP di assets/logos/<id>.webp
     (tidak menimpa logo manual)

Pemakaian:
  python3 tools/fetch_logos_radioindonesia.py            # ambil & pasang
  python3 tools/fetch_logos_radioindonesia.py --no-cache # paksa unduh ulang halaman
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import io
import json
import pathlib
import re
import time
import unicodedata
import urllib.parse
import urllib.request
from dataclasses import dataclass

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CATALOG = ASSETS / "stations.json"
LOGO_DIR = ASSETS / "logos"
CACHE = ROOT.parent / "work" / "ri" / "pages"

SITE = "https://www.radioindonesia.org"
SITEMAP = f"{SITE}/static/sitemaps/www_radioindonesia_org/sitemap-radio-detail-1.xml.gz"
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
      "Accept-Language": "id,en;q=0.8"}
MAX_PX = 192
MIN_BYTES = 900


# --------------------------------------------------------------------- util
def http_get(url: str, timeout: int = 25) -> bytes | None:
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read() if r.status == 200 else None
    except Exception:
        return None


def norm_name(name: str) -> str:
    s = unicodedata.normalize("NFKD", name).lower()
    s = s.replace("&", " dan ")
    s = re.sub(r"[,.;:_/\\-]", " ", s)
    s = re.sub(r"\b(fm|am|radio|streaming|online|dot|com|id)\b", " ", s)
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return " ".join(s.split())


def norm_name_loose(name: str) -> str:
    """Buang keterangan dalam tanda kurung: 'iSwara Bandung (I-Radio)' -> 'iswara bandung'."""
    s = re.sub(r"\([^)]*\)", " ", name)
    s = re.sub(r"\[[^\]]*\]", " ", s)
    return norm_name(s)


STOPWORDS = {
    "radio", "fm", "am", "streaming", "stream", "online", "the", "net", "network",
    "com", "id", "www", "co", "my", "app", "web", "site", "music", "musik", "chanel", "channel",
}


def tokens(*parts: str) -> set[str]:
    out: set[str] = set()
    for p in parts:
        if not p:
            continue
        for w in re.split(r"[^0-9a-z]+", p.lower()):
            if len(w) > 1 and w not in STOPWORDS and not w.isdigit():
                out.add(w)
    return out


def brand_ok(st: dict, t: "TheirStation") -> bool:
    """Tolak pencocokan domain kalau tidak ada satu pun kata merek yang sama.

    Contoh yang harus ditolak: stasiun kita "Bas Radio" dicocokkan ke halaman
    "Ganding City" hanya karena domain sameAs-nya sama (alhastream.com = hosting).
    """
    mine = tokens(st.get("name", ""))
    hay = re.sub(r"[^0-9a-z]+", "", f"{t.name}{t.domain or ''}".lower())
    if not mine:
        # nama seperti "B-Radio" tidak menyisakan token; bandingkan utuh
        whole = re.sub(r"[^0-9a-z]+", "", st.get("name", "").lower())
        return bool(whole) and whole in hay
    return any(tok in hay for tok in mine)

def norm_domain(url: str | None) -> str | None:
    if not url:
        return None
    try:
        host = urllib.parse.urlparse(url if "//" in url else "//" + url).netloc.lower()
    except Exception:
        return None
    host = host.split(":")[0]
    if host.startswith("www."):
        host = host[4:]
    return host or None


def slug_of(url: str) -> str:
    return url.rstrip("/").rsplit("/", 1)[-1]


# ------------------------------------------------------------------ extract
@dataclass
class TheirStation:
    slug: str
    name: str
    logo: str
    domain: str | None
    area: str | None
    page: str


JSONLD_RE = re.compile(
    r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', re.S)


def parse_page(html: str, page_url: str) -> TheirStation | None:
    for block in JSONLD_RE.findall(html):
        if "RadioStation" not in block:
            continue
        try:
            data = json.loads(block)
        except Exception:
            continue
        graph = data.get("@graph") or [data]
        for node in graph:
            if node.get("@type") != "RadioStation":
                continue
            name = (node.get("name") or "").strip()
            logo = (node.get("image") or "").strip()
            if not name or not logo.startswith("http"):
                continue
            same_as = node.get("sameAs") or []
            if isinstance(same_as, str):
                same_as = [same_as]
            domain = next((norm_domain(u) for u in same_as if norm_domain(u)), None)
            return TheirStation(
                slug=slug_of(page_url),
                name=name,
                logo=logo,
                domain=domain,
                area=node.get("areaServed"),
                page=page_url,
            )
    return None


def load_pages(sitemap_urls: list[str], use_cache: bool, workers: int = 8) -> list[TheirStation]:
    CACHE.mkdir(parents=True, exist_ok=True)
    out: list[TheirStation] = []

    def one(url: str) -> TheirStation | None:
        slug = slug_of(url)
        f = CACHE / f"{slug}.html"
        if not (use_cache and f.exists() and f.stat().st_size > 5000):
            body = http_get(url)
            if body:
                f.write_bytes(body)
            time.sleep(0.05)
        if not f.exists():
            return None
        return parse_page(f.read_text(encoding="utf-8", errors="ignore"), url)

    with cf.ThreadPoolExecutor(max_workers=workers) as ex:
        for i, res in enumerate(ex.map(one, sitemap_urls), 1):
            if res:
                out.append(res)
            if i % 100 == 0:
                print(f"  halaman diproses: {i}/{len(sitemap_urls)}")
    return out


# -------------------------------------------------------------------- match
def match_catalog(ours: list[dict], theirs: list[TheirStation]) -> tuple[dict, dict]:
    by_domain: dict[str, list[TheirStation]] = {}
    by_name: dict[str, list[TheirStation]] = {}
    by_loose: dict[str, list[TheirStation]] = {}
    for t in theirs:
        if t.domain:
            by_domain.setdefault(t.domain, []).append(t)
        by_name.setdefault(norm_name(t.name), []).append(t)
        by_loose.setdefault(norm_name_loose(t.name), []).append(t)

    matched: dict[str, TheirStation] = {}
    method: dict[str, str] = {}
    for st in ours:
        if st.get("logo_source") == "manual":
            continue
        dom = norm_domain(st.get("website"))
        dom_cands = [
            t for t in by_domain.get(dom, []) if brand_ok(st, t)
        ] if dom else []
        if len(dom_cands) == 1:
            matched[str(st["id"])] = dom_cands[0]
            method[str(st["id"])] = "domain"
            continue
        nm = norm_name(st["name"])
        cands = by_name.get(nm, [])
        if len(cands) == 1:
            matched[str(st["id"])] = cands[0]
            method[str(st["id"])] = "name"
            continue
        if len(cands) > 1:
            city = (st.get("city") or "").lower()
            cities = [c for c in cands if city and city in (c.area or "").lower()]
            if len(cities) == 1:
                matched[str(st["id"])] = cities[0]
                method[str(st["id"])] = "name+city"
                continue
        # tier 2: nama tanpa keterangan kurung, harus unik & kota konsisten bila diketahui
        loose = norm_name_loose(st["name"])
        cands2 = by_loose.get(loose, []) if loose else []
        if len(cands2) == 1:
            t = cands2[0]
            city = (st.get("city") or "").strip().lower()
            area = (t.area or "").lower()
            city_ok = (not city) or (not area) or (city in area) or (city in norm_name(t.name))
            if city_ok:
                matched[str(st["id"])] = t
                method[str(st["id"])] = "name-loose"
    return matched, method


# ----------------------------------------------------------------- download
def download_logo(their: TheirStation) -> tuple[bytes, str] | None:
    # urutan kualitas: tvos_radios (175px) -> seperti di JSON-LD -> radios/ -> 150px
    bases = [their.logo]
    m = re.search(r"(https://static\.mytuner\.mobi/media/)([a-z0-9_-]+)/(.+)$", their.logo)
    if m:
        root, folder, name = m.groups()
        for alt in ("tvos_radios", "radios", "radios-150px"):
            if alt != folder:
                bases.append(f"{root}{alt}/{name}")
    for url in bases:
        raw = http_get(url, timeout=20)
        if not raw:
            continue
        try:
            im = Image.open(io.BytesIO(raw))
            im.load()
        except Exception:
            continue
        if im.width < 32:
            continue
        im = im.convert("RGBA")
        im.thumbnail((MAX_PX, MAX_PX), Image.LANCZOS)
        out = io.BytesIO()
        im.save(out, format="WEBP", quality=90, method=6)
        blob = out.getvalue()
        if len(blob) >= MIN_BYTES:
            return blob, url
    return None


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-cache", action="store_true", help="unduh ulang semua halaman")
    ap.add_argument("--workers", type=int, default=8)
    args = ap.parse_args()

    print("1) sitemap…")
    sm = http_get(SITEMAP, timeout=60)
    if not sm:
        raise SystemExit("sitemap tidak bisa diunduh")
    import gzip
    xml = gzip.decompress(sm).decode("utf-8", "ignore")
    urls = re.findall(r"<loc>([^<]+)</loc>", xml)
    urls = [u.strip() for u in urls if "/podcast" not in u]
    print(f"   {len(urls)} halaman stasiun")

    print("2) unduh & parse halaman…")
    theirs = load_pages(urls, use_cache=not args.no_cache, workers=args.workers)
    print(f"   {len(theirs)} stasiun terbaca dari JSON-LD "
          f"({sum(1 for t in theirs if t.domain)} punya situs resmi)")

    doc = json.loads(CATALOG.read_text(encoding="utf-8"))
    ours = doc["stations"]
    print("3) mencocokkan…")
    matched, method = match_catalog(ours, theirs)
    from collections import Counter
    print(f"   cocok: {len(matched)}/{len(ours)} {dict(Counter(method.values()))}")

    print("4) unduh logo…")
    LOGO_DIR.mkdir(parents=True, exist_ok=True)
    by_id = {str(s["id"]): s for s in ours}
    new_logo = 0
    upgraded = 0
    failed = 0

    def do(sid: str) -> tuple[str, dict]:
        their = matched[sid]
        got = download_logo(their)
        if not got:
            return sid, {}
        blob, url = got
        rel = f"logos/{sid}.webp"
        (ASSETS / rel).write_bytes(blob)
        st = by_id[sid]
        prev = st.get("logo_source")
        return sid, {"rel": rel, "url": url, "prev": prev}

    with cf.ThreadPoolExecutor(max_workers=args.workers) as ex:
        for sid, res in ex.map(do, list(matched)):
            if not res:
                failed += 1
                continue
            st = by_id[sid]
            if not st.get("logo_asset"):
                new_logo += 1
            elif st.get("logo_source") == "site-favicon":
                upgraded += 1  # favicon situs diganti logo asli stasiun
            st["logo_asset"] = res["rel"]
            st["logo_url"] = res["url"]
            st["logo_source"] = "radioindonesia.org"
            st["logo_page"] = matched[sid].page

    # bersihkan logo lama dari sumber ini yang tidak lagi lolos pencocokan
    keep = {matched[sid].page and f"logos/{sid}.webp" for sid in matched}
    removed = 0
    for st in ours:
        if st.get("logo_source") != "radioindonesia.org":
            continue
        if str(st["id"]) in matched:
            continue
        rel = st.get("logo_asset") or ""
        if rel and rel not in keep:
            path = ASSETS / rel
            others = [
                x for x in ours
                if x is not st and x.get("logo_asset") == rel
            ]
            if not others and path.exists():
                path.unlink()
            for k in ("logo_asset", "logo_url", "logo_source", "logo_page"):
                st.pop(k, None)
            removed += 1
    if removed:
        print(f"   logo dicabut (pencocokan tidak lagi valid): {removed}")

    have = sum(1 for s in ours if s.get("logo_asset"))
    src: dict[str, int] = {}
    for s in ours:
        if s.get("logo_asset"):
            src[s.get("logo_source", "?")] = src.get(s.get("logo_source", "?"), 0) + 1
    doc["_meta"].setdefault("logos", {}).update({
        "total": len(ours),
        "with_logo": have,
        "by_source": src,
        "radioindonesia": {
            "matched": len(matched),
            "match_method": dict(Counter(method.values())),
            "new": new_logo,
            "upgraded_from_favicon": upgraded,
            "failed_download": failed,
            "revoked_brand_mismatch": removed,
        },
    })
    CATALOG.write_text(json.dumps(doc, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"   logo baru: {new_logo} · upgrade dari favicon: {upgraded} · gagal: {failed}")
    print(f"TOTAL logo: {have}/{len(ours)}  {src}")


if __name__ == "__main__":
    main()
