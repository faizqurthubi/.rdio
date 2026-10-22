# 11 — Push ke GitHub: mekanisme & pilihan kredensial

Repo target: **`https://github.com/faizqurthubi/.rdio`** (publik, kosong — 0 byte, tanpa branch,
dibuat 22 Okt 2026 08:13 UTC).

## Fakta penting dulu

Sandbox saya punya **akses internet keluar**, tapi **tidak punya kredensial GitHub apa pun**.
Koneksi ke `github.com:22` dan `ssh.github.com:443` sudah diuji: terbuka, jawabannya
`Permission denied (publickey)` — artinya siap push begitu ada kunci yang diizinkan.
Jadi yang dibutuhkan bukan "cara", melainkan **satu otorisasi dari kamu**.

## Dua jalur otorisasi

| Jalur | Yang kamu lakukan | Risiko | Catatan |
|---|---|---|---|
| **Deploy key (SSH)** — disarankan | Tempel 1 baris public key di Settings → Deploy keys, centang *Allow write access* | Kunci hanya berlaku untuk repo ini, bisa dicabut kapan saja, tidak pernah dikirim lewat chat | Private key tetap di sandbox, tidak pernah keluar |
| Personal Access Token (HTTPS) | Buat fine-grained PAT (Contents: read/write, repo ini saja, kedaluwarsa 7 hari) lalu kirim ke chat | Token masuk riwayat percakapan; harus dicabut setelah selesai | Lebih cepat, sedikit lebih berisiko |

Public key yang sudah saya buat ada di `~/.ssh/otoradio_deploy.pub`. Kunci privatnya
(`~/.ssh/otoradio_deploy`, mode 600) tidak pernah ditampilkan di chat.

## Apa yang akan di-push

Bukan repo kerja `/home/user` (isinya 66 MB riwayat + APK 1.0 berukuran 33 MB yang terlanjur
ter-commit — tidak layak masuk GitHub). Yang di-push adalah tree bersih di `/home/user/publish`,
**satu commit awal**:

```
README.md  CHANGELOG.md  .gitignore  .gitattributes
app/ gradle/ gradlew build.gradle.kts settings.gradle.kts gradle.properties
docs/01…10  data/  preview/emulator/v11-*.png  scripts/
.github/workflows/release.yml
```

Sudah diverifikasi: **tidak ada** `keystore/`, `keystore.properties`, `*.jks`, `local.properties`,
dan pencarian string password keystore menghasilkan nol kecocokan.

Alasan satu commit, bukan riwayat penuh: riwayat lama memuat 3 salinan APK 1.0 (33 MB masing-masing)
dan 2,2 MB screenshot referensi. Itu akan membuat repo langsung gemuk dan tidak bisa dikecilkan
tanpa menulis ulang sejarah. Riwayat asli tetap utuh di workspace.

## Setelah push: menyiapkan rilis pertama

1. **Secrets** (Settings → Secrets and variables → Actions):
   - `KEYSTORE_BASE64` = `base64 -w0 keystore/otoradio-release.jks`
   - `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_PASSWORD`, `KEYSTORE_KEY_ALIAS=otoradio`
2. **Immutable releases**: Settings → General → Releases → *Enable release immutability*.
   Aktifkan **sebelum** rilis pertama supaya rantai rilis langsung terlindungi.
3. `git tag v1.1 && git push origin v1.1` → Actions membangun APK bertanda tangan, memverifikasi
   dengan `apksigner`, lalu membuat **draft Release** berisi `OtoRadio-1.1-release.apk` +
   `SHA256SUMS`. Draft harus di-publish manual — konsekuensi dari immutable releases: asset
   tidak bisa ditambah setelah publish, jadi draft → lengkapi → publish.

## Alur rilis berikutnya (v1.2, dst.)

```bash
# 1. naikkan versi di app/build.gradle.kts: versionCode 3, versionName "1.2"
# 2. commit
git commit -am "v1.2: <ringkasan>"
# 3. tag & push -> CI yang membangun
git tag v1.2 && git push origin main v1.2
# 4. publish draft Release, tulis changelog di badan rilis
```

Setelah itu di HP: buka rilis terbaru → unduh → ketuk → **Perbarui** (bukan instal baru).
Favorit & playlist tetap ada karena keystore sama.

## Catatan nama repo

Nama `.rdio` diawali titik, sehingga di daftar repo GitHub ia tampil seperti file tersembunyi dan
URL-nya jadi `github.com/faizqurthubi/.rdio`. Semua fungsi (Actions, Releases, raw, clone) tetap
normal — sudah diverifikasi via API (`private: false`, `default_branch: main`). Kalau mau lebih
enak dibaca dan dibagikan, ganti nama jadi `otoradio` di Settings → Repository name;
GitHub otomatis mengalihkan URL lama. Ini opsional dan tidak memengaruhi apa pun.

## Hasil: rilis pertama lewat CI (22 Okt 2026)

Tag `v1.1` di-push → workflow **"Rilis APK"** jalan ±12 menit dan **sukses seluruhnya**:

```
Cek aset katalog ............... success
Pulihkan keystore dari secret .. success
Cocokkan tag dengan versionName  success
Build rilis .................... success
Verifikasi APK (apksigner) ..... success
Terbitkan Release .............. success
```

Release dibuat sebagai **draft** (sesuai konfigurasi), sehingga belum terlihat publik sampai
tombol *Publish release* ditekan. Draft hanya bisa dilihat pemilik repo — `GET /releases`
tanpa autentikasi tidak akan menampilkannya.

### Catatan soal "Enable release immutability"

Setelan ini **tersedia untuk repo milik akun pribadi**, tetapi **tidak ada di aplikasi GitHub
mobile**. Harus lewat browser:

`github.com/faizqurthubi/.rdio` → **Settings** → **General** → gulir ke bagian **Releases** →
**Enable release immutability**

Kalau memakai browser HP, aktifkan dulu "Request desktop site" karena tampilan mobile
menyembunyikan bagian itu. Alternatif lewat API (butuh PAT dengan akses Administration):

```bash
curl -X PUT -H "Authorization: Bearer <PAT>" \
  https://api.github.com/repos/faizqurthubi/.rdio/immutable-releases
# cek: GET endpoint yang sama -> {"enabled":true,"enforced_by_owner":false}
```

Urutan yang benar: **aktifkan dulu, baru publish draft**. Immutability hanya berlaku untuk rilis
yang dipublikasikan setelah setelan menyala. Kalau draft terlanjur dipublikasikan dan API
melaporkan `"immutable": false`, itu tidak bisa diperbaiki untuk rilis yang sama — biarkan saja
(rilis tetap sah, digest SHA-256 tetap terbit otomatis oleh GitHub), lalu pastikan rilis v1.2
dibuat setelah setelan menyala.
