# Opt Mobileku

Settingan optimasi Android **tanpa root** lewat [Shizuku](https://shizuku.rikka.app/)
(hak shell/ADB, uid 2000) — Bahasa Indonesia, tanpa iklan, tanpa analytics,
**tanpa izin INTERNET**. Semua perubahan hanya di HP pengguna sendiri.

Dibangun lengkap secara bertahap: v0.1 fondasi + preset, menuju manajemen
aplikasi/bloatware (v0.2), tampilan & jaringan (v0.3), dan profil per
aplikasi (v0.4). Rancangan lengkap ada di [`RANCANGAN.md`](RANCANGAN.md).

## Fitur v0.3.0 — Tampilan & jaringan (baru)

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
