# Tahap 3 — Arsitektur Aplikasi "Oto Radio"

Nama paket: `com.otoradio.app` · Nama tampilan: **Oto Radio** · Hanya untuk sideload pribadi.

## 1. Stack (sesuai permintaan, versi terkunci)

| Komponen | Versi | Catatan |
|---|---|---|
| Kotlin | 2.0.21 | + Compose Compiler plugin 2.0.21 |
| AGP | 8.7.3 | compileSdk 35, minSdk 26, targetSdk 35 |
| Gradle | 8.9 | wrapper disertakan |
| UI | Jetpack Compose + Material 3 (BOM 2024.10.01) | `material-icons-extended` |
| Pemutar | Media3 1.4.1 (`exoplayer`, `exoplayer-hls`, `datasource-okhttp`, `session`, `ui`) | `MediaSessionService` |
| DI | Hilt 2.52 + KSP | `hilt-navigation-compose` 1.2.0 |
| DB | Room 2.6.1 | KSP compiler |
| Preferensi | DataStore Preferences 1.1.1 | key-value + JSON string untuk EQ |
| Gambar | **tanpa Coil** | logo dibundel sebagai WebP di `assets/logos/`, dimuat `LogoStore` (LruCache + decode di IO). Coil dihapus agar APK tidak membengkak |
| Jaringan | OkHttp 4.12.0 | data source stream + Radio Browser |
| Navigasi | Navigation Compose 2.8.4 (dependency siap) | shell memakai state tab + AnimatedVisibility |

## 2. Struktur paket

```
com.otoradio.app
├── OtoRadioApp.kt            @HiltAndroidApp
├── MainActivity.kt           edge-to-edge, izin notifikasi, host Compose
├── di/AppModule.kt           Room DB + DAO
├── data/
│   ├── model/Station.kt      model + parser JSON katalog + deteksi HLS
│   ├── local/CatalogLoader.kt   baca assets/stations.json & eq-presets.json
│   ├── local/LogoStore.kt       decode & cache logo assets/logos/*.webp (LruCache 64)
│   ├── local/Db.kt           FavoriteEntity, HistoryEntity, EqProfileEntity, DAO, RoomDatabase
│   ├── local/SettingsStore.kt   DataStore (tema, ICY, reconnect, EQ scope, EQ config)
│   ├── remote/RadioBrowserApi.kt  katalog tambahan (open API, 3 mirror)
│   └── RadioRepository.kt    gabungan katalog + favorit + riwayat + pencarian
├── audio/EqualizerController.kt  android.media.audiofx (EQ, BassBoost, Virtualizer, Loudness)
├── playback/
│   ├── RadioPlayerService.kt MediaSessionService + ExoPlayer + reconnect + sleep timer
│   ├── PlayerConnection.kt   MediaController (UI ⇄ service) + PlayerUiState
│   └── PlaybackBus.kt        StateFlow ICY title, error, sleep deadline
└── ui/
    ├── MainViewModel.kt      library, favorit, riwayat, EQ, settings
    ├── OtoApp.kt             scaffold, bottom nav, mini player, overlay Now Playing & EQ
    ├── theme/Theme.kt        token Oto (warna/tipe/shape) + dynamic color + AMOLED
    ├── components/Components.kt  artwork gradien, StationRow, MiniPlayer, FAB, dsb.
    └── screens/              Home, Search, Favorites, History, NowPlaying, Equalizer, Settings
```

## 3. Alur data & pemutaran

```
UI (Compose) ──tap stasiun──► MainViewModel.play()
        │                          │
        │                          ├─► PlayerConnection.playStation()
        │                          │      └─ sendCustomCommand(PLAY_STATION, bundle)
        │                          │             └─► RadioPlayerService.onCustomCommand
        │                          │                    ├─ build MediaItem (mime auto: HLS/MPD/progressive)
        │                          │                    ├─ ExoPlayer.setMediaItem + prepare + play
        │                          │                    ├─ OkHttpDataSource (UA browser, Icy-MetaData: 1)
        │                          │                    └─ EqualizerController.attach(sessionId)
        │                          ├─► Room: history (upsert)
        │                          └─► DataStore: lastStationId
        └─ collect StateFlow ────── PlayerUiState (MediaController listener + PlaybackBus ICY/error)
```

**Kenapa `MediaSessionService` + `MediaController`?** Supaya notifikasi media, kontrol lock screen/Bluetooth/headset, dan audio focus ditangani sistem (berbeda dari APK referensi yang memakai `MediaSessionCompat` + `MediaBrowserServiceCompat` manual). Ini titik di mana kita sengaja lebih "modern" daripada aplikasi referensi.

### Penanganan stream (mengikuti temuan Tahap 1)
- Mime dipilih otomatis: `.m3u8` → `APPLICATION_M3U8`, `.mpd` → MPD, sisanya progressive (`BASE_TYPE_AUDIO`).
- User-Agent browser Chrome 120 (beberapa server radio menolak UA non-browser), header `Icy-MetaData: 1`.
- `followRedirects` + `followSslRedirects` (banyak URL radio http→https atau lintas domain).
- Buffer: min 15 s / max 50 s / 2.5 s untuk mulai / 5 s setelah rebuffer (referensi: 25/50/1/2 s).
- **Fallback URL**: `url` → `url2` → `url3` (sama seperti logika `nl0.smali` di APK referensi).
- **Reconnect**: setelah semua URL gagal, backoff 2 s → 4 s → 6 s … maks 12 s, maksimum 6 percobaan (bisa dimatikan di Pengaturan). Status ditampilkan di Now Playing ("Menyambung ulang (n/6)…").
- **ICY**: `IcyInfo.title` dipakai sebagai judul lagu bila opsi "Judul lagu dari ICY" aktif.

### Sleep timer
Coroutine `delay(minutes * 60_000)` di service → `player.pause()`. Deadline dipublikasikan lewat `PlaybackBus.sleepDeadline` agar UI menandai ikon bulan.

## 4. Katalog stasiun

1. **Bawaan** — `assets/stations.json` (741 stasiun Indonesia, hasil Tahap 1, di-embed ke APK).
2. **Radio Browser** — `https://{de1,at1,nl1}.api.radio-browser.info/json/stations/search?countrycode=ID&order=clickcount&reverse=true&hidebroken=true`, dimuat sekali saat aplikasi dibuka; bila gagal (offline/blokir) aplikasi tetap jalan dengan katalog bawaan. Hasil digabung lewat `distinctBy(name+url)`.
3. Pencarian lokal mencakup nama, kota, frekuensi, genre.

### Logo stasiun
- Dihasilkan `tools/fetch_logos.py`: Radio Browser `favicon` (cocok token nama persis) → favicon situs resmi → fallback gradien di UI.
- Disimpan `assets/logos/<id>.webp`, maks 192 px, total ±4,0 MB untuk 683 dari 741 stasiun (92%).
- Dipakai di: baris daftar (48 dp), mini player (44 dp), Now Playing (artwork besar), dan artwork notifikasi media (via `LogoStore.loadBitmapBlocking`).

## 5. Equalizer

```
EqualizerController (singleton, Hilt)
  ├─ StateFlow<EqConfig>  ← UI (slider/knob/preset)
  ├─ attach(audioSessionId)   ← dipanggil service saat onAudioSessionIdChanged
  │     ├─ Equalizer(0, sessionId)        5 band UI → band native terdekat
  │     ├─ BassBoost(0, sessionId)        0…1000 mB  (UI 0…100 %)
  │     ├─ Virtualizer(0, sessionId)      0…1000     (UI "Surround sound" 0…100 %)
  │     └─ LoudnessEnhancer(sessionId)    0…2000 mB
  └─ persist ke DataStore (JSON, debounce 350 ms)
```

- **Band**: 60 Hz, 230 Hz, 910 Hz, 4 kHz, 14 kHz (label di layar EQ Oto), gain ±15 dB, langkah 0.1 dB.
- **Preset**: 16 preset dari `assets/eq-presets.json`; memilih preset = menyalin gain per band.
- **Pemetaan band**: jumlah band native perangkat bisa ≠ 5. Untuk tiap band native, dicari band UI terdekat berdasarkan `getCenterFreq()` lalu gain dipetakan. Perangkat dengan 5 band umumnya cocok persis.
- **Perangkat tidak mendukung**: setiap efek dibuka dalam `runCatching`; hasilnya (`Capabilities`) dipakai UI untuk menonaktifkan knob/slider (mis. Virtualizer tidak didukung → knob "Surround sound" otomatis nonaktif) dan menampilkan pesan error.
- **Per stasiun**: Pengaturan → "Cakupan EQ" = *Per stasiun*; tombol "Simpan untuk stasiun" menyimpan `EqProfileEntity` (Room). Saat stasiun itu diputar lagi, profil otomatis dipulihkan (`MainViewModel.play`).

## 6. Fitur lain

| Fitur | Status |
|---|---|
| Background playback + notifikasi media | Ya (MediaSessionService, `ic_stat_radio`) |
| MediaSession (lock screen, Bluetooth, headset) | Ya — metadata judul/artis + artwork bitmap gradien |
| Audio focus & audio becoming noisy | Ya (`setAudioAttributes(..., true)`, `setHandleAudioBecomingNoisy(true)`) |
| Sleep timer | Ya (sheet durasi 5/15/30/60/90 menit) |
| Judul lagu ICY | Ya (toggle) |
| Favorit & riwayat | Ya (Room, riwayat 100 terakhir) |
| Pencarian | Ya (lokal + katalog Radio Browser) |
| Tema gelap/terang/AMOLED/system + dynamic color | Ya |
| Alarm radio, rekam stream, Android Auto browse tree | **Belum** (lihat daftar kekurangan) |
