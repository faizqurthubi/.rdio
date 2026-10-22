# Tahap 2 — Referensi UI Oto Music

Sumber yang dipakai:
1. Artikel HowToGeek `i-tested-the-best-personal-music-players-on-android-...` (Apr 2026) — **diblokir bot** untuk fetch biasa (halaman mengembalikan reCAPTCHA). Diambil dengan **Scrapling 0.5 `Fetcher.get(stealthy_headers=True)`** → HTTP 200, HTML disimpan `reference/oto/howtogeek.html`.
2. Halaman Play Store resmi `com.piyush.music` (Scrapling `Fetcher`) — deskripsi fitur + 24 screenshot resmi → `reference/oto/ps/shot_*.png`.
3. Screenshot store lain: `reference/oto/play_1..5.png`, potongan detail equalizer `reference/oto/eq_crop.png`.
4. Ulasan/publikasi pendukung: Android Police, MakeUseOf, Softpedia/Softonic, APKMirror changelog, Slashdot review summary (untuk daftar fitur & versi EQ).

Legenda: **[FAKTA]** = terlihat langsung di screenshot/teks sumber. **[ASUMSI]** = penyesuaian/rekonstruksi nilai oleh saya.

---

## 1. Prinsip desain yang terbaca

- **[FAKTA]** Oto memakai **Material You (Material 3)** dengan warna aksen dinamis (screenshot memakai aksen pink, hijau, ungu, biru tergantung wallpaper). HowToGeek: *"excellent Material You design … feels like it came straight out of Google's app design studio"*.
- **[FAKTA]** Skala tipografi besar dan *left-aligned* agresif: judul layar "Settings"/"Equalizer"/"Edit lyrics" ±28–32 sp, di bawah app bar dengan judul kecil.
- **[FAKTA]** Tema: light (background putih/cream, mis. `#F1ECDB` pada poster), dark (surface hangat `#1F1A1E`, `#201A1A`, `#322527` — bukan hitam murni), dan AMOLED (hitam murni) + "battery saver". Teks dark theme tidak putih murni (≈`#E6E1E5`).
- **[FAKTA]** Kartu besar dengan sudut membulat (≥24 dp), *pill* search bar penuh lebar dengan ikon hamburger di kiri.
- **[FAKTA]** Now Playing: artwork besar dengan sudut membulat, judul di tengah + artis kecil, lalu baris kontrol 5 tombol: repeat, prev, **FAB play bulat besar**, next, shuffle. Di bawah: slider volume dengan ikon speaker kiri-kanan. Baris bawah: ikon lirik, hati (favorit), bulan (sleep timer), antrean, dan "…" (more).
- **[FAKTA]** Mini player: kartu lebar dengan sudut bulat besar, artwork persegi kiri, judul + artis, tombol play di kanan, dan **seek bar tipis / progress line berwarna aksen** di bawah teks (terlihat pada `play_2.png`).
- **[FAKTA]** Bottom navigation berlatar surface, 3–4 item ikon saja (tanpa label pada beberapa konfigurasi), dengan pil aksen sebagai indikator item terpilih.
- **[FAKTA]** Equalizer: app bar "Equalizer" dengan panah kembali di kiri, **ikon bar chart** dan **switch on/off** di kanan; di bawahnya field outline `Select preset` (dropdown); deretan 5 slider vertikal custom dengan label dB di atas tiap slider dan label frekuensi di bawah; dua **knob melingkar** "Bass boost" dan "Surround sound".
- **[FAKTA]** Sleep timer tampil sebagai bottom sheet bertema surface (`play_2`/`ps/shot_15`): judul "Sleep timer", angka besar `5 min`, daftar cepat `5 min`, tombol `Start`, `Cancel`, `End of the song`.

---

## 2. Token desain (angka yang akan saya pakai)

### 2.1 Warna
Nilai **[FAKTA]** = hasil sampling piksel screenshot (`PIL`, lihat `reference/oto/ps/shot_16.png`, `play_2.png`, `play_5.png`). Nilai peran (role) Material 3 ditandai **[ASUMSI]** bila tidak bisa diukur langsung.

| Token | Light | Dark | AMOLED | Catatan |
|---|---|---|---|---|
| `surface` | `#FBF8F8` | `#1F1A1E` **[FAKTA]** | `#000000` | dark surface hangat, bukan abu netral |
| `surfaceContainer` (kartu, mini player) | `#F3EAF3` **[FAKTA]** | `#201A1A` **[FAKTA]** | `#0A0A0A` | |
| `surfaceContainerHigh` (bottom sheet/NP) | `#EDE4ED` | `#322527` **[FAKTA]** | `#141414` | |
| `primary` (aksen) | `#855C8E` **[FAKTA]** (contoh tema ungu) | `#D0BCFF`-type, di screenshot pink `#F8B0C2` **[FAKTA]** | sama | dinamis dari Material You |
| `onPrimary` | `#FFFFFF` | `#322527` | `#000000` | |
| `primaryContainer` (FAB play) | `#F5D9E2` **[FAKTA]** | `#F8B0C2` **[FAKTA]** | `#F8B0C2` | |
| `onSurface` | `#1F1A1E` | `#E6E1E5` | `#E6E1E5` | |
| `onSurfaceVariant` (subjudul/artis) | `#7A757A` | `#CAC4C9` | `#CAC4C9` | |
| `outline` (border field) | `#857378` | `#5A4A50` | `#3A3A3A` | |
| `scrim` | 40% hitam | 60% hitam | 70% hitam | |

Karena target "semirip mungkin", aplikasi baru memakai **Material You dynamic color bila tersedia**, dengan fallback palet di atas (aksen default pink `#F8B0C2` / ungu `#855C8E`). Nilai asli tiap preset EQ yang diekstrak ada di `/data/eq-presets.json`.

### 2.2 Tipografi
- **[FAKTA]** Huruf sans-serif geometris, sangat mirip **Google Sans / Roboto**. Aplikasi baru: `Roboto` (bawaan Android) atau `Google Sans Text` bila tersedia; **[ASUMSI]** agar tidak menambah aset berlisensi, saya pakai default `Roboto` dengan tracking negatif pada judul besar.
- Skala **[ASUMSI, mengikuti proporsi screenshot]**:
  | Peran | Ukuran | Weight | Line height |
  |---|---|---|---|
  | Display (judul layar) | 32 sp | Medium (500) | 40 |
  | Headline (judul seksi) | 24 sp | Medium | 32 |
  | Title large (judul NP) | 22 sp | Medium | 28 |
  | Title medium (item list) | 16 sp | Medium | 24 |
  | Body (artis/deskripsi) | 14 sp | Regular | 20 |
  | Label (chip, label dB/Hz) | 12 sp | Medium | 16 |

### 2.3 Bentuk, jarak, elevasi
| Token | Nilai |
|---|---|
| Radius kartu besar / bottom sheet / artwork NP | 28 dp / 28 dp atas / 24 dp |
| Radius mini player | 20–24 dp |
| Radius search bar / field | 28 dp (full pill) |
| Radius tombol FAB play | 50% (bulat, 64–72 dp) |
| Padding horizontal layar | 20 dp |
| Jarak antar item list | 8–12 dp |
| Tinggi item list (artwork 48 dp) | 68–72 dp |
| Tinggi mini player | 64–72 dp (+ seek 2 dp) |
| Bottom nav tinggi | 80 dp (M3) |
| Elevasi mini player | 3 dp + shadow lembut |
| Ketebalan seek bar | 2 dp (track) / 3 dp (aktif) |

### 2.4 Ikon
- **[FAKTA]** Ikon Material Symbols *outlined*, tebal 2 dp, ukuran 24 dp; ikon "diperbesar" 28 dp untuk kontrol utama. Ada "animated icons" (fitur resmi Oto).
- Ikon yang dipakai ulang: repeat, skip_previous, play/pause, skip_next, shuffle, favorite/heart, nightlight (sleep), queue_music, more_horiz, search, menu.

### 2.5 Animasi (nilai **[ASUMSI]** — target *feel* Oto, bukan pengukuran)
| Interaksi | Spesifikasi |
|---|---|
| Buka Now Playing dari mini player (swipe up / tap) | shared-element artwork + slide-in vertikal, **350 ms**, `FastOutSlowInEasing`, artwork scale 0.8→1.0 |
| Tutup Now Playing (swipe down / back / drag handle) | reverse, 300 ms; **drag-to-dismiss** dengan offset mengikuti jari |
| Transisi tab bottom nav | fade-through 180 ms + slide 8 dp |
| Tombol play ↔ pause | morph ikon dengan crossfade + scale pulse 120 ms |
| Indikator nav terpilih | pil aksen animate 250 ms `spring(damping 0.8)` |
| Equalizer slider | thumb mengikuti jari 1:1, kurva EQ beranimasi 120 ms saat preset diganti |
| Rotasi/scale artwork saat buffering | indikator putar 1.2 s loop |
| Sleep timer sheet | slide up 300 ms + scrim fade 200 ms |
| Tekan lama item list | multi-select riak + toolbar slide 200 ms |
| "Delightful animations" (resmi Oto) | scale-down 0.96 saat press pada mini player & FAB |

### 2.6 Artwork & visual EQ
- **[FAKTA]** Artwork besar persegi, sudut bulat 24 dp, tanpa border. Di Now Playing tema Material You, background bisa memakai warna turunan artwork (blur/dinamis).
- **[FAKTA]** Slider EQ: track vertikal tipis (2 dp) abu-abu; thumb = **kotak membulat (persegi panjang r≈10 dp, tinggi ±28 dp)** transparan dengan garis tepi, dan ada garis putih kecil di tengah sebagai indikator posisi. Baris label `0 db`, `7 db`, `8 db` di atas dan `60 Hz … 14 kHz` di bawah.
- **[FAKTA]** Knob "Bass boost"/"Surround sound": lingkaran 96–120 dp, cincin progres (arc) tebal ±6 dp berwarna aksen, indikator garis kecil di dalam, label di bawah.
- **[ASUMSI]** Untuk radio (tidak ada artwork), slot artwork diganti **tile gradien + inisial stasiun** dengan palet turunan nama stasiun, radius & ukuran sama dengan artwork Oto.

---

## 3. Layout per layar (spesifikasi implementasi)

1. **Beranda/Jelajah** — app bar ringkas (judul "Radio" + ikon search), search bar pill penuh lebar (hamburger/ikon filter kiri), deretan chip "History / Last added / Most played" gaya Oto **diganti**: `Terbaru`, `Favorit`, `Terdekat/Populer`, `Genre`; lalu list lagu/stasiun (artwork 48 dp, judul 16 sp, subjudul 14 sp, menu titik tiga). Mini player menempel di atas bottom nav.
2. **Pencarian** — search bar aktif saat dibuka, hasil dikelompokkan (Stasiun, Kota, Genre), empty state ikon + teks.
3. **Favorit** / **Riwayat** — list sama seperti beranda, empty state gaya Oto (`fr_favorites` di app lama: ikon 100 dp + judul 20 sp bold + subteks 16 sp + tombol aksen).
4. **Now Playing** — dibuka dari mini player (swipe up). Struktur: drag handle, artwork besar, judul + subjudul (judul lagu ICY / nama stasiun), baris kontrol 5 tombol dengan FAB play 72 dp, baris ikon bawah (lirik→info ICY, hati→favorit, bulan→sleep timer, antrean→daftar stasiun lain, "…"→menu: EQ, share, alarm).
5. **Equalizer** — persis struktur bagian 2.6: app bar + switch, dropdown preset, 5 slider, 2 knob, ditambah (khusus radio) kartu "Terapkan per stasiun".
6. **Pengaturan** — mengikuti struktur `play_2.png`: header besar "Settings", grup *THEMES & COLORS*, *PLAYER*, *AUDIO*, masing-masing item: judul 16 sp + deskripsi 13 sp + kontrol di kanan (switch/chevron). Grup yang saya pakai: Tampilan (tema, dynamic color, AMOLED), Pemutar (reconnect, buffer, judul ICY, autoplay), Audio (EQ, bass boost, virtualizer), Lain (sleep timer default, alarm, tentang).
7. **Bottom nav** — 4 item: Home, Cari, Favorit, Pengaturan (Oto memakai Songs/Albums/Artists/Playlists; saya sesuaikan untuk radio).

---

## 4. Yang tidak bisa diverifikasi
- Nilai animasi/durasi & ukuran dp pastikan **[ASUMSI]** — screenshot tidak menyertakan metadata spesifikasi.
- Warna aksen Oto bersifat dinamis (Material You), jadi tidak ada satu nilai hex "resmi"; nilai yang saya sampling adalah contoh tema tertentu.
- Tidak ada akses ke kode sumber Oto (aplikasi komersial); semua analisis dari gambar & teks publik.
