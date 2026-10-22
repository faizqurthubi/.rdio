# 12 — Perubahan 1.3

Ringkasan revisi atas masukan pemakaian v1.2. Semua butir diuji di emulator Android 8.0
(screenshot `preview/emulator/v13-*.png`).

## 1. Garis progres diganti spektrum + titik LIVE berpendar

**Masalah:** Now Playing menampilkan `LinearProgressIndicator` yang terus merayap. Pada pemutar
musik garis itu berarti (durasi lagu diketahui), tetapi pada siaran langsung tidak ada durasi
total — garisnya hanya gerak semu tanpa makna.

**Perbaikan:** garis itu dihapus dan diganti dua elemen:

- `AudioSpectrum` (`ui/components/Spectrum.kt`) — 27 batang, tinggi mengikuti fungsi sinus dengan
  fase berbeda per batang, plus amplop lembut di tepi supaya bentuknya menyerupai spektrum audio.
  Beranimasi hanya ketika audio benar-benar berbunyi; saat jeda batangnya rata dan redup.
- `LiveDot` — titik merah berdenyut dengan halo di samping label `LIVE`. Saat menyambung labelnya
  berubah menjadi `MENYAMBUNG` dan titiknya diam.

**Catatan jujur (penting):** spektrum ini **dekoratif, bukan FFT dari audio asli**. Untuk
membaca spektrum sungguhan diperlukan `android.media.audiofx.Visualizer` yang menuntut izin
`RECORD_AUDIO` — izin yang akan muncul sebagai "merekam audio" di dialog izin. Untuk aplikasi
radio pribadi, menukar privasi dengan hiasan tidak sepadan, jadi dipilih animasi yang jujur
secara visual tanpa izin tambahan.

## 2. Ikon aplikasi baru: latar kuning, konten hitam

Sumber: `design/logo-boombox.svg` (dari pengguna, CorelDRAW, viewBox 6201×6111) dan
`design/logo-boombox.png` (pratinjau tiga bentuk: kotak, squircle, bulat).

- `drawable/ic_launcher_foreground.xml` — 19 `<path>` disalin dari SVG, `fillType="evenOdd"`,
  warna **hitam**, diskalakan ke *safe zone* 66/108 dp agar tidak terpotong masker adaptive icon
  apa pun (kotak, squircle, maupun bulat).
- `drawable/ic_launcher_background.xml` — kuning solid `#FFD400`.
- `mipmap-mdpi…xxxhdpi/ic_launcher.png` — raster 48–192 px untuk peluncur/pemilih yang belum
  memakai adaptive icon.

Warna kuning tersimpan di satu tempat (`ic_launcher_background.xml`); mengganti nada kuningnya
cukup mengubah satu baris itu.

## 3. Mini player: tombol sebelumnya/berikutnya

`MiniPlayer` menerima `onPrev`/`onNext` yang **hanya diisi bila antrean berisi lebih dari satu
stasiun** (`queue.size > 1` di `OtoApp`). Saat hanya ada satu stasiun, tombolnya tidak muncul
supaya tidak menjadi tombol mati.

## 4. Mini player: artwork tidak lagi pudar

**Masalah:** logo stasiun tertelan warna dinamis karena `StationArtwork` menggambar gradien di
belakang logo; pada logo PNG berlatar transparan, gradien itu tembus dan membuat gambar terlihat
kusam.

**Perbaikan:** `StationArtwork` mendapat parameter `solidBackground`. Di mini player dipakai
`surfaceContainerLowest` (latar netral) + cincin tipis `outline` 35%, dan ukuran naik 44 → 48 dp.
Hasilnya logo tampil utuh dan terpisah jelas dari permukaan berwarna dinamis.

## 5. Tombol back Android

**Masalah:** back langsung keluar aplikasi dari layar mana pun. Penyebabnya: tidak ada satu pun
`BackHandler` di kode.

**Perbaikan:** hierarki back sekarang berlapis:

1. `NowPlayingScreen` — back menutup sheet sleep timer bila terbuka, lalu menutup Now Playing.
2. `OtoApp` — back menutup (berurutan) sheet "Tambah ke playlist", sheet Antrean, Equalizer,
   Now Playing, halaman detail playlist, lalu kembali ke tab Radio.
3. Di tab Radio tanpa lapisan terbuka, `BackHandler` dinonaktifkan sehingga sistem yang menangani
   → keluar aplikasi seperti biasa.

Diuji: back dari Now Playing → kembali ke daftar (aplikasi tetap hidup); back sekali lagi dari
tab Radio → keluar ke launcher.

## Verifikasi

| Cek | Hasil |
|---|---|
| `assembleDebug` + 18 unit test | sukses, 0 gagal |
| `assembleRelease` (R8 + shrink) | sukses, 5.735.087 byte |
| Emulator Android 8.0: spektrum + LIVE | tampil, beranimasi saat berbunyi |
| Mini player: prev/pause/next | tampil saat antrean > 1 (terlihat di `v13-01`) |
| Ikon baru | muncul di launcher sebagai "Oto Radio" kuning-hitam (`v13-03`) |
| Tombol back | tidak keluar dari Now Playing; keluar hanya dari tab Radio |
