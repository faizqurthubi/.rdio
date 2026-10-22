# Tahap 5 — Kekurangan & saran perbaikan

## A. Belum sempurna / belum dikerjakan

| # | Hal | Dampak | Saran |
|---|---|---|---|
| 1 | **Tidak ada uji di perangkat/emulator** (sandbox tanpa `/dev/kvm`) | Perilaku runtime (audio, notifikasi, EQ) belum terbukti; hanya terbukti kompilasi + unit test + lint | Pasang APK di HP, jalankan checklist `docs/04-implementasi.md` §4 |
| 2 | Penyimpanan EQ per stasiun hanya *recall otomatis* saat `eqScope = PER_STATION` | Bila pengguna memilih Global, profil per stasiun tetap ada tapi tidak dipakai | Tambahkan switch "gunakan profil stasiun ini" di layar EQ |
| 3 | Belum ada preset kustom yang bisa diberi nama (hanya preset bawaan + "Custom") | Pengguna tidak bisa menyimpan kurva favorit lintas stasiun | Simpan `EqPreset` kustom di Room/DataStore + UI daftar preset kustom |
| 4 | LoudnessEnhancer ada di controller tapi belum ada slider di UI | Fitur tidak terjangkau | Tambah knob ketiga atau masuk "Advanced" |
| 5 | Pemetaan band EQ 5 → band native memakai frekuensi terdekat | Pada perangkat 10-band, dua band native bisa memetakan ke band UI yang sama | Ganti ke interpolasi linear antar band UI |
| 6 | Android Auto hanya kontrol (metadata + tombol), belum ada *browse tree* | Di Android Auto pengguna tidak bisa memilih stasiun dari layar mobil | Ganti `MediaSession` → `MediaLibrarySession` + `onGetChildren`/`onSearch` |
| 7 | Rekaman stream, alarm radio, widget, Chromecast belum ada | Di luar cakupan waktu | Rekaman: `MediaCodec`+`MediaMuxer` dari `AudioProcessor`/`TeeAudioProcessor`; alarm: `AlarmManager` + `setExactAndAllowWhileIdle`; widget: Glance |
| 8 | Logo stasiun: **683/741 terpasang (92%)**; 58 sisa pakai tile gradien (server asal 404, penyiar tanpa situs/favicon, nama tidak bisa dicocokkan tanpa risiko salah pasang) | Sebagian daftar tanpa gambar; logo dari radioindonesia.org hanya ≤175 px | Override manual `assets/logos/<id>.webp` (lihat README §4), atau ambil logo HD dari situs/IG penyiar |
| 9 | Katalog Radio Browser dimuat penuh saat aplikasi dibuka | ±1–3 detik tambahan & kuota data kecil | Cache hasil di Room + refresh harian via WorkManager |
| 10 | Riwayat dibatasi 100 baris dan tidak bisa dihapus per item | Minor | Tambah swipe-to-delete |
| 11 | Tidak ada penanganan khusus `geoblocked=1` seperti aplikasi asli (toast "geoblocked") | Pengguna hanya melihat error stream | Tampilkan badge "mungkin geo-blocked" di baris stasiun |
| 12 | `minSdk 26` tapi tema Material You dinamis butuh Android 12 | Di Android 8–11 memakai palet fallback (sudah ada fallback, tapi belum diuji visual) | Uji di perangkat lama; sesuaikan kontras |
| 13 | APK debug 34 MB (tanpa R8; dex 65 MB tak terkompresi + 4,0 MB logo) | Boros ruang | Build `release` dengan `isMinifyEnabled=true` + signing config sendiri (estimasi 8–10 MB) |
| 14 | Belum ada test instrumentasi Compose (UI test) | Regresi UI tidak terdeteksi otomatis | `androidTest` + `createComposeRule` bila ada emulator |
| 15 | Nama kelas `EqConfig` disimpan di DataStore sebagai JSON tunggal | Tidak ada migrasi versi | Tambahkan field `version` bila format berubah |

## B. Asumsi yang diambil (bukan fakta)

1. **Preset EQ** — APK radio tidak punya EQ sama sekali, jadi 16 preset di `eq-presets.json` disusun dari label UI Oto (60/230/910/4k/14k Hz) + nilai EQ 5-band generik. Tidak ada preset yang diekstrak dari APK mana pun.
2. **Durasi animasi & ukuran dp** — screenshot Oto tidak memuat spesifikasi; nilai (350 ms transisi Now Playing, 28 dp radius, 72 dp FAB) adalah rekonstruksi agar terasa mirip.
3. **Warna** — Oto memakai Material You dinamis sehingga tidak ada satu nilai hex resmi; hex di `docs/02` adalah hasil sampling piksel screenshot tertentu, dipakai sebagai fallback saat dynamic color tidak tersedia/dimatikan.
4. **Judul ICY** — sekitar separuh stasiun (uji sampel 30 URL) mengirim metadata ICY; UI menanganinya sebagai opsional.
5. **Radio Browser** — endpoint/server mirror bisa berubah; aplikasi menelan error dan tetap berjalan dengan katalog bawaan.
6. **Kredensial API radiofmapp** — Basic auth hard-coded di APK asli; dipakai hanya untuk menarik katalog sekali (Tahap 1). Aplikasi baru **tidak** memanggil API itu saat runtime.

## C. Prioritas saran berikutnya

1. Uji di perangkat nyata + perbaiki temuan (terutama notifikasi, audio focus, dan EQ di vendor yang berbeda-beda).
2. `MediaLibrarySession` untuk Android Auto (browse stasiun).
3. Preset kustom bernama + slider Loudness.
4. Build release ter-minify untuk ukuran & performa.
5. Cache katalog Radio Browser di Room.

## Catatan verifikasi (2026-10-22)

Bagian "belum diuji di perangkat" kini sebagian sudah tertutup: APK diuji jalan di **emulator
Android 8.0 x86_64 mode TCG** (lihat `docs/06-uji-emulator.md`). Tiga bug UI ditemukan di uji
tersebut dan sudah diperbaiki: logo hilang di Favorit/Riwayat/Pencarian, kartu "Baru diputar"
memakai inisial alih-alih logo, dan baris tombol Now Playing terpotong navigation bar.
Sisa yang masih wajib diuji di perangkat fisik: Android 13+ (izin `POST_NOTIFICATIONS` runtime,
dynamic color), Bluetooth/Android Auto, kualitas audio, stream HLS, sleep timer sampai habis.
