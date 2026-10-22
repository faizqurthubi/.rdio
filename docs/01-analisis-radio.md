# Tahap 1 — Analisis APK Radio (Radio Indonesia FM)

Target: `base.apk` (29 164 238 byte, SHA-256 di bawah), diunduh dari Google Drive pada 2026-10-22.
Alat: `apktool 2.11.1` (resource + manifest), `jadx 1.5.1` (decompile Java), `grep` pada smali, `curl` untuk verifikasi endpoint hidup.
Metode mengikuti instruksi skill REA (`docs/android-analysis.md`, `docs/apktool-resource-analysis.md`): JADX untuk source, Apktool untuk resource/manifest, dan setiap klaim disertai bukti (nama kelas/berkas/baris).

```
sha256: (lihat docs/01-lampiran-sha256.txt)
```

Legenda: **[FAKTA]** = terbaca langsung dari APK/respons server. **[ASUMSI]** = interpretasi/turunan saya.

---

## a. Metadata

| Item | Nilai |
|---|---|
| Package | `com.radiofmapp.radioindonesia` |
| Nama tampilan | `Radio Indonesia FM` (`res/values/strings.xml` → `app_name`) |
| versionName / versionCode | `4.9.508` / `508` (apktool.yml `versionInfo`) |
| minSdk / targetSdk / compileSdk | 29 / 37 / 37 (apktool.yml `sdkInfo`, manifest `android:compileSdkVersion`) |
| Application class | `com.radiofmapp.core.App` (`android:name`) |
| Activity | `SplashActivity` (LAUNCHER, exported), `MainActivity` (`singleTask`, portrait, `configChanges=uiMode`), `SubscriptionActivity` |
| Service | `com.radiofmapp.core.player.PlayerService` — `foregroundServiceType="mediaPlayback"` |
| Receiver | Tidak ada receiver aplikasi sendiri di manifest; `ACTION_AUDIO_BECOMING_NOISY` & `SCREEN_OFF` didaftarkan runtime |
| Permission (app) | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `BLUETOOTH`, `READ_PHONE_STATE` (maxSdk 30) |
| Permission (pihak ketiga) | `AD_ID`, `ACCESS_ADSERVICES_*`, `com.android.vending.BILLING`, `BIND_GET_INSTALL_REFERRER_SERVICE` |
| `usesCleartextTraffic` | **true** — penting: 130 dari 741 stream memakai `http://` (lihat bagian c) |
| Metadata iklan/analitik | AdMob (`ca-app-pub-1467312147757958~7680703105`), Facebook Audience Network, **TikTok/Pangle 8.2.0.4** (`com.bytedance.sdk.pangle.version`), Firebase (Analytics, Crashlytics, Remote Config), Google Play Billing 9.1.0 |

**[FAKTA]** Aplikasi ini adalah white-label dari template radio berbahasa Spanyol ("RadioFmApp", semua log berbahasa Spanyol, field model `nombre/ciudad/frecuencia/emisoras`), di-rebranding ke Indonesia lewat `res/values/strings.xml` (`pais_name=Indonesia`, `api_pais=id`).

**[FAKTA]** Remote Config default (`res/xml/remote_config_defaults.xml`): `subscriptions_enabled=false`, `first_screen=0`, `interstitial_after_click=false`, `app_update_enabled`, `showExitBanner=false`, `app_open_enabled=false`, `cmp_enabled=true`.

---

## b. Arsitektur & library

**[FAKTA]** Tidak ada library native (0 entri `lib/`), murni Java/Kotlin + 5 `classes*.dex`.

| Lapisan | Implementasi nyata di APK |
|---|---|
| Pemutar | **Media3 ExoPlayer** (`androidx.media3.exoplayer.ExoPlayer`, `DefaultMediaSourceFactory`, `DefaultLoadControl`, `DefaultTrackSelector`). Bukan Media3 `MediaSessionService` |
| Session/notifikasi | **`androidx.media` (MediaSessionCompat) + `MediaBrowserServiceCompat`** — `PlayerService` mengimplementasikan `onGetRoot`/`onLoadChildren`; notifikasi dibuat manual oleh `com.radiofmapp.core.notification.PlayerNotificationManager` (bukan `MediaSessionService`/`PlayerNotificationManager` bawaan Media3) |
| Data source | `androidx.media3.datasource.okhttp.OkHttpDataSource$Factory` + OkHttp/DNS-over-HTTPS `https://dns.google/dns-query` dan fallback resolver `8.8.8.8`,`8.8.4.4`; custom `SSLContext("TLS")`; User-Agent browser Chrome 120/97 (menyamar sebagai browser). Host khusus `egostreaming.pe`, `mediacp.eu` diperlakukan berbeda |
| Fallback HTTP | `DefaultHttpDataSource$Factory` dengan `setAllowCrossProtocolRedirects(true)`, connect 15 000 ms, read 30 000 ms |
| Jaringan | OkHttp + Gson (`com.google.gson.annotations.SerializedName` di `Station`) |
| Gambar | Glide (`com.bumptech.glide`) |
| Database | Room 2.7.0 (`RoomDatabase`, `MultiInstanceInvalidationService`); tabel `emisora` + tabel `emi_favorita` + VIEW `topemisoras` (DDL di smali kelas DAO) |
| Preferensi | DataStore 1.1.7 + `SharedPreferences` (`AutoPlayEnabled`, `LastStationId`, `launch_count`, `date_firstlaunch`) |
| Lain | WorkManager 2.11.2, Firebase Remote Config/Analytics/Crashlytics, Play Billing 9.1.0, AdMob, FAN, Pangle |
| Pola arsitektur | Activity/Fragment klasik (ViewBinding + `findViewById`), `ViewModel` + `LiveData` (`PlayerViewModel`, `StationsFragment`), DAO Room, callback HTTP custom (`HttpRequestOK` = kelas `g20`/`h20`/`f20`). **Bukan** Compose, **bukan** Hilt/Dagger (0 referensi Hilt) |

**[FAKTA]** Skema Room (DDL persis, dari string di smali):
```sql
CREATE TABLE emisora ( id INTEGER, nombre TEXT, url TEXT, imagen TEXT, geoblocked INTEGER,
                       url2 TEXT, url3 TEXT, frecuencia TEXT, ciudad TEXT, www TEXT)
-- tabel favorit: emi_favorita(idEmiFavorita, favorita)
CREATE VIEW topemisoras AS select emi_favorita.idEmiFavorita AS idEmiFavorita, emisora.nombre AS nombre,
  emisora.url AS url, emisora.imagen AS imagen, emi_favorita.favorita AS favorita, emisora.geoblocked,
  emisora.url2, emisora.url3, emisora.frecuencia, emisora.ciudad, emisora.www
  from emi_favorita join emisora where (emi_favorita.idEmiFavorita = emisora.id)
```

**[FAKTA]** Model data `com.radiofmapp.core.stations.Station` (Parcelable, Gson): `id:int, name, url, url2, url3, imagen, category, frecuencia, ciudad, www, geoblocked:int, favorite:boolean`. `StationFav`: `idEmiFavorita, favorita`.

---

## c. Sumber stasiun (endpoint, format, katalog)

**[FAKTA]** Basis API ada di resource string `api_domain = https://api.radiofmapp.com`, `api_version = v2`, `api_pais = id`.
URL dirakit di `PlayerService`/`ic0`/`g20` (`g20.smali` baris ~880-930):

```
https://api.radiofmapp.com/{api_version}/pais/{api_pais}/emisoras
https://api.radiofmapp.com/{api_version}/pais/{api_pais}/last_update
logo = https://api.radiofmapp.com/images/stations/{api_pais}/{imagen}
```

**[FAKTA]** Autentikasi: header `Authorization` diisi di `h20.smali` (`const-string "Authorization"`) dari `ig1.a(Charset)` yang berisi kredensial hard-coded `androides:aceroparalosbarcos` → `Basic base64(...)`. Verb `GET`. Tanpa header → nginx 401; dengan header → 200.

**[FAKTA]** Respons `/v2/pais/id/emisoras` (diverifikasi 2026-10-22, 215 874 byte):
```json
{"data":[{"id":18787,"nombre":"103.4 Radio DFM","url":"https://i.klikhost.com/9000/stream",
"imagen":"radio1034dfm.png","url2":null,"geoblocked":1,"url3":null,"frecuencia":"103.4 FM",
"ciudad":"Jakarta","www":"https://www.1034dfm.com/","enabled":1}],
 "last_update":"2026-10-20 07:26:28","code":200}
```
- 741 stasiun, seluruhnya `enabled=1`, 39 `geoblocked=1`, 264 punya `url2`, 8 punya `url3`.
- `/last_update` mengembalikan `{"last_update":"2026-10-20 07:26:28","code":200}` → dipakai untuk cache invalidation DB lokal.
- **Logo 404**: `images/stations/id/<imagen>` mengembalikan HTTP 404 (dicek juga tanpa auth, `v2/images/...`, `radiofmapp.com/...`). **[ASUMSI]** Server sudah memindahkan/menghapus aset gambar; aplikasi aslinya pun kini menampilkan placeholder. Karena itu `logo_url` di katalog kita `null` dan aplikasi baru memakai tile gradien + inisial (fallback), bukan logo asli.
- API tidak punya field genre/kategori (field `category` ada di model tapi tidak dikirim server).

**[FAKTA]** Format stream (sampel acak 30 URL, header dicek via curl `Icy-MetaData: 1`):
| Aspek | Hasil |
|---|---|
| Skema seluruh katalog | 611 `https`, 130 `http` (cleartext) |
| Ekstensi/tipe | 236 `.mp3`, 5 `.aac`, 3 `.m3u8` (HLS, `application/vnd.apple.mpegurl`), 366 "icecast-ish" tanpa ekstensi, sisanya lain |
| Content-Type saat diputar | `audio/mpeg`, `audio/aac`, `audio/aacp`, `application/vnd.apple.mpegurl` |
| ICY metadata | ±53% sampel membalas header `icy-*` (name/metaint/genre/br) → judul lagu dari ICY bisa diandalkan, tapi harus tahan null |
| Kegagalan lazim | 401 (butuh referer/token), 302 → HTML, 503/502, koneksi time-out |

**Logika URL fallback (FAKTA, `nl0.smali` = `Player.Listener`):**
- Indeks URL `g` (0..2), maksimum `h` = 2 bila `url2` & `url3` ada, 1 bila hanya `url2`.
- Saat `onPlayerError`: bila `g <= h` → `g++`, player lama di-null-kan & di-release, `PlayerService.d(station)` dipanggil ulang sehingga ExoPlayer dibangun ulang dengan URL berikutnya. Tidak ada exponential backoff, tidak ada penjadwalan ulang otomatis setelah URL terakhir habis.
- `.m3u` diperlakukan khusus: diunduh dulu via OkHttp (UA `Mozilla/5.0`) lalu di-parse; `.mpd` → MPD; mengandung `m3u8` → HLS; selain itu `BASE_TYPE_AUDIO` (progressive).
- Bila `geoblocked=1` → dialog/toast "geoblocked" (`geoblocked_toast`, `geoblocked_desc`).
- Buffer ExoPlayer: `setBufferDurationsMs(25_000, 50_000, 1_000, 2_000)`.

**Keluaran Tahap 1c:** `/data/stations.json` — 741 stasiun bersih dengan field `id, name, stream_url, stream_url_alt[], logo_file, logo_url(null), frequency, city, country, country_code, website, geoblocked, enabled, genre(null), source`. Raw respons disimpan di `/data/stations-raw.json`.

---

## d. Fitur

| Fitur | Status | Bukti |
|---|---|---|
| Background playback | Ya | `PlayerService` foreground (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`), `startForeground` via `PlayerNotificationManager` |
| Wake lock | Ya, agresif | `PowerManager.WakeLock` PARTIAL_WAKE_LOCK `"PlayerService:service-wakelock"` `acquire(600000L)` (10 menit, tanpa renew eksplisit) + `WifiManager.WifiLock` `createWifiLock(3,"PlayerService")` |
| Notifikasi media | Ya, manual | `PlayerNotificationManager` (channel `media_playback_channel`, aksi play/pause/prev/next, `MediaStyle` + `MediaControllerCompat`) |
| MediaSession | Ya | `MediaSessionCompat`, `MediaBrowserServiceCompat` (`onGetRoot`/`onLoadChildren` → kompatibel Android Auto/Bluetooth) |
| Audio focus | Ya | `AudioManager.requestAudioFocus` (API26+: `AudioFocusRequest` gain=1/AUDIOFOCUS_GAIN, `AudioAttributes` CONTENT_TYPE_MUSIC/USAGE_MEDIA); saat loss → pause |
| Audio becoming noisy | Ya | receiver runtime `android.media.AUDIO_BECOMING_NOISY` → pause (lepas headset berhenti) |
| Sleep timer | Ya | dialog `res/layout/dialog_sleep.xml`, menu `sleep_id`, switch di drawer, `bundle.putInt("sleep-time", menit)`; log `"Sleep activado/desactivado"`. Disimpan dalam preferensi, bukan service |
| Favorit | Ya | Room `emi_favorita` + `StationsFavFragment`/`StationsFavAdapter`, toggle `ic_action_favorite` |
| Riwayat | **Tidak ada** | tidak ada tabel/kelas history di APK |
| Pencarian | Ya, lokal | `res/menu/menu_search.xml` (`SearchView`), filter di `StationsFragment.filter()` atas list Room (bukan server-side) |
| Top 20 | Ya | VIEW `topemisoras` + `Top20StationsFragment` |
| Autoplay saat buka | Ya | preferensi `AutoPlayEnabled`, switch `navigation_switch_auto_play` |
| Rating/share/feedback | Ya | dialog custom + `ACTION_SEND`/Play Store intent |
| Subscription/no-ads | Ya (opsional, default off) | Play Billing 9.1.0 + `SubscriptionActivity` |

---

## e. Equalizer / audio effects  →  **TIDAK ADA**

**[FAKTA]** Tidak ada satu pun referensi `android.media.audiofx.*` di seluruh APK (0 hasil untuk `Equalizer`, `BassBoost`, `Virtualizer`, `LoudnessEnhancer`, `PresetReverb`, `AudioEffect`), baik di `smali*` maupun di `smali_assets`. Tidak ada kelas EQ/DSP, tidak ada layout EQ, tidak ada preset tersimpan. Aplikasi radio ini murni memutar stream tanpa pengolahan audio.

Konsekuensinya, **tidak ada nilai band/preset yang bisa "disalin apa adanya"** dari APK ini. `/data/eq-presets.json` karena itu disusun dari referensi Oto Music (Tahap 2) + preset standar 5-band yang umum dipakai aplikasi musik Android; provenance tiap preset dicatat di dalam file (`source` per preset). Ini **ASUMSI/DERIVASI**, bukan hasil ekstraksi.

**[ASUMSI]** Untuk aplikasi baru, desain EQ yang saya pakai: 5 band tetap (60, 230, 910, 3600, 14000 Hz, rentang −15…+15 dB, langkah 0.1 dB) + BassBoost (0–1000 millibel) + Virtualizer (0–1000) + LoudnessEnhancer (0–2000 mB), semuanya diikat ke `audioSessionId` ExoPlayer dan dibungkus fallback bila perangkat tidak mendukung.

---

## f. UI

**[FAKTA]** Tidak ada Compose; seluruh UI XML + Material Components 1.x/Material3 theme `Theme.Material3.Light.NoActionBar`.

**Layar & navigasi**
1. `SplashActivity` — ambil country code (`http://ip-api.com/json`), version check, load data, iklan; ada tombol "reintentar" bila gagal internet.
2. `MainActivity` (`res/layout/main_fragment.xml`):
   - `DrawerLayout` + `MaterialToolbar` (background `colorPrimary #1097FB`)
   - `FrameLayout id=frame_container` berisi fragment tab: **Stations** (`ic_emisoras`), **Favorites** (`ic_favorita`), **Top 20** (`ic_top20`)
   - **Bottom playback bar** (`layout_playback_controls`): artwork 50 dp + nama stasiun 15 sp bold + deskripsi 12.5 sp + tombol play/pause 32 dp (ini analog "mini player" pada aplikasi radio ini)
   - Banner AdMob di atas playback bar (`ad_view_container`)
   - `NavigationView` drawer: grup Fragments, Rate, Share, "Remove ads", switch Autoplay, switch Sleep, Settings
3. `SubscriptionActivity` — layar langganan (slide-up `ActivitySlideUpAnimation`).
4. Dialog: `dialog_sleep.xml`, `dialog_gdpr.xml`, `dialog_sendfeedback.xml`, `dialog_custom*.xml`, `custom_toast_warning.xml`.

**Daftar layout aplikasi (bukan library):** `main_fragment.xml`, `emisoras_tab.xml`, `emisorasfav_tab.xml`, `emi_rowlistview.xml`, `emi_rowlistview_fav.xml`, `empty_favorite.xml`, `custom_dialog.xml`, `dialog_sleep.xml`, `activity_subscription.xml`, `navigation_header.xml`, `navigation_switch_sleep.xml`, `navigation_switch_auto_play.xml`, `navigation_item_with_badge.xml`.

**Token visual (FAKTA, `res/values/colors.xml`, `styles.xml`, dimensi di layout):**
- `colorPrimary #1097FB`, `colorPrimaryVariant #0665AA`, `colorPrimary_alpha #901097FB`, `colorSecondary #00BCD4`, `colorTertiary #5BE9FB`, `colorBackground #FFFFFF`
- Toolbar & playback bar memakai `colorPrimary`; status bar `colorPrimaryVariant`
- Baris list: artwork 50×50 dp (margin 10 dp), judul 15 sp bold, deskripsi 13 sp, divider 1 dp `list_view_divider`, background `list_view_selector`
- Ikon: vektor Material (`ic_*`, `ab_*`), tint `colorPrimary_alpha` untuk ikon favorit

**Aset yang layak dipakai ulang:** tidak banyak — yang relevan hanya set ikon vektor generik (`ic_action_play/pause/previous/next`, `ic_action_favorite`, `ic_sleep`, `ic_emisoras`, `ic_favorita`, `ic_top20`, `ic_settings`, `ic_share`, `ic_search_black_24`). Logo stasiun **tidak** tersedia (server 404), jadi aplikasi baru memakai fallback inisial.

---

## c-bis. Addendum: pemulihan logo (diverifikasi 2026-10-22)

Logo dari server asal tetap 404. Logo dipulihkan lewat dua sumber lain (bukan dari APK):

| Sumber | Hasil uji | Catatan |
|---|---|---|
| Radio Browser `favicon` (countrycode=ID) | 611 stasiun, 480 punya favicon; **70** cocok token-nama dengan katalog kita | logo asli stasiun (PNG/JPG dari situs penyiar / mytuner) |
| Google favicon service dari field `website` | **547** berhasil (33/40 pada uji sampel awal) | ikon situs resmi, resolusi 128 px |
| **radioindonesia.org** (sitemap + JSON-LD `RadioStation`) | **275** cocok (169 domain `sameAs`, 96 nama persis, 9 nama longgar, 1 nama+kota) | logo asli penyiar dari mytuner ≤175 px |
| **Total terpasang** | **683 / 741 (92%)** | 58 sisa memakai tile gradien + inisial |

Pencocokan fuzzy berbasis `difflib` sengaja **tidak** dipakai untuk logo — uji awal menunjukkan
risiko salah pasang (mis. "Alternatif radio" vs "Alternatif radio jakarta"). Yang dipakai hanya
tiga tingkat aman: (1) domain `sameAs` identik, (2) nama ternormalisasi identik dan unik,
(3) nama tanpa keterangan kurung identik + kota konsisten ("iSwara Bandung (I-Radio)" ↔
"iSWARA Bandung"). Pencocokan lewat *domain root* (mis. semua `*.blogspot.com`) ditolak karena
mengelompokkan puluhan stasiun tak berhubungan.

Pencocokan domain juga dijaga fungsi `brand_ok()`: satu kata merek dari nama stasiun kita harus
muncul di nama/domain halaman sumber. Tanpa penjaga ini `alhastream.com` (hosting) membuat 6
stasiun Lampung memakai logo "Ganding City", dan `radiomuaranetwork.id` membuat "Radio Aditya -
Subang" memakai logo "Radio Paranti" — keduanya sudah dicabut. Logo jaringan yang memang sama
(RRI, Sonora, Megaswara, XChannel, Elshinta) tetap dipakai bersama.

Untuk 58 stasiun sisa, override manual:
taruh `assets/logos/<id>.webp`.

Sumber ketiga punya skrip sendiri, `tools/fetch_logos_radioindonesia.py`:

```bash
python3 tools/fetch_logos_radioindonesia.py            # pakai cache HTML di work/ri/pages
python3 tools/fetch_logos_radioindonesia.py --no-cache --workers 8
```
Halaman diambil dari `sitemap-radio-detail-1.xml.gz` (724 URL), logo dari
`https://static.mytuner.mobi/media/{tvos_radios|radios|radios-150px}/<file>` (hash pada nama
file wajib; hanya tersedia sampai ±175 px). Skrip ini **tidak** menimpa logo `manual` dan
melewati stasiun yang sudah punya logo asli (`radio-browser`/`radioindonesia.org`).

## Ringkasan yang dipakai di aplikasi baru

1. **Katalog**: `/data/stations.json` (741 stasiun hasil API, tanpa logo) — dipakai sebagai katalog offline bawaan.
2. **Stream**: harus dukung progressive MP3/AAC **dan** HLS (`.m3u8`), HTTP cleartext (130 URL), ICY metadata, UA browser, cross-protocol redirect, connect/read timeout 15 s/30 s, fallback multi-URL, buffer 25 s/50 s.
3. **EQ**: dibangun dari nol (tidak ada warisan dari APK radio).
4. **UI**: acuan visual sepenuhnya dari Oto Music (Tahap 2), bukan dari APK ini.

## Keterbatasan analisis
- Kelas di-obfuscate R8 (`a.smali`, `g20.smali`, dst.); pemetaan nama fungsional saya lakukan dari string log berbahasa Spanyol — akurat untuk perilaku, tapi nama kelas asli hilang.
- `PlayerService.b(int)` (factory data source) tidak bisa didecompile penuh oleh jadx; saya rekonstruksi dari smali.
- Tidak dilakukan analisis dinamis (tidak ada perangkat/emulator); semua perilaku runtime disimpulkan dari kode + uji endpoint dengan curl.
- Logo stasiun tidak bisa dipulihkan dari server asal (404); dipulihkan dari Radio Browser, favicon situs resmi, dan radioindonesia.org (lihat addendum c-bis).
