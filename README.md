# Opt Mobileku

Settingan optimasi Android **tanpa root** lewat [Shizuku](https://shizuku.rikka.app/)
(hak shell/ADB, uid 2000) — Bahasa Indonesia, tanpa iklan, tanpa analytics,
**tanpa izin INTERNET**. Semua perubahan hanya di HP pengguna sendiri.

Dibangun lengkap secara bertahap: v0.1 fondasi + preset, menuju manajemen
aplikasi/bloatware (v0.2), tampilan & jaringan (v0.3), dan profil per
aplikasi (v0.4). Rancangan lengkap ada di [`RANCANGAN.md`](RANCANGAN.md).

## Perbaikan v0.3.3 — Diagnosis refresh tanpa potongan

Temuan dari log diagnosis v0.3.2 di Infinix user: panel memang punya
mode asli 60/90/120/144Hz; `mDesiredDisplayModeSpecs` ikut bergerak ke
120 saat `peak_refresh_rate=120`, tapi `baseModeId` & mode aktif
SurfaceFlinger tetap 90Hz, dan XOS menulis ulang `peak_refresh_rate`
jadi 90.0 dari waktu ke waktu — gerbangnya ada di pemilihan mode
display versi vendor, bukan di settings. Diagnosis v0.3.2 terpotong
tepat 40 kunci sehingga ekor daftar (yang bisa memuat kunci penentu)
hilang. v0.3.3: daftar kunci diagnosis **tanpa cap** + pola tambahan
"preferred", dan grep dumpsys diperluas ke baris preferred/baseMode/
activeMode/desiredDisplay.

## Perbaikan v0.3.2 — Log Eksekusi rinci

Atas permintaan user, log tidak lagi cuma ringkasan teks. Tab Log kini
punya **Log Eksekusi**: SETIAP perintah shell yang dijalankan aplikasi
dicatat apa adanya — teks perintahnya, **kode exit**, durasi, dan
**output mentah** (stdout+stderr memang sudah digabung di ShellService).
Perintah yang tidak sempat jalan tercatat `exit=TIDAK-JALAN (Shizuku
belum siap)`. Output raksasa dipotong 1.000 karakter dengan penanda
jumlah totalnya. Tombol Salin Log menggabungkan Log Perubahan + Log
Eksekusi dalam satu teks. Implementasi: hook `execLogger` di
`ShizukuCtl.execBlocking` (satu-satunya gerbang eksekusi) → file
`exec_log.txt` terpisah via `OptStore`.

## Fitur v0.6.0 — Pemindai fitur tersembunyi (tab Rahasia)

Atas permintaan user ("cari fitur tersembunyi", keduanya dipilih):

- **Kunci setting tersembunyi**: pindai `settings list` di
  system/secure/global, disaring pola menarik (refresh/fps, GPU &
  render, animasi, thermal, game, layar, baterai, vendor `tran_*`…).
  Tampil nama + namespace + nilai sekarang, dengan pencarian. Ketuk
  kunci untuk detail: nilai angka/boolean bisa **diubah** — nilai asal
  disnapshot generik (`hidden:<ns>:<key>`, ikut **Pulihkan Semua**),
  penulisan dibaca-balik; nilai teks/JSON hanya bisa dilihat & disalin
  namanya. `Optimizer.restoreAll` diperluas memulihkan entri
  `hidden:` secara generik.
- **Menu tersembunyi**: (a) kode rahasia — 4636 (Testing), 8255,
  7594 — dikirim sebagai broadcast `SECRET_CODE` lewat shell, setara
  mengetik di dialer; (b) pemindai aktivitas **exported** berpola
  engineer/test/debug/hidden/secret/factory/diagnosa/kalibrasi dari
  semua aplikasi terpasang, ketuk untuk membuka langsung.
- Batas jujur dinyatakan di UI: aplikasi tidak bisa memverifikasi
  layar tujuan benar-benar tampil (kode bisa tak punya penerima;
  aktivitas bisa langsung menutup).

## Fitur v0.5.0 — Ekspor log, ringkasan, tile Quick Settings

Dipilih user dari daftar usulan (opsi 2 + 3; ekspor/impor data tidak
diambil kali ini):

- **Ekspor Log ke file**: tombol Simpan (pemilih file sistem) dan
  Bagikan (lembar berbagi Android) di tab Log. Isi file: header info
  HP (model, versi Android, versi aplikasi, preset aktif) + Log
  Perubahan + Log Eksekusi lengkap — menggantikan pola tempel-ke-chat
  yang rawan terpotong. Berbagi berjalan lewat FileProvider terbatas
  folder cache ekspor.
- **Ringkasan log**: kartu hitungan di tab Log — perubahan berhasil
  (✓) vs gagal (GAGAL) dan total baris kedua log.
- **Tile Quick Settings**: tile "Preset Opt Mobileku" untuk panel
  notifikasi — ketuk untuk memutar Hemat → Seimbang → Performa tanpa
  membuka aplikasi. Syarat jujur: Shizuku harus sedang jalan & izin
  ada; bila belum siap, ketukan membuka aplikasi utama dan subtitle
  tile menyatakan "Shizuku belum siap". Subtitle selalu menampilkan
  preset yang benar-benar tersimpan (hanya berubah bila penerapan
  sukses penuh).

Izin aplikasi TIDAK berubah (tile memakai izin sistem
BIND_QUICK_SETTINGS_TILE khusus service QS, bukan izin runtime).

## Fitur v0.4.0 — Profil per aplikasi

Di tab Aplikasi, setiap aplikasi (selain daftar lindung) kini bisa
diberi **profil** — tiga knob v0.2 diterapkan sekaligus:

- **🎮 Mode Game**: bucket standby Aktif + whitelist Doze + background
  diizinkan.
- **😴 Mode Tidur**: bucket Dibatasi + whitelist dicabut + background
  ignore (hemat maksimal; notifikasi bisa telat, dinyatakan di UI).
- Sebelum profil pertama diterapkan, keadaan asal trio aplikasi itu
  **disnapshot**; tombol **Lepas Profil** memulihkannya persis.
  Penanda profil hanya disimpan bila 3/3 aksi terbaca-balik sukses.
- Kartu **Terapkan Ulang Semua Profil** di daftar aplikasi untuk
  menerapkan ulang seluruh profil tersimpan sekaligus (berguna sesudah
  restart HP).
- **Ekspor snapshot**: tombol Salin Snapshot (JSON) di tab Info.
- Kartu refresh rate kini memuat catatan terverifikasi XOS: plafon
  120/144Hz aktif di game/aplikasi terpilih, UI umum ditahan 90Hz dari
  pabrik — sama seperti Pengaturan bawaan (kasus ditutup 2026-11-14).

Yang belum masuk v0.4 (jujur): grafik riwayat perubahan — riwayat teks
di tab Log tetap tersedia.

## Perbaikan v0.3.1 — Refresh rate jalur Transsion

Uji lapangan v0.3.0 di Infinix: refresh rate ditolak & tetap lock 60Hz
meski panel support s.d. 144Hz. Penyebabnya: XOS memakai kunci vendor
Transsion sebagai master, bukan `peak_refresh_rate` generik. v0.3.1:

- Tombol refresh rate kini menulis **dua jalur**: generik
  (`peak_refresh_rate` format desimal) + vendor (`tran_refresh_mode`,
  `last_tran_refresh_mode_in_refresh_setting`,
  `tran_transform_refresh_mode_in_refresh_setting`,
  `tran_need_recovery_refresh_mode`) — tiap kunci dibaca-balik dan
  dilaporkan satu per satu; semua ikut snapshot & Pulihkan Semua.
- Tombol **144Hz**; kartu refresh menampilkan mode XOS saat ini.
- Tombol **Diagnosis ke Log**: membuang semua kunci settings yang
  mengandung "refresh"/"tran_" (system/secure/global) + ringkasan
  `dumpsys display` ke tab Log untuk bahan diagnosis.

## Fitur v0.3.0 — Tampilan & jaringan

Di tab Optimasi, dua seksi baru — semua ikut **snapshot & Pulihkan Semua**:

- **Jaringan**: DNS Pribadi (Mati / Otomatis / Hostname + kolom hostname),
  Penghemat Data global (`cmd netpolicy set restrict-background`),
  Scan WiFi selalu aktif ON/OFF.
- **Tampilan**: skala font (0.85–1.3), **refresh rate eksperimental**
  (peak 60/90/120 + lepas, paksa-tetap min=peak; selalu dibaca-balik —
  bila HP menolak, UI bilang menolak), kepadatan layar (`wm density`
  ±20 / Reset, dengan konfirmasi), ukuran layar (`wm size` 90% / Reset,
  dengan konfirmasi).
- Catatan kejujuran: status penghemat data tidak punya jalur baca-balik
  yang stabil di semua HP; bila tidak terbaca, UI menyatakannya apa
  adanya, bukan mengklaim terverifikasi.

## Fitur v0.2.0 — Manajemen aplikasi & bloatware

- **Tab Aplikasi**: daftar semua aplikasi (pengguna + sistem), cari nama /
  package, filter Semua / Pengguna / Sistem / Nonaktif.
- **Per aplikasi** (semua dibaca-balik & tercatat di log):
  - Bucket standby: Aktif / Sering dipakai / Kadang / Jarang / Dibatasi
  - Whitelist Doze: masukkan / cabut
  - Background: batasi (AppOps ignore) / izinkan
  - Paksa berhenti, Bekukan (suspend) / Cairkan
  - Nonaktifkan / Aktifkan (konfirmasi dua langkah)
  - **Copot untuk user ini saja** (`pm uninstall --user 0`, konfirmasi dua
    langkah) + **Pulihkan** (`install-existing`) dari layar yang sama
- **Daftar lindung**: aplikasi inti sistem + launcher/keyboard/dialer
  bawaan (dideteksi dari perannya) + Shizuku + Opt Mobileku sendiri tidak
  punya tombol destruktif sama sekali.
- Izin baru v0.2: `QUERY_ALL_PACKAGES` — memang inti tab ini; dinyatakan
  terbuka di sini. Tetap tanpa INTERNET.

## Fitur v0.1.0 — Fondasi + Preset

- **Onboarding Shizuku 5 status**: belum terpasang / belum jalan / belum
  izin / menghubungkan / siap — plus panduan mengaktifkan Shizuku lewat
  Wireless Debugging di tab Info. Tanpa Shizuku, aplikasi masuk mode
  lihat-saja (tombol optimasi mati, bukan pura-pura jalan).
- **Preset 1-tap**:
  - 🔋 **Hemat Baterai** — animasi 0.5x, penghemat baterai ON, baterai adaptif ON
  - ⚖️ **Seimbang** — animasi 0.5x, penghemat OFF, adaptif ON
  - 🎮 **Performa / Game** — animasi mati (0), penghemat OFF, adaptif OFF
- **Optimasi manual**: skala animasi (0 / 0.5x / 1x), penghemat baterai
  (`cmd power set-mode`), baterai adaptif, optimasi ART (`bg-dexopt`,
  dipicu di latar), pangkas cache semua aplikasi (`pm trim-caches`).

## Pengaman (sejak v0.1, bukan menyusul)

- **Snapshot & Pulihkan Semua**: nilai asal setiap setting dicatat sebelum
  perubahan pertama; satu tombol mengembalikan semuanya, terverifikasi
  baca-balik per kunci.
- **Read-back verification**: setiap penulisan dibaca balik. Bila HP/OEM
  mengabaikan sebuah setting, UI bilang terus terang — tidak mengklaim
  berhasil.
- **Log perubahan lokal** (tab Log, bisa disalin) + penangkap crash lokal.

## Batas jujur

Shizuku adalah hak shell, **bukan root**: tidak ada governor CPU/GPU,
kernel, build.prop, atau klaim "FPS unlock". Tidak ada "RAM booster" yang
menutup aplikasi berulang-ulang (itu justru memperlambat HP).

## Izin

Izin API Shizuku (`rikka.shizuku.manager.permission.API_V23`) + sejak v0.2
`QUERY_ALL_PACKAGES` (inti tab Aplikasi). Tidak ada `INTERNET`.

## Build

Build hanya lewat GitHub Actions (`.github/workflows/build.yml`): keystore
tetap dipulihkan dari secret repo, APK release dibangun lalu diverifikasi
dengan `apksigner` resmi — sidik jari penanda tangan harus sama dengan
keystore yang di-pin:

- Signer SHA-256: `A34A3196F98848AF36A5EC8A217E5E69189FADE495A21084C21AAEA84267764D`
- Package: `id.optmobileku`
