# 13 — Perubahan 1.4

Revisi atas masukan pemakaian v1.3. Karena toolchain lokal sudah dibersihkan (dan kamu minta
tanpa instal SDK), **kompilasi diverifikasi oleh CI GitHub Actions**, bukan di mesin ini —
lihat bagian terakhir: CI memang menangkap tiga error dan semuanya sudah diperbaiki.

## 1. Spektrum mengikuti suara sungguhan

Sebelumnya spektrum hanya animasi sinus. Sekarang datanya **diambil dari audio yang benar-benar
diputar**:

```
ExoPlayer → DefaultRenderersFactory.buildAudioSink()
          → DefaultAudioSink dengan DefaultAudioProcessorChain(SpectrumAudioProcessor)
          → PCM dianalisis (FFT 1024, 28 pita 60 Hz–14 kHz) → SpectrumBus
          → UI membaca StateFlow → tinggi batang
```

- `audio/SpectrumAudioProcessor.kt` — turunan `BaseAudioProcessor`. `queueInput()` menyalin
  buffer read-only untuk dianalisis, lalu meneruskan audio apa adanya ke output buffer
  (`replaceOutputBuffer`), jadi suara tidak berubah sama sekali.
- FFT radix-2 in-place + jendela Hann + skala dB (lantai −70 dB) + attack cepat/decay lambat.
- Pembaruan UI dibatasi ±25 kali/detik supaya hemat baterai dan tidak membuat UI tersendat.

**Kenapa bukan `android.media.audiofx.Visualizer`:** kelas itu menuntut izin `RECORD_AUDIO`
yang akan muncul sebagai "merekam audio" di dialog izin. Penyadapan internal ini tidak butuh
izin apa pun, tetap akurat, dan tidak menambah dependensi baru.

Bila belum ada sampel audio (mis. baru menyambung), UI menampilkan riak tenang sebagai penanda
"hidup", bukan batang palsu yang mengaku spektrum.

## 2. Spektrum selebar layar

`Row` dengan `Modifier.weight(1f)` per batang + `fillMaxWidth()`, jadi 28 batang terbagi rata
dari tepi kiri ke tepi kanan (sebelumnya lebar batang tetap sehingga hanya mengisi separuh).

## 3. Status siaran tiga keadaan

| Keadaan | Tampilan |
|---|---|
| Menyambung | spinner kecil **berputar** + teks `MENYAMBUNG` |
| Berbunyi | **titik merah berpendar** + teks `LIVE` |
| Berhenti/galat | **titik abu-abu** + teks `OFFLINE` |

## 4. Ikon: isi hitam diperkecil

*Safe zone* logo diubah dari 66/108 → **54/108** dp pada `ic_launcher_foreground.xml`, dan raster
`mipmap-*` (48–192 px) digambar ulang. Isi hitam sekarang punya margin lega di dalam latar kuning,
tidak lagi menyentuh tepi saat dipotong masker bulat/squircle.

## 5. Mini player sadar antrean sejak awal

`MainViewModel.play(station, context = null)` kini memakai **seluruh katalog** sebagai antrean
bila tidak ada konteks eksplisit. Sebelumnya antrean = satu stasiun, sehingga tombol next/prev
baru muncul setelah kamu menekan next. Sekarang, memutar satu stasiun dari daftar mana pun
langsung memberi antrean > 1 dan tombol prev/play/next tampil sejak detik pertama.

## 6. Artwork masih pudar — akar masalahnya

Audit ulang menemukan sebab sebenarnya: **sebagian besar logo katalog adalah garis hitam dengan
latar transparan**. Di v1.3 logonya hanya diberi latar netral gelap, jadi garis hitam di atas
permukaan gelap = hampir tak terlihat. Itu bukan masalah opasitas, tapi kontras.

Perbaikan:
- `StationArtwork` mendapat parameter `plateColor`: piring **putih** di belakang logo, dengan
  padding 14% agar tidak menempel tepi.
- Cincin `outline` 35% dihapus (lapisan itulah yang membuat kesan kusam).
- `LogoStore` membatasi decode ke sisi ≥ 512 px (`inSampleSize` pangkat dua). Sebelumnya logo
  besar diperkecil puluhan kali setiap kali digambar, yang mengaburkan garis.

## 7. Tombol back: konfirmasi

`BackHandler` tetap berlapis (tutup sheet → Now Playing → detail playlist → tab lain), tetapi di
akar aplikasi kini muncul dialog:

> **Keluar dari Oto Radio?** — "Siaran akan berhenti." → **Keluar** / **Batal**

## Verifikasi (lewat CI, bukan lokal)

`.github/workflows/build.yml` baru: setiap push ke `main` menjalankan
`assembleDebug` + 18 unit test dan mengunggah APK debug sebagai artifact (tanpa menerbitkan rilis).
Error kompilasi dilaporkan sebagai **anotasi** supaya bisa dibaca dari API publik.

Run pertama menangkap tiga error nyata — semuanya sudah diperbaiki:

| Error | Sebab | Perbaikan |
|---|---|---|
| `queueInput` return type tidak cocok | Di media3 1.4.x `AudioProcessor.queueInput` mengembalikan `Unit`, dan `BaseAudioProcessor` tidak menyediakannya | Implementasi penuh: analisis → `replaceOutputBuffer()` → `flip()` |
| `Argument type mismatch: Array<AudioProcessor>` | `DefaultAudioProcessorChain` menerima **vararg** | Kirim objeknya langsung, bukan `arrayOf(...)` |
| `Unresolved reference 'context'` | Blok dialog ditempatkan sebelum `val context` | Pakai `LocalContext.current` di dalam blok |

Hasil akhir: **Build (uji kompilasi) — success**, lalu tag `v1.4` memicu workflow **Rilis APK**
yang membangun APK bertanda tangan dan menerbitkannya sebagai rilis immutable.
