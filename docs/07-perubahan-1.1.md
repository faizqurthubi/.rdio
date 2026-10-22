# 07 — Perubahan versi 1.1

Rilis: `dist/OtoRadio-1.1-release.apk` (5,7 MB, **release ter-minify**, ditandatangani keystore tetap).
Semua poin di bawah adalah permintaan pengguna setelah memakai versi 1.0 di HP.

| # | Permintaan | Status |
|---|---|---|
| 1 | Thumbnail kotak 1:1 (seperti pemutar kaset) | ✅ |
| 2 | Default audio tanpa proses EQ (raw dari stasiun) | ✅ |
| 3 | Ikon beranda diganti ikon radio | ✅ |
| 4 | Next/Prev mengikuti daftar asal (mis. Favorit) | ✅ |
| 5 | Favorit jadi sub-menu di bawah Playlist + playlist kustom | ✅ |
| 6 | Hapus semua sub-judul penjelasan | ✅ |
| 7 | Cara mengelola versi revisi tanpa install ulang | ✅ (lihat `08-rilis-dan-update.md`) |

## 1. Artwork 1:1
`ui/screens/NowPlayingScreen.kt`: kotak artwork memakai `BoxWithConstraints` +
`Modifier.size(minOf(maxWidth, maxHeight))` sehingga **selalu persegi** (1:1) berapa pun tinggi
layar, dengan `clip(RoundedCornerShape(28.dp))`. Ukuran gambar mengikuti sisi terpendek ruang
tersisa, jadi judul/penyanyi/tombol tidak lagi terhimpit.

## 2. Audio raw secara default
`audio/EqualizerController.kt` diubah menjadi **malas (lazy)**:

- `attach(audioSessionId)` tidak lagi langsung membuat `Equalizer`, `BassBoost`, `Virtualizer`,
  `LoudnessEnhancer`. Efek hanya dibuat lewat `createEffects()` saat pengguna **menyalakan** EQ.
- Selama EQ mati, tidak ada satu pun AudioEffect yang menempel di sesi audio → yang keluar
  benar-benar audio asli stasiun (tidak ada penguatan bass tersembunyi).
- Migrasi sekali jalan (`SettingsStore.eqRawDefaultApplied()`): pengguna yang sudah punya setelan
  EQ tersimpan otomatis di-reset ke `EqConfig()` (enabled = false, semua gain 0, bass 0).
- Saat EQ dinyalakan, efek dibuat tepat saat itu dan langsung menerima setelan tersimpan.

Bukti uji emulator: EQ mati → `Equalizer` tidak dibuat; setelah toggle dinyalakan, slider berubah
warna (efek terpasang). Screenshot `v11-08-equalizer.png` (mati) vs `v11-09-eq-aktif.png` (hidup).

## 3. Ikon navigasi
`ui/OtoApp.kt`: tab pertama memakai `Icons.Rounded.Radio` dengan label **Radio**
(sebelumnya `Icons.Outlined.Home` berlabel "Beranda").

## 4. Navigasi kontekstual (next/prev)
`ui/MainViewModel.kt` menyimpan antrean konteks:

```kotlin
fun play(station: Station, context: List<Station>? = null, label: String? = null)
fun next(); fun previous(); fun jumpTo(index: Int)
```

- Membuka stasiun dari **Favorit** menetapkan antrean = daftar favorit; `next` lanjut ke favorit
  berikutnya (bukan ke seluruh 850 stasiun).
- Berlaku juga untuk: playlist kustom, hasil pencarian, dan daftar "Semua stasiun".
- `next`/`previous` tidak lagi memutar ulang stasiun yang sama: bila antrean hanya 1 item,
  muncul pesan "Tidak ada stasiun berikutnya di daftar ini".
- Ikon antrean di Now Playing (sebelumnya mati) membuka **bottom sheet Antrean**: label sumber,
  daftar stasiun, dan ketuk untuk lompat (`jumpTo`).
- Snackbar menampilkan label antrean aktif lewat `queueLabel`.

## 5. Playlist
Struktur baru (Room v2, migrasi non-destruktif `MIGRATION_1_2` — favorit & riwayat lama aman):

- `playlists(id, name, createdAt)`
- `playlist_tracks(playlistId, stationId, name, streamUrl, ..., position, addedAt)`

Tab **Playlist** (`ui/screens/PlaylistScreens.kt`) berisi:

```
Favorit         ›   (playlist bawaan, jumlah stasiun)
Baru diputar    ›   (playlist bawaan)
— Playlist saya —
RadioJazz       ⋮   (buat/ganti nama/hapus)
+ Buat playlist
```

- Hati pada setiap baris stasiun tetap berfungsi: stasiun otomatis masuk **Favorit**.
- Menu **titik tiga** di Beranda/Cari/Playlist → "Tambah ke playlist" (bottom sheet, pilih
  playlist atau buat baru sekaligus menambah).
- Di dalam playlist kustom, titik tiga pada baris = hapus dari playlist.
- Tombol ▶ di header memutar seluruh isi playlist sebagai antrean.

## 6. Sub-judul dihapus
`ui/screens/SettingsScreen.kt`: seluruh teks penjelasan di bawah judul dihapus (Dynamic color,
ICY, sambung ulang, putar terakhir, Equalizer, cakupan EQ, bagian TENTANG). `SettingSwitch` /
`SettingRow` kini menerima `subtitle: String? = null`. Header Beranda juga tidak lagi menampilkan
"850 stasiun · Indonesia + 120 Radio Browser".

## Verifikasi
- `assembleDebug` + `testDebugUnitTest` (10 test) + `lintDebug`: **sukses, 0 error**.
- `assembleRelease` minify+shrink: **sukses**, 5.687.670 byte, 683 logo & `stations.json` utuh
  di dalam APK, sertifikat `CN=OtoRadio` (bukan debug lagi).
- Uji di emulator Android 8.0 API 26 (TCG): pasang APK release, jalankan, lalu bukti screenshot
  `preview/emulator/v11-*.png`:
  - `v11-01-beranda.png` — ikon radio, tanpa sub-judul, titik tiga tersedia
  - `v11-02-sheet.png` — lembar "Tambah ke playlist"
  - `v11-03-playlist.png` — Favorit/Baru diputar/Playlist saya
  - `v11-04-favorit.png` — isi Favorit (3 stasiun)
  - `v11-05-nowplaying.png` — artwork 1:1
  - `v11-06-antrean.png` — bottom sheet antrean
  - `v11-07-pengaturan.png` — tanpa sub-judul
  - `v11-08-equalizer.png`, `v11-09-eq-aktif.png` — EQ mati (raw) → dinyalakan
