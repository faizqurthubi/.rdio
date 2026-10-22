# Riwayat rilis

Format ringkas. Detail teknis per revisi ada di `docs/`.

## 1.1 — versionCode 2 (2026-10-22)

Permintaan revisi dari pemakaian v1.0:

1. **Artwork 1:1** — Now Playing memakai `BoxWithConstraints` + `size(minOf(maxWidth, maxHeight))`,
   jadi kotak artwork selalu persegi di layar apa pun (mirip tampilan pemutar kaset VCD).
2. **Audio default raw** — `EqualizerController` kini *lazy*: `Equalizer`, `BassBoost`,
   `Virtualizer`, `LoudnessEnhancer` baru dibuat saat EQ dinyalakan. Selama mati tidak ada satu
   AudioEffect pun menempel, jadi yang keluar suara asli stasiun. Ada migrasi sekali jalan yang
   mereset setelan EQ lama.
3. **Ikon radio** — tab utama memakai `Icons.Rounded.Radio` + label "Radio" (bukan ikon rumah).
4. **Navigasi kontekstual** — `MainViewModel.play(station, context, label)` menyimpan daftar asal;
   `next()/previous()/jumpTo()` mengikuti daftar itu. Membuka stasiun dari Favorit berarti next
   lanjut ke favorit berikutnya. Ikon antrean di Now Playing kini membuka bottom sheet Antrean.
5. **Playlist** — Room v2 (migrasi non-destruktif). Tab Playlist berisi **Favorit** dan
   **Baru diputar** sebagai playlist bawaan, plus playlist kustom (buat / ganti nama / hapus).
   Menu titik tiga → "Tambah ke playlist".
6. **Sub-judul dihapus** — semua teks penjelasan di Pengaturan dan header Beranda hilang.
7. **Rilis bertanda tangan** — keystore rilis + `signingConfig`, R8 `minify` + `shrinkResources`:
   APK 34 MB → 5,4 MB, `versionCode` 2. Semua rilis berikutnya memakai keystore yang sama
   sehingga bisa memperbarui tanpa instal ulang.

Verifikasi: `assembleDebug` + 10 unit test + lint sukses; `assembleRelease` sukses dengan 683 logo
dan katalog utuh di dalam APK; dipasang dan dijalankan di emulator Android 8.0.

## 1.0 — versionCode 1

Versi awal: katalog 741 stasiun, pencarian, favorit, equalizer, media session + notifikasi,
dynamic color. Debug-signed, 34 MB. **Tidak bisa di-update langsung ke 1.1** — harus uninstall
sekali karena tanda tangannya berbeda.
