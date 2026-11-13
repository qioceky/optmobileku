# RANCANGAN — Opt Mobileku (settingan optimasi Android via Shizuku)

Tanggal: 2026-11-13. Status: **v0.3.3 RILIS 2026-11-14** (diagnosis
refresh tanpa cap; bukti dumpsys: gerbang 90Hz di pemilihan mode vendor).
**v0.3.2 RILIS 2026-11-14** (Log Eksekusi
rinci: perintah + exit + output, atas permintaan user; CI hijau, signer sama).
**v0.3.1 RILIS 2026-11-14** (fix refresh
rate jalur Transsion `tran_*` setelah uji lapangan v0.3.0 di Infinix
ditolak & lock 60Hz; CI hijau, signer sama).
**v0.3.0 RILIS 2026-11-13** atas perintah
user ("Lanjutkan" kedua — v0.3 tampilan & jaringan; CI hijau, signer sama).
**v0.2.0 RILIS 2026-11-13** atas perintah
user ("Lanjutkan") — CI run `43450776481` hijau setelah 1x perbaikan
kompilasi (Elvis di setEnabled), APK 6.587.016 byte, SHA-256 APK
`308020a4…6fdf`, signer SAMA dengan v0.1.0 (menimpa, tanpa uninstall),
izin kini +QUERY_ALL_PACKAGES. Menunggu uji lapangan tab Aplikasi.
**v0.1.0 RILIS 2026-11-13** (repo PUBLIK
`qioceky/optmobileku`, CI run `43448045885` HIJAU sekali jalan, APK
6.537.828 byte, SHA-256 APK `8eb72bd1…1f9c`, signer SHA-256
`A34A3196…764D` terverifikasi apksigner resmi di CI dan lokal, kelas
manifest semua ada di DEX). Menunggu uji lapangan user.
Keputusan user (2026-11-13 21:54 WIB): **lengkap bertahap, mulai dari
v0.1 preset** — roadmap v0.1 → v0.4 di bawah dikunci.
Catatan v0.1: izin `QUERY_ALL_PACKAGES` DITUNDA ke v0.2 (belum dibutuhkan
preset/optimasi dasar; v0.1 hanya izin API Shizuku, tanpa INTERNET).
Permintaan user (2026-11-13 21:49 WIB): "Buatkan project baru aplikasi,
settingan optimasi android via shizuku — Nama app: Opt Mobileku"

## Identitas

- Nama: **Opt Mobileku**
- Package: `id.optmobileku`
- Repo: PUBLIK `qioceky/optmobileku` (direncanakan, belum dibuat)
- Lokal: `~/workspace/optmobileku/`
- Bahasa UI: Indonesia
- Gaya visual: soft neobrutalism (kertas #FFFDF7, tinta #161616, kuning
  #FFD02E, hijau #2FBF71, merah #FF5A5A, biru #DCEEFF) — sama dengan
  MikroKu & RuangKlon. Mockup menyertai rancangan ini.
- Tanpa iklan, tanpa analytics, tanpa backend, **tanpa INTERNET** (target).
  Semua perubahan hanya di HP user sendiri.
- HP acuan: Infinix HOT 60 Pro user (sama seperti project sebelumnya).

## Apa itu Shizuku di aplikasi ini (keputusan teknis inti)

Shizuku menjalankan service dengan hak **shell (uid 2000 / level ADB)**,
BUKAN root. Opt Mobileku menjalankan perintah shell lewat Shizuku
UserService (pola RuangApung yang sudah terbukti jalan di HP user):

- Library: Shizuku API **13.1.5** — `dev.rikka.shizuku:api` +
  `dev.rikka.shizuku:provider` sebagai artifact TERPISAH.
  (Pelajaran RuangApung v0.2.0: provider pindah artifact di v13;
  mendeklarasikan class provider lama = crash saat buka. Verifikasi isi
  AAR sebelum menulis manifest, jangan menebak.)
- Perintah dijalankan serial lewat satu UserService, dengan timeout
  eksplisit, dan **setiap penulisan WAJIB dibaca-balik (read-back)**:
  `settings get …` setelah `settings put …`. Android 14+ makin banyak
  kunci yang diam-diam diabaikan OEM — read-back adalah satu-satunya
  bukti tweak benar-benar diterapkan. UI menampilkan status per tweak:
  DITERAPKAN ✓ / DITOLAK HP ✕ / BELUM DICOBA.
- Mode tanpa Shizuku: aplikasi tetap buka sebagai **mode lihat saja** —
  diagnosis perangkat + nilai setting saat ini (yang bisa dibaca publik)
  + panduan mengaktifkan Shizuku (wireless debugging). Tidak ada tombol
  optimasi yang pura-pura jalan tanpa Shizuku.

## Katalog tweak — semua diverifikasi dari dokumentasi/sumber shell Android

### 1. Kelancaran UI (settings global)
- `window_animation_scale`, `transition_animation_scale`,
  `animator_duration_scale` — pilihan 1.0 (normal) / 0.5 (cepat) / 0 (mati).
  Ini tweak rasa-cepat paling nyata dan paling aman; revert = tulis 1.0.

### 2. Kompilasi aplikasi / ART (cmd package)
- `cmd package bg-dexopt-job` — optimasi semua aplikasi pakai profil
  (job yang sama dengan "App Booster" bawaan OEM). Sekali jalan, bukan
  loop; HP bisa hangat & sibuk beberapa menit — UI wajib memperingatkan.
- `cmd package compile -m speed-profile -f <pkg>` per aplikasi terpilih.
- `pm trim-caches <size>` — pangkas cache semua aplikasi sampai target
  bebas tertentu (mis. `999999999999` = pangkas semaksimalnya yang aman).

### 3. Baterai
- Penghemat baterai: `cmd power set-mode 1|0` (lebih resmi daripada
  menulis `low_power` mentah).
- Baterai adaptif: `settings put global adaptive_battery_management_enabled 1|0`.
- **Daftar putih Doze per aplikasi**: `dumpsys deviceidle whitelist +<pkg>` /
  `-<pkg>` — tambah/kurangi aplikasi yang boleh jalan bebas di background.
- **Bucket standby per aplikasi**: `am set-standby-bucket <pkg>
  active|working_set|frequent|rare|restricted` (+ `am get-standby-bucket`).
- **AppOps background**: `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND
  allow|ignore`, `RUN_IN_BACKGROUND allow|ignore`, `WAKE_LOCK allow|ignore`
  (yang terakhir ditandai AGRESIF — bisa menunda notifikasi aplikasi itu).

### 4. Aplikasi & bloatware
- Daftar semua aplikasi (user + sistem) via PackageManager.
- Nonaktifkan / aktifkan: `pm disable-user --user 0 <pkg>` / `pm enable <pkg>`.
- Bekukan sementara: `pm suspend --user 0 <pkg>` / `pm unsuspend`.
- Copot untuk user ini saja: `pm uninstall -k --user 0 <pkg>` —
  **reversibel** dengan `cmd package install-existing <pkg>`; tombol
  "Pulihkan" wajib ada di layar yang sama. Data aplikasi TIDAK ikut
  musnah di partisi sistem, tapi data user aplikasi itu hilang — peringatan
  eksplisit sebelum eksekusi.
- Paksa berhenti: `am force-stop <pkg>`. Hapus cache per aplikasi:
  lewat `pm clear --cache-only` bila didukung build, bila tidak tombol
  ini disembunyikan (jangan pakai `pm clear` penuh = hapus data).
- **Daftar lindung (tidak bisa dinonaktifkan/dicopot dari aplikasi ini):**
  SystemUI, Settings, launcher aktif, keyboard aktif, Telepon, Shizuku,
  Opt Mobileku sendiri, Google Play Services, dan package inti OEM yang
  masuk daftar blokir terkurasi. Daftar ini hardcoded + tampil transparan.

### 5. Jaringan & data
- DNS Pribadi: `settings put global private_dns_mode off|opportunistic|hostname`
  + `private_dns_specifier` (mis. `dns.google`, `one.one.one.one`).
- Penghemat data global: `cmd netpolicy set restrict-background true|false`.
- Pemindaian WiFi selalu-aktif: `settings put global wifi_scan_always_enabled 0|1`.

### 6. Tampilan
- Skala font: `settings put system font_scale 0.85|1.0|1.15|1.3`.
- Refresh rate (EKSPERIMENTAL, per-OEM): `settings put system
  peak_refresh_rate` + `min_refresh_rate` — WAJIB read-back; bila HP
  menolak / mengabaikan, UI bilang terus terang "tidak didukung HP ini",
  bukan mengklaim berhasil. Infinix user belum terverifikasi untuk kunci ini.
- Kepadatan/resolusi layar (`wm density`, `wm size`) **DITUNDA ke v0.3**
  dan hanya dengan tombol Reset bawaan (`wm density reset`, `wm size reset`)
  di layar yang sama — risiko salah nilai = tampilan kacau.

## Yang TIDAK bisa & TIDAK akan diklaim (batas jujur)

- **Bukan root**: tidak bisa ganti CPU/GPU governor, kernel, thermal,
  build.prop persisten, atau voltage. Aplikasi "game booster" yang
  mengklaim itu tanpa root pada dasarnya berbohong — Opt Mobileku tidak.
- Tidak ada "RAM booster" loop yang menutup aplikasi berulang-ulang —
  itu justru bikin HP lebih lambat & boros (aplikasi cold-start lagi).
- Tidak ada tweak `setprop` sulap / kode "FPS unlock" dari internet yang
  tidak terverifikasi. Setiap tweak di katalog punya perintah, nilai
  asal, dan cara revert yang tercatat.
- Sebagian kunci settings berbeda antar OEM (XOS Infinix vs AOSP);
  yang gagal read-back ditampilkan sebagai gagal, bukan disembunyikan.

## Pengaman (wajib sejak v0.1, bukan menyusul)

1. **Snapshot & Pulihkan**: sebelum perubahan PERTAMA, aplikasi menyimpan
   nilai asal semua kunci settings yang akan disentuh + daftar perubahan
   per aplikasi. Satu tombol **"Pulihkan Semua"** mengembalikan ke
   keadaan sebelum Opt Mobileku menyentuh apa pun.
2. **Log perubahan lokal**: setiap aksi tercatat (waktu, perintah ringkas,
   nilai sebelum → sesudah, hasil read-back). Bisa diekspor/disalin user.
3. **Konfirmasi dua langkah** untuk aksi destruktif: copot user-0,
   nonaktifkan aplikasi sistem, bucket `restricted`, AppOps WAKE_LOCK ignore.
4. **Read-back verification** untuk semua penulisan settings (lihat atas).
5. Penangkap crash lokal (pola RuangApung/MikroKu: log bisa disalin user).

## Arsitektur

- Kotlin native + Jetpack Compose, lapis sederhana:
  UI (Compose) → ViewModel → `OptimizerRepository` → `ShizukuShell`
  (UserService Shizuku, Runtime.exec sebagai uid 2000) + `SettingsStore`
  (DataStore: snapshot, log, preset aktif) .
- Izin Android target: `QUERY_ALL_PACKAGES` (inti aplikasi ini memang
  mengelola aplikasi lain — dicantumkan jujur di README & Play-style
  disclosure di dalam aplikasi) + izin runtime Shizuku itu sendiri.
  Tanpa INTERNET. Tanpa izin berbahaya lain.
- Toolchain disamakan project lain: Gradle 8.14.5, AGP 8.13.2, JDK 21,
  compileSdk/targetSdk 35, minSdk 26.
- Build **hanya** via GitHub Actions CI (Gradle tidak bisa jalan di VM
  Muse). Keystore JKS di-pin sejak rilis pertama (secret repo terpisah
  per aplikasi, pola skill android_ci_apk_signing), verifikasi rilis
  pakai `apksigner` resmi + cek semua class komponen manifest ada di DEX.

## Preset 1-tap (inti v0.1)

| Preset | Animasi | Baterai | Lainnya |
|---|---|---|---|
| **Hemat Baterai** | 0.5x | Penghemat ON, adaptif ON, trim cache | Bucket aplikasi jarang-pakai → `rare` (daftar dipilih user) |
| **Seimbang** (bawaan) | 0.5x | Penghemat OFF, adaptif ON | bg-dexopt dijadwalkan manual dari tombol |
| **Performa / Game** | 0 (mati) | Penghemat OFF, adaptif OFF, game masuk whitelist Doze | Compile `speed-profile` untuk game terpilih |
| **Pulihkan** | kembali ke snapshot | kembali ke snapshot | semua per-app dibatalkan |

Preset BUKAN sulap terpisah — isinya persis tweak katalog di atas,
diterapkan berurutan, dan hasilnya per item tampil di log. User bisa
ubah manual setelah preset diterapkan (status preset jadi "Kustom").

## Roadmap bertahap (usulan — menunggu pilihan user)

### v0.1 — Fondasi + preset (rilis pertama)
- Onboarding Shizuku 5-status (pola RuangApung: belum terpasang /
  belum jalan / belum izin / siap / ditolak) + mode lihat-saja.
- Beranda: info perangkat (model, Android, RAM, penyimpanan, baterai),
  status Shizuku, preset aktif, tombol Pulihkan Semua.
- Preset 1-tap: Hemat / Seimbang / Performa + snapshot & log perubahan.
- Optimasi manual: skala animasi (0 / 0.5 / 1), penghemat baterai,
  baterai adaptif, bg-dexopt, trim cache — semua dengan read-back.
- Tolok ukur go/no-go: di Infinix user, animasi berubah nyata, preset
  Hemat menyalakan penghemat baterai, dan Pulihkan mengembalikan
  semua nilai ke asal (terverifikasi read-back).

### v0.2 — Manajemen aplikasi
- Daftar aplikasi user + sistem, cari, urut ukuran.
- Per aplikasi: bucket standby, whitelist Doze, AppOps background,
  force-stop, suspend, disable (dengan daftar lindung), copot user-0
  + pulihkan (install-existing).

### v0.3 — Tampilan & jaringan
- DNS Pribadi, penghemat data, scan WiFi, skala font.
- Refresh rate eksperimental (read-back, jujur bila ditolak HP).
- wm density/size dengan tombol reset di tempat.

### v0.4 — Profil per aplikasi & pendalaman
- Profil "Mode Game": kumpulan aplikasi + preset yang diterapkan
  sekaligus; profil "Mode Tidur" (hemat maksimal).
- Riwayat/grafik log perubahan, ekspor snapshot.
- (Kandidat, belum dipesan) widget / tile Quick Settings untuk ganti preset.

## Yang TIDAK masuk (sengaja)
- Root-only tweaks, Xposed/LSPosed hooks, overlay "FPS meter" palsu.
- Menonaktifkan aplikasi inti di luar daftar lindung.
- Otomatisasi berkala yang berjalan tanpa sepengetahuan user
  (tidak ada background service pemantau di v0.1–v0.3).
