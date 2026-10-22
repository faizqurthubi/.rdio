# 06 — Uji jalan di emulator (verifikasi Tahap 5)

Tanggal: 2026-10-22. Tujuan: membuktikan APK benar-benar **jalan**, bukan hanya terkompilasi.

## 1. Kendala & jalan keluar (FAKTA)

| Percobaan | Hasil |
|---|---|
| Emulator ARM64 (`system-images;android-26;default;arm64-v8a`) | **Gagal**: `FATAL | QEMU2 emulator does not support arm64 CPU architecture` (emulator 37.2.12 menolak ABI ARM di launcher-nya) |
| Emulator x86_64 + KVM | Tidak mungkin: `/dev/kvm` tidak ada di sandbox |
| **Emulator x86_64 + `-accel off` (TCG, emulasi CPU penuh)** | **Berhasil**. Boot Android 8.0.0 dalam 126 s |

Konfigurasi yang dipakai:

```bash
sdkmanager --install "emulator" "system-images;android-26;default;x86_64"
avdmanager create avd -n x26 -k "system-images;android-26;default;x86_64" -d pixel
# config.ini: hw.ramSize=1536, hw.cpu.ncore=2, hw.gpu.mode=swiftshader_indirect
emulator -avd x26 -no-window -no-audio -no-boot-anim -no-snapshot -accel off \
         -gpu swiftshader_indirect -memory 1536 -cores 2 -partition-size 2048
```

Catatan operasional:
- `partition-size 2048` wajib; default minta 7,2 GB dan gagal karena disk tinggal ±4 GB.
- Emulator **pernah segfault** saat Gradle build berjalan bersamaan (2 vCPU, RAM 2 GB). Urutkan:
  build dulu, baru jalankan emulator.
- Muncul dialog **"System UI isn't responding"** — itu SystemUI emulator yang kelaparan CPU di TCG,
  bukan aplikasi kita (proses `com.otoradio.app` tetap hidup, UI tetap merespons setelah "Wait").
- Waktu yang wajar di TCG: `install` ±2,5 menit, cold start aplikasi ±12 s, tiap tap butuh 2–10 s.

## 2. Yang terbukti bekerja

| Uji | Bukti (FAKTA) |
|---|---|
| Instalasi | `adb install` → `Success`, Android 8.0.0 / x86_64 |
| Cold start | `Displayed com.otoradio.app/.MainActivity: +11s776ms`, tanpa crash |
| Katalog bawaan | Judul "Radio — 850 stasiun · Indonesia + 120 Radio Browser" |
| **Logo asli tampil** | `assets/logos/*.webp` ter-render di Beranda, "Baru diputar", Favorit, Now Playing, notifikasi |
| **Audio streaming** | `dumpsys media_session` → `state=3 (PLAYING)`, `position` naik 61 895 → 131 091 ms; log `google.mp3.decoder` + `AudioTrack` aktif |
| Audio focus | `MediaFocusControl: requestAudioFocus() from … AudioFocusManager` |
| Mini player | Muncul di atas NavigationBar dengan judul stasiun + tombol jeda |
| Now Playing | Terbuka, logo besar, judul + kota, tombol prev/play-pause/next, 5 tombol aksi bawah |
| Favorit | Tap hati → tab Favorit menampilkan stasiun tersimpan (persist ke Room) |
| Riwayat / "Baru diputar" | Kartu muncul setelah stasiun diputar |
| Pencarian | Ketik "jak" → hasil live (103.4 Radio DFM, Al-Mahabbah, Alternatif radio) |
| Pengaturan | Tema (System/Terang/Gelap/AMOLED), dynamic color, ICY, auto-reconnect, auto-play, sleep timer, cakupan EQ |
| **Equalizer** | Kurva 5 band + slider per band (60/230/910/3k/14k Hz, 0 dB), preset "Flat", Bass boost & Surround knob |
| **Notifikasi media** | "Oto Radio · 01:21" — judul stasiun + logo + tombol jeda, aksi lock screen tersedia |
| **Tetap main saat layar mati** | Layar dimatikan 25 s, posisi audio terus bertambah (foreground service + WAKE_LOCK bekerja) |

## 3. Bug yang ditemukan & diperbaiki

1. **Logo hilang di layar Favorit, Riwayat, dan Pencarian.**
   Penyebab: `StationRow(...)` dipanggil tanpa argumen `logoStore` di 3 tempat
   (`ui/screens/LibraryScreens.kt`). Akibatnya `rememberStationLogo(null, …)` selalu `null` dan
   jatuh ke tile inisial. Perbaikan: teruskan `logoStore` di ketiga pemanggilan.
2. **Kartu "Baru diputar" (RecentCard) memakai inisial, bukan logo.**
   Penyebab: `RecentCard` di `ui/screens/HomeScreen.kt` menggambar `Text(initials)` di atas
   gradien, tidak pernah memakai `StationArtwork`. Perbaikan: ganti isi kotak dengan
   `StationArtwork(station, size = 132, corner = 20, logoStore = logoStore)`.
3. **Now Playing: baris tombol bawah terpotong navigasi sistem.**
   Penyebab: `Column` di `NowPlayingScreen.kt` hanya `fillMaxSize()` tanpa inset system bar;
   tombol favorit/sleep-timer/EQ/share/volume turun ke zona navigation bar.
   Perbaikan: tambah `statusBarsPadding()` + `navigationBarsPadding()` dan kurangi spacer
   (28 dp → 10 dp di atas, 24 dp → 8 dp di bawah). Setelah perbaikan kelima tombol terlihat utuh.

Semua perbaikan diverifikasi ulang di emulator: build baru → `adb install -r` → screenshot
`12-beranda.png`, `13-favorit-fix.png`, `15-nowplaying-fix.png`.

## 4. Bukti visual

Folder `preview/emulator/` (1080×1920, dari `adb exec-out screencap`):

| File | Isi |
|---|---|
| `00-boot-pertama-systemui-notresponding.png` | Boot pertama + dialog SystemUI emulator |
| `03-play.png` | Beranda setelah stasiun diputar (mini player + riwayat) |
| `04-nowplaying.png` | Now Playing **sebelum** perbaikan padding (bug terlihat) |
| `05-pengaturan.png` | Pengaturan: tema & pemutar |
| `07-equalizer.png` | Equalizer 5 band + knob bass boost/surround |
| `08-favorit.png` | Favorit **sebelum** perbaikan (logo jadi tile "1R") |
| `12-beranda.png` | Beranda setelah perbaikan (RecentCard pakai logo asli) |
| `13-favorit-fix.png` | Favorit setelah perbaikan (logo muncul) |
| `15-nowplaying-fix.png` | Now Playing setelah perbaikan (semua tombol utuh) |
| `16-notifikasi.png` | Notifikasi media + kontrol lock screen |
| `17-cari.png` | Pencarian live "jak" |

## 5. Yang **tetap** belum teruji (jujur)

- **Android 13+ (targetSdk 35)**: emulator jalan di API 26 karena hanya image itu yang muat di
  disk/RAM sandbox. Perilaku izin `POST_NOTIFICATIONS` runtime, predictive back, dan
  dynamic color Material You baru aktif di Android 12+.
- **Bluetooth / Android Auto / headset fisik**: butuh perangkat keras.
- **Kualitas audio**: emulator `-no-audio`, jadi yang terbukti hanya pipeline decode + AudioTrack
  berjalan, bukan suara yang keluar dari speaker.
- **Stream HLS (`.m3u8`)**: tidak diuji; emulator TCG terlalu lambat untuk memastikan.
- **Sleep timer berakhir**: tidak diuji sampai habis (butuh 30 menit di emulator TCG).
- Uji ukuran layar lain (tablet, lipat) dan mode gelap/terang sistem belum dijelajahi semua.

## 6. Kesimpulan

Tahap 5 kini **terverifikasi sebagian**: aplikasi terbukti terpasang, tidak crash, memutar audio
sungguhan dari stream Indonesia, menampilkan logo asli, dan menyimpan favorit/riwayat. Tiga bug UI
yang hanya kelihatan saat runtime sudah ditemukan lewat pengujian ini dan diperbaiki.
Sisa pengujian yang butuh perangkat fisik tercatat di bagian 5.
