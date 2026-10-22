# 09 — Cara menyimpan & mendistribusikan APK yang benar

## Jawaban singkat

**Ya, byte APK tetap harus sampai ke HP** — Android tidak punya cara lain memasang aplikasi
sideload. Yang bisa dihilangkan bukan *download*-nya, melainkan **kerja manual**-nya: cari file,
unduh, buka, ketuk instal, tiap kali ada revisi.

Kesalahan umum: menyimpan APK sebagai **file di cloud drive**. Drive dirancang untuk dokumen,
bukan kanal rilis. Akibatnya: tidak ada nomor versi, tidak ada riwayat yang bisa dibandingkan,
link unduhan berubah/berhenti berfungsi, dan tidak ada mekanisme "versi baru tersedia".

**Tempat yang tepat untuk APK = release channel** (GitHub Releases / repo F-Droid), dengan file
`SHA256SUMS` + changelog, dan **klien updater** di HP yang menariknya otomatis.

## Perbandingan opsi

| Cara | Otomatis di HP | Riwayat versi | Checksum | Kerja tambahan | Cocok untuk |
|---|---|---|---|---|---|
| Google Drive (sekarang) | ✗ manual unduh + ketuk | ✗ (harus disiplin rename) | manual | kecil | arsip/cadangan, kirim ke orang |
| **GitHub Releases + Obtainium** | **✓ cek & instal sendiri** | ✓ tag per versi | ✓ | kecil (sekali setup) | **rekomendasi** |
| GitHub Releases + updater di dalam app | ✓ (dalam app) | ✓ | ✓ | sedang (tulis kode) | kalau ingin tanpa app pihak ketiga |
| Repo F-Droid sendiri (`fdroidserver`) | ✓ (klien F-Droid/Droid-ify) | ✓ metadata | ✓ | besar (server/CI) | kalau mau terasa seperti toko app |
| Telegram/WhatsApp sendiri | ✗ | ✗ | ✗ | nol | sekali pakai, tidak untuk revisi |
| Git LFS / taruh APK di repo git | ✗ | ✓ | ✓ | — | **hindari**: repo jadi berat, binary tidak cocok di git |

Catatan ukuran: satu APK 5,4 MB. 20 versi = ±108 MB — masih wajar di Releases maupun Drive.

## Rekomendasi: GitHub Releases + Obtainium

Kenapa: gratis, permanen, tiap versi punya URL tetap & immutable, bisa menaruh changelog,
dan Obtainium (open-source, gratis) bisa **memeriksa rilis baru lalu mengunduh + memasang sendiri**.

### Sekali setup (±10 menit)

1. Buat repo di GitHub, mis. `otoradio` (boleh **privat**; Obtainium mendukung repo privat
   dengan Personal Access Token. Kalau ingin paling gampang, buat **publik** — isinya hanya
   APK, changelog, dan README, tanpa kunci signing).
2. Simpan sumber proyek (`otoradio/`) di repo itu. **Jangan** commit `keystore/` dan
   `keystore.properties` (sudah masuk `.gitignore`).
3. Rilis pertama:
   ```bash
   bash scripts/prepare-release.sh        # build + checksum + version.json + draf changelog
   gh release create v1.1 \
       dist/OtoRadio-1.1-release.apk \
       dist/SHA256.txt dist/version.json \
       --title "OtoRadio 1.1" --notes-file dist/CHANGELOG-v1.1.md
   ```
   (atau tanpa `gh`: buka GitHub → Releases → Draft new release → tag `v1.1` → unggah APK.)
4. Di HP: pasang **Obtainium** (github.com/ImranR98/Obtainium, ambil APK-nya dari Releases
   mereka), lalu **Add App** → tempel URL repo GitHub-mu. Beri izin "Install unknown apps".
5. Selesai. Rilis berikutnya cukup `git tag` + `gh release create`; HP memberi notifikasi
   "versi baru", kamu ketuk *Update*, dan karena tanda tangannya sama, **data favorit/playlist
   tidak hilang**.

Aturan yang membuat ini bekerja:
- Nama file APK harus berisi versi (`OtoRadio-1.2-release.apk`).
- `versionCode` **wajib naik** tiap rilis (Android menolak yang sama).
- Tanda tangan **wajib sama** (keystore yang sudah kita buat).
- Satu tag = satu rilis; jangan menimpa (overwrite) APK pada tag lama.

## Alternatif: repo F-Droid sendiri

Kalau mau benar-benar seperti toko aplikasi (metadata, ikon, deskripsi, update otomatis):

```bash
pip install fdroidserver
fdroid init                      # buat config.yml, isi keystore repo sendiri
# taruh sumber di metadata/com.otoradio.app.yml  (Builds + BuildsVersion)
fdroid update --create-metadata
fdroid deploy                    # unggah ke static host (GitHub Pages/VPS)
```
Lalu di klien F-Droid/Droid-ify: Settings → Repositories → tambahkan URL repo.
Lebih "rapi", tapi butuh server/CI untuk membangun tiap rilis — overkill untuk pemakaian pribadi.

## Kalau ingin updater di dalam aplikasi

Detail lengkapnya (endpoint, field, rangkaian keamanan, izin, batasan): **`docs/10-updater-dalam-app.md`**.


Bisa saya tambahkan ke OtoRadio (tanpa app pihak ketiga): cek `version.json` di URL tetap, dan
kalau `versionCode` lebih tinggi, tampilkan kartu "Versi 1.2 tersedia" → unduh → pasang lewat
`PackageInstaller` (butuh izin `REQUEST_INSTALL_PACKAGES`). Skema `dist/version.json` sudah
saya siapkan supaya tinggal dibaca. Bilang saja kalau mau, saya kerjakan di rilis berikutnya.

## Di mana menyimpan apa

| Aset | Tempat yang tepat | Alasan |
|---|---|---|
| APK tiap versi | GitHub Releases (+ arsip di Drive) | immutable, ada tag & changelog |
| Kode sumber | repo git (GitHub/privat) | riwayat & bisa di-diff |
| `SHA256SUMS`, `version.json`, `CHANGELOG` | ikut di rilis | verifikasi unduhan |
| **Keystore + password** | **password manager** (Bitwarden/1Password) + salinan offline (flashdisk), **bukan** Drive publik | kalau bocor, orang bisa membuat "update" palsu bertanda tangan sah |
| Arsip jangka panjang | 3-2-1: GitHub + Drive + lokal | satu kanal bisa hilang |

**Catatan soal keystore:** saya sebelumnya menaruhnya di folder Drive `APK/SIGNING-jangan-dibagikan/`
supaya tidak hilang. Itu **cukup untuk sekarang**, tapi jangka panjang pindahkan ke password
manager dan hapus dari Drive — kunci signing adalah aset paling sensitif di proyek ini, bukan APK-nya.
