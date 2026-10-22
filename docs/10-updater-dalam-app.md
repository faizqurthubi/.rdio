# 10 — Updater di dalam app di atas GitHub Releases

Jawaban singkat: **bisa, dan hampir semua yang dilakukan Obtainium bisa ditiru** — karena
Obtainium sendiri tidak memakai API rahasia. Ia hanya membaca API rilis publik (GitHub, GitLab,
Codeberg, F-Droid, dsb.), membandingkan nomor versi, mengunduh APK, lalu menyerahkannya ke
`PackageInstaller` Android. Semua itu bisa kita panggil dari dalam OtoRadio.

Bedanya cuma: Obtainium adalah *shell* generik untuk banyak sumber; updater bawaan hanya untuk
satu repo — justru lebih sederhana dan bisa dibuat lebih pas (changelog tampil di dalam app).

## Apa yang "immutable" di GitHub Releases (fakta, bukan asumsi)

Fitur **Immutable Releases** sudah GA sejak 28 Okt 2025 dan gratis untuk semua paket.
Kalau diaktifkan di level repo (`Settings → General → Releases → Enable release immutability`):

| Objek | Bisa diubah setelah publish? |
|---|---|
| Asset (file APK) | **Tidak** — tidak bisa ditambah, diganti, dihapus |
| Tag git | **Tidak** — terkunci ke satu commit, tidak bisa digeser/dihapus |
| Attestation | Dibuat otomatis (ditandatangani GitHub) sebagai bukti tag + commit + asset |
| Judul & catatan rilis | **Ya, masih bisa diedit pemilik repo** |

Poin terakhir penting dan sering salah diklaim: **catatan rilis tidak ikut terkunci.** Jadi
"changelog immutable" sebenarnya berarti dua hal berbeda:

1. **Mengunci APK-nya** (yang benar-benar penting) → pakai Immutable Releases. Ini yang membuat
   orang lain tidak bisa menukar file di rilis lama.
2. **Mengunci teks changelog** → kalau memang ingin benar-benar tak bisa diubah, taruh
   `CHANGELOG.md` di dalam repo (terkunci oleh tag) atau di dalam APK sebagai asset. Updater
   membacanya dari tag, bukan dari badan rilis.

Sejak Juni 2025 GitHub juga menghitung **SHA-256 tiap asset** dan menampilkannya di API
(`assets[].digest = "sha256:…"`). Artinya checksum tidak perlu kita terbitkan sendiri — cukup
diverifikasi terhadap nilai yang dilaporkan GitHub.

## Endpoint yang dipakai (hanya 1 request untuk cek update)

```
GET https://api.github.com/repos/{owner}/{repo}/releases/latest
Accept: application/vnd.github+json
```
Field yang kita ambil:

| Field | Kegunaan |
|---|---|
| `tag_name` | `v1.2` → dibandingkan dengan `versionCode` di `version.json` / `BuildConfig` |
| `body` | changelog → ditampilkan di kartu "Versi baru tersedia" |
| `published_at` | tanggal rilis |
| `immutable` | `true` = asset terkunci (bisa ditampilkan sebagai lencana) |
| `assets[].name` | memilih `OtoRadio-*-release.apk` |
| `assets[].size` | progres unduhan |
| `assets[].digest` | `sha256:…` → **wajib** dicocokkan sebelum instal |
| `assets[].browser_download_url` | URL unduhan langsung (tanpa auth untuk repo publik) |

Batas rate: 60 request/jam tanpa token per IP. Dengan cek `WorkManager` sekali sehari,
sangat jauh dari batas. Repo **privat** butuh Personal Access Token yang ditanam di APK —
dan token di APK bisa diekstrak. Karena itu rekomendasi: repo **publik**, isinya APK + changelog
saja, tanpa kunci signing.

## Rangkaian keamanan (ini yang membuatnya setara/bahkan lebih baik dari Obtainium)

1. **Digest wajib cocok.** Hitung SHA-256 file hasil unduhan, bandingkan dengan `assets[].digest`
   dari API. Beda satu byte → tolak, hapus file.
2. **Tanda tangan wajib sama.** Android sendiri sudah menolak update yang cert-nya berbeda. Kita
   tambahkan pemeriksaan proaktif: `PackageManager.getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)`
   → bandingkan dengan cert app terpasang. Salah → batalkan sebelum dialog instal muncul.
3. **Nama paket wajib sama** (`com.otoradio.app`) — mencegah APK "updater" palsu.
4. **Tolak downgrade**: `versionCode` baru harus > yang terpasang.
5. **HTTPS saja**, tanpa redirect ke domain lain, tanpa `usesCleartextTraffic`.
6. Immutable Releases + attestation → rilis lama tidak bisa ditukar di sisi server.

Kombinasi 1+2 berarti penyerang harus memegang **keystore** kita untuk bisa menyodorkan update
palsu — sama persis dengan model keamanan Play Store, hanya kuncinya di tangan kita.

## Yang harus ditambahkan ke kode

| Bagian | Isi |
|---|---|
| `AndroidManifest.xml` | `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>`, `<uses-permission android:name="android.permission.INTERNET"/>` (sudah ada) |
| `data/UpdateChecker.kt` | `OkHttp`/`HttpURLConnection` ke API, parse JSON (`org.json` bawaan, tanpa dependency baru), bandingkan `versionCode` |
| `data/ApkDownloader.kt` | unduh ke `cacheDir`, hitung SHA-256 streaming, verifikasi digest + signer |
| `data/ApkInstaller.kt` | `PackageInstaller.Session` (bukan `Intent.ACTION_VIEW`) + `FileProvider` + `BroadcastReceiver` untuk hasil |
| `ui/PengaturanScreen.kt` | baris "Versi 1.1 → 1.2 tersedia" + tombol *Unduh & perbarui*, progress, dan changelog singkat |
| `UpdateWorker.kt` | `WorkManager` periodik (24 jam, hanya saat ada jaringan, best-effort) |
| `BuildConfig` | `UPDATE_REPO = "owner/otoradio"` (atau URL `version.json` statis) |

Izin yang diminta ke pengguna: satu kali "Izinkan pemasangan aplikasi dari sumber ini"
(Android 8+ per-app). Tidak butuh root, tidak butuh Play Services, tidak butuh app lain.

## Batasan yang jujur

- **Tidak bisa memasang tanpa interaksi** (kecuali app jadi Device Owner / pakai `adb`). Android
  selalu menampilkan dialog konfirmasi. Obtainium pun begitu.
- **Cek latar belakang tidak real-time.** `WorkManager` dijadwalkan Android, bisa tertunda saat
  baterai lemah. Sama seperti Obtainium.
- Kalau HP memblokir pemasangan dari "sumber tidak dikenal", pengguna harus mengizinkannya sekali.
- Pembaruan tetap **butuh kuota data** (APK 5–6 MB). Tidak ada cara menghindari itu; yang hilang
  hanyalah langkah manualnya.

## Perbandingan akhir

| Kemampuan | Obtainium | Updater bawaan (rencana ini) |
|---|---|---|
| Deteksi versi baru | ✓ | ✓ |
| Notifikasi | ✓ (notifikasi sistem) | ✓ (opsional) |
| Changelog tampil | ✓ | ✓ (langsung di Pengaturan, lebih ringkas) |
| Verifikasi SHA-256 | ✓ (opsional) | ✓ (wajib) |
| Cek tanda tangan APK | ✓ | ✓ |
| Bisa banyak sumber/repo | ✓ | ✗ (hanya repo kita) |
| Butuh app tambahan | ✓ (harus instal) | ✗ |
| Kode yang harus dirawat | — | ±300 baris Kotlin |

Rekomendasi: **pakai keduanya** — GitHub Releases sebagai kanal resmi (immutable, ada
attestation, bisa dibuka dari browser), updater bawaan sebagai jalur utama sehari-hari, dan
Obtainium sebagai cadangan kalau updater bawaan bermasalah.

---

# Implementasi (v1.2, versionCode 3) — sudah terpasang & teruji

## Berkas baru

| Berkas | Isi |
|---|---|
| `data/update/UpdateInfo.kt` | model + `ReleaseParser` (murni, bisa diuji tanpa Android): parsing JSON, pemilihan asset `*-release.apk`, perbandingan versi numerik, **tolak URL non-HTTPS** |
| `data/update/UpdateRepository.kt` | panggil `releases/latest`, unduh dengan progres, hitung SHA-256 streaming, verifikasi paket + sertifikat |
| `data/update/ApkInstaller.kt` | cek izin `REQUEST_INSTALL_PACKAGES`, buka layar izin, dan serahkan APK ke dialog sistem lewat FileProvider |
| `ui/UpdateViewModel.kt` | state: Idle → Checking → Available → Downloading(%) → ReadyToInstall → UpToDate / Error / NeedPermission |
| `res/xml/file_paths.xml` | jalur `cache-path` untuk FileProvider (authority `${applicationId}.updates`) |
| `app/src/test/.../UpdateParserTest.kt` | 8 unit test |

## Perubahan pada berkas lama

- `AndroidManifest.xml`: izin `REQUEST_INSTALL_PACKAGES` + `<provider FileProvider>`.
- `app/build.gradle.kts`: `buildConfig = true`, field `UPDATE_REPO="faizqurthubi/.rdio"`,
  `UPDATE_API_URL` (kosong di rilis; bisa di-override hanya pada build debug).
- `SettingsScreen`: bagian **PEMBARUAN** (versi, status, tombol Periksa/Perbarui, progres,
  changelog 3 baris). Baris TENTANG kini membaca versi dari `PackageManager`, bukan hardcode.
- `OtoApp`: `hiltViewModel<UpdateViewModel>()`, cek otomatis saat app dibuka (maks 6 jam sekali),
  dan **badge** di tab *Atur* bila ada versi baru.

## Hasil uji di emulator Android 8.0 (API 26)

| Langkah | Hasil |
|---|---|
| Pasang v1.2 (versionCode 3), buka Pengaturan | "Versi 1.2 · Belum diperiksa" |
| Tekan *Periksa* terhadap JSON rilis tiruan v1.3 | "Versi 1.3 tersedia" + changelog muncul |
| Tekan *Perbarui* | progres "Mengunduh 19% … 75% …" berjalan |
| Verifikasi | SHA-256 cocok, paket & sertifikat cocok, versionCode 4 > 3 |
| Dialog sistem | "Do you want to install an update to this existing application? Your existing data will not be lost." |
| Setelah INSTALL | `dumpsys package` → **versionCode=4, versionName=1.3** |
| Buka lagi Pengaturan | "Versi 1.3 · Terbaru" |

Bukti layar: `preview/emulator/v12-01-progres-unduh.png`, `v12-02-dialog-instal.png`,
`v12-03-setelah-update.png`.

## Bug yang tertangkap justru karena diuji di Android 8

`GET_SIGNING_CERTIFICATES` baru ada di API 28. Di Android 8 flag itu membuat
`PackageInfo.signatures` bernilai null, sehingga APK yang sah dilaporkan
"APK tidak bertanda tangan" dan pembaruan selalu ditolak. Perbaikannya: pilih
`GET_SIGNING_CERTIFICATES` (≥ 28) atau `GET_SIGNATURES` (< 28) — lihat `pmFlags`
di `UpdateRepository.kt`. Tanpa uji di perangkat lama, bug ini akan lolos ke rilis.

## Catatan keamanan pada build

- `allowInsecureHttp` hanya `true` bila `BuildConfig.DEBUG`; build rilis **hanya** menerima
  `https://`. Ini dipakai untuk menguji lewat server lokal (`10.0.2.2`) tanpa menerbitkan rilis.
- Progres unduhan memakai `contentLength`; bila server tidak mengirimnya, dipakai `size` dari API.
- APK pembaruan disimpan di `cacheDir/oto-update.apk` dan dihapus bila verifikasi gagal.
