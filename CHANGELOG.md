# Riwayat rilis

Format ringkas. Detail teknis per revisi ada di `docs/`.

## 1.2 — versionCode 3 (2026-10-22)

**Pembaruan langsung dari dalam aplikasi.** Pengaturan → PEMBARUAN → *Periksa*.

- Aplikasi membaca `releases/latest` dari repo ini, membandingkan versi, lalu menampilkan
  "Versi X tersedia" beserta changelog singkat dan tombol *Perbarui*.
- Unduhan diverifikasi sebelum dipasang: **SHA-256 harus sama** dengan `digest` yang dilaporkan
  GitHub, **nama paket harus `com.otoradio.app`**, **versionCode harus lebih tinggi**, dan
  **sertifikat penanda tangan harus identik** dengan aplikasi terpasang. Gagal satu syarat =
  berkas dihapus, tidak ada instalasi.
- Rilis tanpa SHA-256 ditolak (tidak ada rilis seperti itu sejak Juni 2025).
- Hanya HTTPS yang diterima pada build rilis.
- Izin baru: `REQUEST_INSTALL_PACKAGES` (untuk memasang APK pembaruan) + FileProvider internal.
- Pemeriksaan otomatis 6 jam sekali saat aplikasi dibuka, plus tombol *Periksa* manual.
  Tanda merah kecil muncul di tab **Atur** bila ada versi baru.
- Dibangun dengan unit test baru (8 kasus: parsing rilis, pemilihan asset, perbandingan versi,
  penolakan URL non-HTTPS) — total 18 test lulus.

Catatan: versi 1.1 **tidak** punya fitur ini, jadi pembaruan ke 1.2 masih perlu dipasang manual
sekali. Setelah 1.2 terpasang, semua rilis berikutnya bisa diambil dari dalam aplikasi.


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
