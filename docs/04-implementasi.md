# Tahap 4 — Implementasi & verifikasi

Repo: `/home/user/otoradio` (Gradle multi-module, satu modul `:app`).

## 1. Riwayat build

| Langkah | Perintah | Hasil |
|---|---|---|
| Wrapper | `gradle wrapper --gradle-version 8.9` | OK (Gradle 8.9, AGP 8.7.3) |
| Build #1 | `./gradlew :app:assembleDebug` | Gagal: `SDK location not found` → `local.properties` ditambahkan |
| Build #2 | idem | Gagal: daemon dibunuh OOM (RAM sandbox 1.9 GB) → `swapfile 3 GB` + `-Xmx1200m`, `workers.max=1`, `kotlin.compiler.execution.strategy=in-process` |
| Build #3 | idem | Gagal kompilasi (14 error) → diperbaiki (lihat §2) |
| Build #4 | idem | **BUILD SUCCESSFUL** |
| Unit test #1 | `./gradlew :app:testDebugUnitTest` | 1 gagal (`isHls` memakai `android.net.Uri` → tidak tersedia di JVM) → `Station.isHls` dibuat murni string |
| Lint #1 | `./gradlew :app:lintDebug` | 30 error `UnsafeOptInUsageError` (Media3 `@UnstableApi`) → `@androidx.annotation.OptIn(UnstableApi::class)` pada `RadioPlayerService` |
| Build final | `assembleDebug` + `testDebugUnitTest` + `lintDebug` | **BUILD SUCCESSFUL**, 9 unit test lulus, lint 0 error |

## 2. Error kompilasi yang diperbaiki (jejak lengkap)

1. `RadioRepository.eqProfile` — fungsi DAO suspend dipanggil dari fungsi non-suspend → dijadikan `suspend`.
2. `PlayerConnection` — `pendingStation?.let { play(it) }` tidak ada (`play` bukan method kelas) → `playStation(it)`.
3. `IcyInfo.query` tidak ada di Media3 1.4.1 → `IcyInfo.url` (diverifikasi dengan `javap` pada AAR).
4. `MediaSession.ERROR_INVALID_COMMAND` / `MediaSession.RESULT_SUCCESS` tidak ada → `SessionResult.RESULT_ERROR_BAD_VALUE` / `SessionResult.RESULT_SUCCESS` (diverifikasi `javap`).
5. `LoadErrorInfo.loadErrorCount` tidak ada → kelas `RadioErrorPolicy` yang tidak terpakai dihapus.
6. `Components.kt` — `by animateFloatAsState` tanpa `import androidx.compose.runtime.getValue`.
7. `EqualizerScreen` — `MaterialTheme.colorScheme` dipanggil di dalam lambda `Canvas` (bukan composable) → warna di-hoist ke variabel.
8. `EqualizerScreen` — `detectDragGestures` memberi `Offset`, bukan `Float` → `drag.y`.

## 3. Verifikasi yang dijalankan

Karena sandbox tidak punya `/dev/kvm` maupun perangkat fisik, verifikasi runtime di perangkat tidak bisa dilakukan. Yang dijalankan sebagai gantinya:

1. **Kompilasi penuh** `assembleDebug` (Kotlin + KSP Hilt + Room + resource + dex + signing).
2. **Unit test JVM (9 test, semua lulus)**:
   - `Station.fromAssetJson` memetakan 11 field katalog + alt URL + subtitle + geoblocked.
   - Stasiun tanpa nama/URL ditolak.
   - Deteksi HLS (`.m3u8` dengan query string).
   - `EqConfig` JSON round-trip (termasuk `stationId=null`) dan input rusak → `null`.
   - Layout band = `[60, 230, 910, 3600, 14000]`, range −15…+15 dB.
   - **Asset katalog asli**: 741 entri terbaca, 741 terparse, semua nama tidak kosong, semua URL `http/https`, 200+ punya URL cadangan, urutan alfabetis, `CatalogLoader.parseStations` (jalur kode yang sama dengan aplikasi) menghasilkan 741.
   - Asset `eq-presets.json`: 16 preset, tiap preset 5 band.
3. **Lint** (`lintDebug`) — 0 error.
4. **Pemeriksaan APK**: `apksigner verify` (debug key), `aapt2 dump badging` (package `com.otoradio.app`, minSdk 26, targetSdk 35, launchable `MainActivity`), dan aset `stations.json` (356 KB) + `eq-presets.json` benar-benar ada di dalam APK.

## 4. Cara menguji di perangkat

```bash
adb install -r /home/user/dist/OtoRadio-1.0-debug.apk
# atau: buka file APK di HP, izinkan "Install unknown apps"
```

Checklist uji manual:
1. **Katalog** — buka aplikasi: header "Radio", jumlah stasiun 741 (+ hasil Radio Browser bila online).
2. **Putar** — ketuk "103.4 Radio DFM": mini player muncul di atas bottom nav, notifikasi media tampil, audio keluar. Cek juga stasiun RRI (`rri…mp3`) dan satu HLS bila ada.
3. **Now Playing** — ketuk mini player: layar penuh geser naik; uji tarik-ke-bawah untuk menutup, tombol putar/jeda, next/prev (ganti stasiun), share.
4. **ICY** — pada stasiun yang mengirim metadata, judul lagu berubah di Now Playing (matikan lewat Pengaturan → "Judul lagu dari ICY").
5. **Reconnect** — matikan Wi-Fi saat memutar: muncul "Menyambung ulang (n/6)…", lalu pulih saat jaringan kembali.
6. **Sleep timer** — ikon bulan → pilih 5 menit → mulai; pemutaran berhenti sendiri.
7. **Equalizer** — ikon grafik EQ: nyalakan switch, ubah preset (mis. Rock), geser 5 slider, putar knob Bass boost/Surround. Bandingkan suara saat switch off/on. Cek pesan bila perangkat tidak mendukung suatu efek.
8. **Per stasiun** — Pengaturan → Cakupan EQ "Per stasiun" → atur EQ → "Simpan untuk stasiun" → pindah stasiun → kembali: setelan dipulihkan.
9. **Favorit & riwayat** — hati di baris stasiun → tab Favorit; riwayat muncul di Beranda ("Baru diputar") dan layar Riwayat.
10. **Tema** — Pengaturan: System/Terang/Gelap/AMOLED + dynamic color on/off.
11. **Ukuran layar** — coba di layar kecil (≤5") dan tablet: daftar & Now Playing harus tetap terbaca (artwork proporsional, teks ellipsis).

## 5. Catatan teknis build di mesin ini

- JDK 17 (Temurin) di `/home/user/tools/jdk-17.0.20.1+1`, Android SDK di `/home/user/android-sdk` (platform 35, build-tools 35.0.0 & 34.0.0), Gradle 8.9.
- `local.properties` berisi `sdk.dir=/home/user/android-sdk` (tidak di-commit).
- Perlu swap 3 GB agar tidak OOM saat Kotlin+KSP+Hilt kompilasi:
  `sudo fallocate -l 3G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`
- Build bersih dari nol ≈ 2–3 menit; incremental ≈ 20–60 detik.
