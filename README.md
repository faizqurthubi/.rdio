# OtoRadio

Pemutar radio internet pribadi untuk Android. Sideload, tanpa Play Store. Katalog 741 stasiun
+ 683 logo dibundel di dalam APK, jadi daftar stasiun tetap bisa dibuka tanpa jaringan.

**Versi terbaru: 1.1** (versionCode 2) — unduh dari tab **[Releases](../../releases/latest)**.

## Pasang

1. Unduh `OtoRadio-<versi>-release.apk` dari halaman Releases terbaru.
2. Buka file itu di HP → izinkan "Instal aplikasi dari sumber ini" untuk aplikasi yang membukanya.
3. Setelah terpasang, pembaruan berikutnya cukup menimpa versi lama — data favorit dan playlist
   tidak hilang, selama APK ditandatangani dengan keystore yang sama.

Verifikasi unduhan:
```bash
sha256sum -c SHA256SUMS
```

## Update otomatis (opsional)

Pasang [Obtainium](https://github.com/ImranR98/Obtainium) → **Add App** → tempel URL repo ini.
Obtainium akan memeriksa Releases dan menawarkan pembaruan.

## Build sendiri

```bash
./gradlew :app:assembleDebug
# rilis bertanda tangan butuh keystore.properties di root proyek (tidak masuk repo):
#   storeFile=keystore/otoradio-release.jks
#   storePassword=…
#   keyAlias=otoradio
#   keyPassword=…
./gradlew :app:assembleRelease
```

Butuh JDK 17 dan Android SDK (compileSdk 35). Di mesin RAM kecil pakai
`--no-daemon --console=plain`; `gradle.properties` sudah dibatasi ke 1 worker.

## Isi repo

| Path | Isi |
|---|---|
| `app/src/main/java/com/otoradio/app/` | kode Kotlin + Compose (player, playlist, EQ, pencarian) |
| `app/src/main/assets/` | `stations.json` (katalog) + `logos/` (683 logo) |
| `docs/` | catatan teknis & riwayat revisi (`07`–`10`) |
| `data/` | sumber data katalog & preset EQ |
| `scripts/` | `prepare-release.sh` (build + checksum + version.json), `restore-toolchain.sh` |
| `preview/emulator/` | tangkapan layar bukti uji v1.1 |
| `.github/workflows/release.yml` | CI: build rilis bertanda tangan, unggah ke Releases |

## Riwayat singkat

- **1.0** — versi awal (debug-signed, 34 MB).
- **1.1** — artwork Now Playing selalu 1:1; audio default benar-benar raw (efek EQ baru dibuat
  saat dinyalakan); ikon radio di tab utama; navigasi kontekstual (next/prev mengikuti daftar
  asal); Favorit jadi sub-playlist di samping "Baru diputar" & playlist kustom; semua sub-judul
  penjelasan dihapus; rilis bertanda tangan 5,4 MB. Lihat `docs/07-perubahan-1.1.md`.

## Catatan keamanan

Keystore rilis **tidak** ada di repo ini dan tidak akan pernah di-commit. Ia disimpan terpisah,
dan di CI hanya dipulihkan dari repository secret. Tanpa file itu, tidak ada pihak lain yang
bisa menerbitkan pembaruan yang diterima Android sebagai sah.

## Lisensi

Proyek pribadi, bukan untuk distribusi publik komersial. Katalog stasiun dikumpulkan dari
direktori publik pihak ketiga; logo milik masing-masing stasiun.
