# Opt Mobileku

Settingan optimasi Android **tanpa root** lewat [Shizuku](https://shizuku.rikka.app/)
(hak shell/ADB, uid 2000) — Bahasa Indonesia, tanpa iklan, tanpa analytics,
**tanpa izin INTERNET**. Semua perubahan hanya di HP pengguna sendiri.

Dibangun lengkap secara bertahap: v0.1 fondasi + preset, menuju manajemen
aplikasi/bloatware (v0.2), tampilan & jaringan (v0.3), dan profil per
aplikasi (v0.4). Rancangan lengkap ada di [`RANCANGAN.md`](RANCANGAN.md).

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

v0.1 hanya memakai izin API Shizuku (`rikka.shizuku.manager.permission.API_V23`).
Tidak ada `INTERNET`, tidak ada `QUERY_ALL_PACKAGES` (yang terakhir baru
dibutuhkan v0.2 untuk daftar aplikasi, dan akan dinyatakan saat itu).

## Build

Build hanya lewat GitHub Actions (`.github/workflows/build.yml`): keystore
tetap dipulihkan dari secret repo, APK release dibangun lalu diverifikasi
dengan `apksigner` resmi — sidik jari penanda tangan harus sama dengan
keystore yang di-pin:

- Signer SHA-256: `A34A3196F98848AF36A5EC8A217E5E69189FADE495A21084C21AAEA84267764D`
- Package: `id.optmobileku`
