# 08 — Rilis & update aplikasi tanpa install ulang

Jawaban singkat untuk poin 7: **bisa**. Syaratnya satu, dan itu sudah disiapkan di v1.1:
APK harus ditandatangani dengan **keystore yang sama setiap rilis**. Kalau tanda tangan berubah,
Android menolak update dan kamu terpaksa uninstall (data favorit/playlist hilang).

## 1. Yang sudah disiapkan

| Berkas | Isi |
|---|---|
| `otoradio/keystore/otoradio-release.jks` | keystore rilis (RSA 4096, valid 10.000 hari) |
| `otoradio/keystore.properties` | `storeFile`, `storePassword`, `keyAlias`, `keyPassword` |
| `app/build.gradle.kts` | `release { signingConfig = ... }` dibaca dari file di atas |
| sertifikat | `CN=OtoRadio`, SHA-256 `425814de8dad67bce641404c26d388c8cec5f59082805afb8327ba6914f82bd8` |

> **PENTING:** simpan `otoradio-release.jks` + password-nya. Tanpa file itu kamu tidak akan bisa
> merilis update yang bisa menimpa instalasi lama. Salinannya sudah saya unggah ke Drive
> (`APK/SIGNING-jangan-dibagikan/`). Jangan bagikan ke orang lain.

Keduanya **tidak masuk git** (`.gitignore`: `keystore/`, `keystore.properties`).

## 2. Alur membuat versi revisi (contoh 1.2)

```bash
cd otoradio
# 1. naikkan versi — WAJIB, Android menolak APK dengan versionCode sama
#    app/build.gradle.kts:  versionCode = 3    versionName = "1.2"

# 2. build
./gradlew :app:assembleRelease

# 3. hasil: app/build/outputs/apk/release/app-release.apk
```

### Pasang di HP
- **Dengan kabel** — menimpa versi lama, data tetap:
  ```bash
  adb install -r app-release.apk
  ```
- **Tanpa kabel** — kirim APK ke HP (Drive/WhatsApp), ketuk, pilih **Perbarui/Update**.
  Jangan pilih "Uninstall" — itu menghapus favorit & playlist.

### Menurunkan versi (downgrade)
Android menolak APK dengan `versionCode` lebih kecil. Untuk uji coba:
`adb install -r -d app-release.apk` (hanya lewat adb, data tetap).

## 3. Riwayat versi rilis

| Versi | versionCode | Berkas | Catatan |
|---|---|---|---|
| 1.0 | 1 | `OtoRadio-1.0-debug.apk` (arsip) | debug-signed, 34 MB |
| **1.1** | **2** | **`OtoRadio-1.1-release.apk`** | artwork 1:1, audio raw default, playlist, nav kontekstual, minify 5,7 MB, release-signed |

Karena 1.0 yang terpasang di HP-mu bertanda tangan **debug**, sedangkan 1.1 bertanda tangan
**keystore rilis**, pemasangan 1.1 **sekali ini** harus menimpa paksa:

```bash
adb uninstall com.otoradio.app     # data lama hilang (favorit/playlist)
adb install OtoRadio-1.1-release.apk
```
atau di HP: uninstall aplikasi lama → pasang 1.1. Setelah itu **semua update berikutnya cukup
`adb install -r`** tanpa kehilangan data.

## 4. Checklist sebelum mengirim update ke HP

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --no-daemon
unzip -l app/build/outputs/apk/release/app-release.apk | grep -c assets/logos/   # harus 683
/opt/tc/sdk/build-tools/35.0.0/apksigner verify --print-certs app-release.apk    # cek CN=OtoRadio
```
Lalu catat sha256 di `dist/SHA256.txt` dan salin ke folder Drive.

## 5. Kalau nanti mau rilis ke Play Store
Bukan tujuan proyek ini (sideload), tapi bila berubah pikiran: Play Store menuntut
**Play App Signing** (kunci dipegang Google) dan APK/AAB tidak boleh berisi katalog stasiun
pihak ketiga tanpa izin. Untuk pemakaian pribadi, sideload seperti sekarang sudah paling praktis.
