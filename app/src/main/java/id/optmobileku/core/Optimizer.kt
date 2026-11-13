package id.optmobileku.core

import android.content.Context
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mesin optimasi Opt Mobileku v0.1.
 *
 * Prinsip yang tidak bisa ditawar (dari RANCANGAN.md):
 * 1. Setiap penulisan setting WAJIB dibaca-balik (read-back). Berhasil
 *    tulis saja tidak cukup — sebagian HP/OEM diam-diam mengabaikan kunci.
 * 2. Sebelum kunci disentuh pertama kali, nilai asalnya masuk snapshot
 *    agar tombol "Pulihkan Semua" selalu punya jalan pulang.
 * 3. Setiap aksi tercatat di log lokal.
 *
 * Semua fungsi suspend dan berjalan di Dispatchers.IO karena panggilan
 * binder Shizuku bersifat memblokir.
 */
object Optimizer {

    data class TweakResult(
        val name: String,
        val success: Boolean,
        val detail: String,
    )

    // Kunci setting yang dilacak snapshot. ID snapshot = "<namespace>/<key>".
    private data class TrackedKey(val namespace: String, val key: String) {
        val id: String get() = "$namespace/$key"
    }

    private val ANIM_KEYS = listOf(
        TrackedKey("global", "window_animation_scale"),
        TrackedKey("global", "transition_animation_scale"),
        TrackedKey("global", "animator_duration_scale"),
    )
    private val ADAPTIVE_KEY = TrackedKey("global", "adaptive_battery_management_enabled")
    private val LOW_POWER_KEY = TrackedKey("global", "low_power")

    // v0.3 — jaringan & tampilan (semua ikut snapshot & Pulihkan Semua)
    private val DNS_MODE_KEY = TrackedKey("global", "private_dns_mode")
    private val DNS_HOST_KEY = TrackedKey("global", "private_dns_specifier")
    private val WIFI_SCAN_KEY = TrackedKey("global", "wifi_scan_always_enabled")
    private val FONT_SCALE_KEY = TrackedKey("system", "font_scale")
    private val PEAK_HZ_KEY = TrackedKey("system", "peak_refresh_rate")
    private val MIN_HZ_KEY = TrackedKey("system", "min_refresh_rate")

    // v0.3.1 — kunci vendor Transsion (Infinix/Tecno/itel, XOS/HiOS).
    // Di HP Transsion, layanan refresh bawaan memakai kunci tran_* ini
    // sebagai master; peak_refresh_rate generik ditimpa/dibuang (uji
    // lapangan v0.3.0 di Infinix user: ditolak, tetap lock 60Hz).
    private val TRAN_MODE_KEY = TrackedKey("system", "tran_refresh_mode")
    private val TRAN_LAST_KEY = TrackedKey("system", "last_tran_refresh_mode_in_refresh_setting")
    private val TRAN_TRANSFORM_KEY = TrackedKey("system", "tran_transform_refresh_mode_in_refresh_setting")
    private val TRAN_RECOVERY_KEY = TrackedKey("system", "tran_need_recovery_refresh_mode")
    private val TRAN_KEYS = listOf(TRAN_MODE_KEY, TRAN_LAST_KEY, TRAN_TRANSFORM_KEY, TRAN_RECOVERY_KEY)

    private val ALL_TRACKED = ANIM_KEYS + ADAPTIVE_KEY + LOW_POWER_KEY +
        listOf(DNS_MODE_KEY, DNS_HOST_KEY, WIFI_SCAN_KEY, FONT_SCALE_KEY, PEAK_HZ_KEY, MIN_HZ_KEY) +
        TRAN_KEYS

    // Snapshot non-settings (bukan kunci settings): status netpolicy & wm.
    const val SNAP_NETPOLICY = "special/netpolicy_restrict"
    const val SNAP_WM_DENSITY = "special/wm_density"
    const val SNAP_WM_SIZE = "special/wm_size"

    // ---------- Util shell ----------

    private fun shell(cmd: String): ShizukuCtl.ShellResult? = ShizukuCtl.execBlocking(cmd)

    /** Baca satu setting. Null = tidak ada / "null" / gagal baca. */
    fun readSettingRaw(namespace: String, key: String): String? {
        val r = shell("settings get $namespace $key") ?: return null
        if (!r.ok) return null
        val v = r.output.trim()
        return if (v.isEmpty() || v == "null") null else v
    }

    private fun valuesEqual(expected: String, actual: String?): Boolean {
        if (actual == null) return false
        val e = expected.toFloatOrNull()
        val a = actual.toFloatOrNull()
        return if (e != null && a != null) e == a else expected == actual
    }

    // ---------- Baca keadaan saat ini (untuk UI) ----------

    data class CurrentState(
        val animationScale: String?,
        val powerSaverOn: Boolean?,
        val adaptiveBatteryOn: Boolean?,
    )

    suspend fun readCurrentState(): CurrentState = withContext(Dispatchers.IO) {
        val anim = readSettingRaw("global", "window_animation_scale")
        val lowPower = readSettingRaw("global", "low_power")
        val adaptive = readSettingRaw("global", "adaptive_battery_management_enabled")
        CurrentState(
            animationScale = anim,
            powerSaverOn = lowPower?.let { it == "1" },
            adaptiveBatteryOn = adaptive?.let { it == "1" },
        )
    }

    // ---------- Tweak individual ----------

    private fun snapshotKey(context: Context, tracked: TrackedKey) {
        val current = readSettingRaw(tracked.namespace, tracked.key)
        OptStore.snapshotOnce(context, tracked.id, current)
    }

    /** Tulis setting + snapshot + read-back. Mengembalikan hasil terverifikasi. */
    private fun writeSettingVerified(
        context: Context,
        tracked: TrackedKey,
        value: String,
        displayName: String,
    ): TweakResult {
        snapshotKey(context, tracked)
        val put = shell("settings put ${tracked.namespace} ${tracked.key} $value")
        if (put == null) {
            return TweakResult(displayName, false, "Shizuku belum siap")
        }
        if (!put.ok) {
            OptStore.addLog(context, "$displayName GAGAL ditulis (exit ${put.exitCode})")
            return TweakResult(displayName, false, "Perintah ditolak HP (exit ${put.exitCode})")
        }
        val back = readSettingRaw(tracked.namespace, tracked.key)
        return if (valuesEqual(value, back)) {
            OptStore.addLog(context, "$displayName → $value ✓ terbaca balik")
            TweakResult(displayName, true, "Diterapkan ✓ (terbaca balik: $back)")
        } else {
            OptStore.addLog(context, "$displayName → $value DITOLAK (terbaca balik: ${back ?: "null"})")
            TweakResult(displayName, false, "HP mengabaikan — terbaca balik: ${back ?: "tidak ada"}")
        }
    }

    suspend fun setAnimationScale(context: Context, scale: String): List<TweakResult> =
        withContext(Dispatchers.IO) {
            ANIM_KEYS.map { key ->
                writeSettingVerified(context, key, scale, "Animasi ${key.key}")
            }.also {
                if (it.all { r -> r.success }) {
                    // Tiga kunci satu nilai: ringkas log tampilannya di UI saja.
                }
            }
        }

    suspend fun setAdaptiveBattery(context: Context, on: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            writeSettingVerified(
                context, ADAPTIVE_KEY, if (on) "1" else "0",
                "Baterai adaptif ${if (on) "ON" else "OFF"}",
            )
        }

    suspend fun setPowerSaver(context: Context, on: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            snapshotKey(context, LOW_POWER_KEY)
            // Cara resmi: cmd power set-mode (bukan tulis low_power mentah).
            val r = shell("cmd power set-mode ${if (on) "1" else "0"}")
            if (r == null) return@withContext TweakResult("Penghemat baterai", false, "Shizuku belum siap")
            // Read-back lewat low_power: set-mode menuliskannya untuk kita.
            val back = readSettingRaw("global", "low_power")
            val expected = if (on) "1" else "0"
            if (valuesEqual(expected, back)) {
                OptStore.addLog(context, "Penghemat baterai → ${if (on) "ON" else "OFF"} ✓ terbaca balik")
                TweakResult("Penghemat baterai", true, "Diterapkan ✓ (low_power=$back)")
            } else {
                OptStore.addLog(context, "Penghemat baterai → ${if (on) "ON" else "OFF"} DITOLAK (low_power=${back ?: "null"}, exit ${r.exitCode})")
                TweakResult("Penghemat baterai", false, "Terbaca balik low_power=${back ?: "tidak ada"}")
            }
        }

    /** Pangkas cache semua aplikasi sampai target bebas sangat besar (aman, hanya cache). */
    suspend fun trimCaches(context: Context): TweakResult = withContext(Dispatchers.IO) {
        val r = shell("pm trim-caches 999999999999")
            ?: return@withContext TweakResult("Pangkas cache", false, "Shizuku belum siap")
        if (r.ok) {
            OptStore.addLog(context, "Pangkas cache semua aplikasi ✓")
            TweakResult("Pangkas cache", true, "Cache semua aplikasi dipangkas ✓")
        } else {
            OptStore.addLog(context, "Pangkas cache GAGAL (exit ${r.exitCode}): ${r.output.take(120)}")
            TweakResult("Pangkas cache", false, "Gagal (exit ${r.exitCode})")
        }
    }

    /**
     * Optimasi ART (bg-dexopt) dijalankan sebagai proses latar di sisi
     * shell — job-nya bisa makan menit dan tidak boleh menahan binder.
     * Mengembalikan sukses = job berhasil DIPICU, bukan selesai.
     */
    suspend fun runBgDexopt(context: Context): TweakResult = withContext(Dispatchers.IO) {
        val cmd = "sh -c 'nohup cmd package bg-dexopt-job >/data/local/tmp/optmobileku-dexopt.log 2>&1 &' ; echo dipicu"
        val r = shell(cmd)
            ?: return@withContext TweakResult("Optimasi aplikasi (ART)", false, "Shizuku belum siap")
        if (r.ok) {
            OptStore.addLog(context, "Optimasi ART (bg-dexopt) dipicu di latar")
            TweakResult(
                "Optimasi aplikasi (ART)", true,
                "Dipicu di latar ✓ — HP akan sibuk beberapa menit, biarkan saja",
            )
        } else {
            OptStore.addLog(context, "Optimasi ART GAGAL dipicu (exit ${r.exitCode})")
            TweakResult("Optimasi aplikasi (ART)", false, "Gagal dipicu (exit ${r.exitCode})")
        }
    }

    // ---------- Preset ----------

    enum class Preset(val label: String) {
        HEMAT("Hemat Baterai"),
        SEIMBANG("Seimbang"),
        PERFORMA("Performa / Game"),
    }

    suspend fun applyPreset(context: Context, preset: Preset): List<TweakResult> {
        val results = mutableListOf<TweakResult>()
        when (preset) {
            Preset.HEMAT -> {
                results += setAnimationScale(context, "0.5")
                results += setPowerSaver(context, true)
                results += setAdaptiveBattery(context, true)
            }
            Preset.SEIMBANG -> {
                results += setAnimationScale(context, "0.5")
                results += setPowerSaver(context, false)
                results += setAdaptiveBattery(context, true)
            }
            Preset.PERFORMA -> {
                results += setAnimationScale(context, "0")
                results += setPowerSaver(context, false)
                results += setAdaptiveBattery(context, false)
            }
        }
        if (results.all { it.success }) {
            OptStore.setPreset(context, preset.label)
        } else {
            OptStore.setPreset(context, preset.label + " (sebagian)")
        }
        OptStore.addLog(context, "Preset ${preset.label} diterapkan: ${results.count { it.success }}/${results.size} berhasil")
        return results
    }

    // ---------- Pulihkan ----------

    /**
     * Kembalikan SEMUA kunci terlacak ke nilai snapshot asal.
     * Kunci yang tadinya tidak ada dihapus lagi. low_power dipulihkan
     * lewat cmd power set-mode. Snapshot TIDAK dihapus (bisa pulihkan
     * berulang), preset aktif direset.
     */
    suspend fun restoreAll(context: Context): List<TweakResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TweakResult>()
        for (tracked in ALL_TRACKED) {
            val (exists, original) = OptStore.snapshotValue(context, tracked.id)
            if (!exists) continue
            if (tracked == LOW_POWER_KEY) {
                val on = original == "1"
                val r = shell("cmd power set-mode ${if (on) "1" else "0"}")
                val back = readSettingRaw("global", "low_power")
                val okRestore = r != null && valuesEqual(if (on) "1" else "0", back)
                results += TweakResult(
                    "Pulihkan penghemat baterai",
                    okRestore,
                    if (okRestore) "Kembali ke asal ($back) ✓" else "Gagal — terbaca balik ${back ?: "tidak ada"}",
                )
                continue
            }
            if (original == null) {
                shell("settings delete ${tracked.namespace} ${tracked.key}")
                val back = readSettingRaw(tracked.namespace, tracked.key)
                val okRestore = back == null
                results += TweakResult(
                    "Pulihkan ${tracked.key}",
                    okRestore,
                    if (okRestore) "Kembali tidak ada (asal) ✓" else "Masih terbaca: $back",
                )
            } else {
                val put = shell("settings put ${tracked.namespace} ${tracked.key} $original")
                val back = readSettingRaw(tracked.namespace, tracked.key)
                val okRestore = put != null && put.ok && valuesEqual(original, back)
                results += TweakResult(
                    "Pulihkan ${tracked.key}",
                    okRestore,
                    if (okRestore) "Kembali ke asal ($original) ✓" else "Gagal — terbaca balik ${back ?: "tidak ada"}",
                )
            }
        }
        // Pulihkan item non-settings (netpolicy & wm) dari snapshot spesial.
        val (npExists, npOrig) = OptStore.snapshotValue(context, SNAP_NETPOLICY)
        if (npExists) {
            val on = npOrig == "true"
            val r = shell("cmd netpolicy set restrict-background $on")
            val back = readRestrictBackground()
            val okR = r != null && r.ok && (back == null || back == on)
            results += TweakResult(
                "Pulihkan penghemat data",
                okR,
                if (okR) "Kembali ke asal (${if (on) "ON" else "OFF"}) ✓" else "Gagal memulihkan netpolicy",
            )
        }
        val (densExists, densOrig) = OptStore.snapshotValue(context, SNAP_WM_DENSITY)
        if (densExists) {
            val r = if (densOrig == null) shell("wm density reset") else shell("wm density $densOrig")
            val back = readWmDensity().second
            val okR = r != null && r.ok && back == densOrig
            results += TweakResult(
                "Pulihkan kepadatan layar",
                okR,
                if (okR) "Kembali ke asal (${densOrig ?: "bawaan"}) ✓" else "Terbaca balik override=$back",
            )
        }
        val (sizeExists, sizeOrig) = OptStore.snapshotValue(context, SNAP_WM_SIZE)
        if (sizeExists) {
            val r = if (sizeOrig == null) shell("wm size reset") else shell("wm size $sizeOrig")
            val back = readWmSize().second
            val okR = r != null && r.ok && back == sizeOrig
            results += TweakResult(
                "Pulihkan ukuran layar",
                okR,
                if (okR) "Kembali ke asal (${sizeOrig ?: "bawaan"}) ✓" else "Terbaca balik override=$back",
            )
        }
        if (results.isNotEmpty() && results.all { it.success }) {
            OptStore.setPreset(context, "Belum ada (sudah dipulihkan)")
        }
        OptStore.addLog(context, "Pulihkan Semua: ${results.count { it.success }}/${results.size} kunci kembali ke asal")
        results
    }

    // ================= v0.3 — Jaringan & Tampilan =================

    data class NetDisplayState(
        val dnsMode: String?,
        val dnsHost: String?,
        val restrictBackground: Boolean?,
        val wifiScanAlways: Boolean?,
        val fontScale: String?,
        val peakHz: String?,
        val minHz: String?,
        val tranRefreshMode: String?,
        val densityPhysical: String?,
        val densityOverride: String?,
        val sizePhysical: String?,
        val sizeOverride: String?,
    )

    suspend fun readNetDisplayState(): NetDisplayState = withContext(Dispatchers.IO) {
        val (dp, do_) = readWmDensity()
        val (sp, so) = readWmSize()
        NetDisplayState(
            dnsMode = readSettingRaw("global", "private_dns_mode"),
            dnsHost = readSettingRaw("global", "private_dns_specifier"),
            restrictBackground = readRestrictBackground(),
            wifiScanAlways = readSettingRaw("global", "wifi_scan_always_enabled")?.let { it == "1" },
            fontScale = readSettingRaw("system", "font_scale"),
            peakHz = readSettingRaw("system", "peak_refresh_rate"),
            minHz = readSettingRaw("system", "min_refresh_rate"),
            tranRefreshMode = readSettingRaw("system", "tran_refresh_mode"),
            densityPhysical = dp,
            densityOverride = do_,
            sizePhysical = sp,
            sizeOverride = so,
        )
    }

    /** Baca status penghemat data global. Null = tidak terbaca di HP ini. */
    fun readRestrictBackground(): Boolean? {
        // Jalur 1: cmd netpolicy get (tersedia di sebagian build)
        shell("cmd netpolicy get restrict-background")?.takeIf { it.ok }?.output?.let { out ->
            val low = out.lowercase()
            if ("enabled" in low || low.trim() == "true") return true
            if ("disabled" in low || low.trim() == "false") return false
        }
        // Jalur 2: dumpsys netpolicy — cari baris restrict background
        val dump = shell("dumpsys netpolicy")?.takeIf { it.ok }?.output ?: return null
        for (line in dump.lines()) {
            val low = line.lowercase()
            if ("restrict" in low && "background" in low) {
                if ("true" in low || "enabled" in low) return true
                if ("false" in low || "disabled" in low) return false
            }
        }
        return null
    }

    suspend fun setPrivateDnsMode(context: Context, mode: String, label: String): TweakResult =
        withContext(Dispatchers.IO) {
            writeSettingVerified(context, DNS_MODE_KEY, mode, "DNS Pribadi mode $label")
        }

    suspend fun setPrivateDnsHost(context: Context, host: String): List<TweakResult> =
        withContext(Dispatchers.IO) {
            val clean = host.trim().removePrefix("https://").trimEnd('/')
            listOf(
                writeSettingVerified(context, DNS_HOST_KEY, clean, "DNS Pribadi host"),
                writeSettingVerified(context, DNS_MODE_KEY, "hostname", "DNS Pribadi mode Hostname"),
            )
        }

    suspend fun setWifiScanAlways(context: Context, on: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            writeSettingVerified(
                context, WIFI_SCAN_KEY, if (on) "1" else "0",
                "Scan WiFi selalu aktif ${if (on) "ON" else "OFF"}",
            )
        }

    suspend fun setRestrictBackground(context: Context, on: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            val before = readRestrictBackground()
            if (before != null) OptStore.snapshotOnce(context, SNAP_NETPOLICY, before.toString())
            val r = shell("cmd netpolicy set restrict-background $on")
            if (r == null) return@withContext TweakResult("Penghemat data", false, "Shizuku belum siap")
            val back = readRestrictBackground()
            when {
                back == on -> {
                    OptStore.addLog(context, "Penghemat data → ${if (on) "ON" else "OFF"} ✓ terbaca balik")
                    TweakResult("Penghemat data", true, "Diterapkan ✓ (terbaca balik)")
                }
                back == null && r.ok -> {
                    OptStore.addLog(context, "Penghemat data → ${if (on) "ON" else "OFF"} terkirim (baca-balik tidak tersedia di HP ini)")
                    TweakResult("Penghemat data", true, "Perintah terkirim ✓ — tapi HP ini tidak menyediakan baca-balik statusnya, cek di Pengaturan > Jaringan bila ragu")
                }
                else -> {
                    OptStore.addLog(context, "Penghemat data → ${if (on) "ON" else "OFF"} DITOLAK (terbaca balik $back)")
                    TweakResult("Penghemat data", false, "Terbaca balik tidak cocok: $back")
                }
            }
        }

    suspend fun setFontScale(context: Context, scale: String): TweakResult =
        withContext(Dispatchers.IO) {
            writeSettingVerified(context, FONT_SCALE_KEY, scale, "Skala font $scale")
        }

    /**
     * Refresh rate EKSPERIMENTAL (v0.3.1): tulis jalur generik AOSP
     * (peak format desimal, mis. "120.0") DAN jalur vendor Transsion
     * (kunci tran_* bernilai Hz bulat). Hasil per kunci dilaporkan apa
     * adanya lewat read-back — kunci yang tidak ada/diabaikan ROM akan
     * terlihat ditolak, dan itu bahan diagnosis, bukan kegagalan diam.
     */
    suspend fun setPeakRefreshRate(context: Context, hz: String): List<TweakResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<TweakResult>()
            results += writeSettingVerified(context, PEAK_HZ_KEY, "$hz.0", "Refresh rate puncak (generik) ${hz}Hz")
            for (key in TRAN_KEYS) {
                results += writeSettingVerified(context, key, hz, "Refresh rate XOS ${key.key}")
            }
            results
        }

    /** Lepas semua kunci refresh (generik + Transsion) kembali ke bawaan. */
    suspend fun clearPeakRefreshRate(context: Context): List<TweakResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TweakResult>()
        for (key in listOf(PEAK_HZ_KEY) + TRAN_KEYS) {
            snapshotKey(context, key)
            shell("settings delete ${key.namespace} ${key.key}")
            val back = readSettingRaw(key.namespace, key.key)
            val ok = back == null
            OptStore.addLog(context, "Lepas ${key.key} ${if (ok) "✓" else "(masih terbaca $back)"}")
            results += TweakResult(
                "Lepas ${key.key}", ok,
                if (ok) "Terhapus — kembali bawaan ✓" else "Masih terbaca: $back",
            )
        }
        results
    }

    /**
     * Diagnosis refresh rate: tulis semua kunci settings yang mengandung
     * "refresh"/"tran_"/"preferred" dari ketiga namespace (TANPA cap —
     * v0.3.2 memotong di 40 kunci dan ekor daftarnya hilang) + baris
     * dumpsys display soal refresh/preferred/mode aktif ke Log aplikasi.
     */
    suspend fun diagnoseRefreshRate(context: Context): TweakResult = withContext(Dispatchers.IO) {
        val sb = StringBuilder("=== Diagnosis refresh rate ===")
        for (ns in listOf("system", "secure", "global")) {
            val out = shell("settings list $ns")?.takeIf { it.ok }?.output.orEmpty()
            val hits = out.lines().filter { line ->
                val low = line.lowercase()
                ("refresh" in low || "tran_" in low || "preferred" in low) && line.isNotBlank()
            }
            sb.append("\n-- $ns (${hits.size} kunci) --")
            if (hits.isEmpty()) sb.append("\n(tidak ada)")
            hits.forEach { sb.append("\n$it") }
        }
        val disp = shell("dumpsys display | grep -iE 'refresh|preferred|baseMode|activeMode|desiredDisplay' | head -40")
            ?.takeIf { it.ok }?.output.orEmpty()
        sb.append("\n-- dumpsys display (refresh/preferred/mode) --")
        if (disp.isBlank()) sb.append("\n(tidak terbaca)") else sb.append("\n").append(disp.trim())
        OptStore.addLog(context, sb.toString())
        TweakResult(
            "Diagnosis refresh rate", true,
            "Ditulis ke tab Log ✓ — buka tab Log, Salin Log, kirim ke Qandy bila refresh masih ditolak",
        )
    }

    /** Paksa refresh tetap: min = peak. Matikan = hapus kunci min. */
    suspend fun setForceConstantRefresh(context: Context, on: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            snapshotKey(context, MIN_HZ_KEY)
            if (!on) {
                shell("settings delete system min_refresh_rate")
                val back = readSettingRaw("system", "min_refresh_rate")
                val ok = back == null
                OptStore.addLog(context, "Paksa refresh tetap OFF ${if (ok) "✓" else "(min masih $back)"}")
                return@withContext TweakResult("Paksa refresh tetap", ok, if (ok) "Dimatikan ✓" else "Min masih terbaca: $back")
            }
            val peak = readSettingRaw("system", "peak_refresh_rate")
                ?: return@withContext TweakResult(
                    "Paksa refresh tetap", false,
                    "Atur dulu refresh rate puncak di atas — nilai min diambil darinya",
                )
            writeSettingVerified(context, MIN_HZ_KEY, peak, "Refresh rate minimum $peak Hz")
        }

    // ---------- wm density / size ----------

    /** (physical, override) dari `wm density`. */
    fun readWmDensity(): Pair<String?, String?> {
        val out = shell("wm density")?.takeIf { it.ok }?.output ?: return null to null
        var physical: String? = null
        var override: String? = null
        for (line in out.lines()) {
            val t = line.trim()
            if (t.startsWith("Physical density:")) physical = t.removePrefix("Physical density:").trim()
            if (t.startsWith("Override density:")) override = t.removePrefix("Override density:").trim()
        }
        return physical to override
    }

    /** (physical, override) dari `wm size`, format "1080x2400". */
    fun readWmSize(): Pair<String?, String?> {
        val out = shell("wm size")?.takeIf { it.ok }?.output ?: return null to null
        var physical: String? = null
        var override: String? = null
        for (line in out.lines()) {
            val t = line.trim()
            if (t.startsWith("Physical size:")) physical = t.removePrefix("Physical size:").trim()
            if (t.startsWith("Override size:")) override = t.removePrefix("Override size:").trim()
        }
        return physical to override
    }

    private fun snapshotWmDensity(context: Context) {
        OptStore.snapshotOnce(context, SNAP_WM_DENSITY, readWmDensity().second)
    }

    private fun snapshotWmSize(context: Context) {
        OptStore.snapshotOnce(context, SNAP_WM_SIZE, readWmSize().second)
    }

    suspend fun setWmDensity(context: Context, dpi: Int): TweakResult = withContext(Dispatchers.IO) {
        snapshotWmDensity(context)
        val r = shell("wm density $dpi")
            ?: return@withContext TweakResult("Kepadatan layar", false, "Shizuku belum siap")
        val back = readWmDensity().second
        val ok = r.ok && back == dpi.toString()
        OptStore.addLog(context, "Kepadatan layar → $dpi ${if (ok) "✓" else "DITOLAK (override=$back)"}")
        TweakResult("Kepadatan layar", ok, if (ok) "Diterapkan ✓ (override $dpi dpi)" else "Terbaca balik override=${back ?: "tidak ada"}")
    }

    suspend fun resetWmDensity(context: Context): TweakResult = withContext(Dispatchers.IO) {
        snapshotWmDensity(context)
        val r = shell("wm density reset")
            ?: return@withContext TweakResult("Kepadatan layar", false, "Shizuku belum siap")
        val back = readWmDensity().second
        val ok = r.ok && back == null
        OptStore.addLog(context, "Kepadatan layar direset ${if (ok) "✓" else "(override masih $back)"}")
        TweakResult("Kepadatan layar", ok, if (ok) "Kembali ke bawaan ✓" else "Override masih terbaca: $back")
    }

    suspend fun setWmSizePercent(context: Context, percent: Int): TweakResult = withContext(Dispatchers.IO) {
        snapshotWmSize(context)
        val (physical, _) = readWmSize()
        val parts = physical?.split("x")?.map { it.trim().toIntOrNull() }
        if (parts == null || parts.size != 2 || parts.any { it == null }) {
            return@withContext TweakResult("Ukuran layar", false, "Ukuran fisik layar tidak terbaca di HP ini")
        }
        val w = parts[0]!! * percent / 100
        val h = parts[1]!! * percent / 100
        val target = "${w}x${h}"
        val r = shell("wm size $target")
            ?: return@withContext TweakResult("Ukuran layar", false, "Shizuku belum siap")
        val back = readWmSize().second
        val ok = r.ok && back == target
        OptStore.addLog(context, "Ukuran layar → $target ($percent%) ${if (ok) "✓" else "DITOLAK (override=$back)"}")
        TweakResult("Ukuran layar", ok, if (ok) "Diterapkan ✓ ($target)" else "Terbaca balik override=${back ?: "tidak ada"}")
    }

    suspend fun resetWmSize(context: Context): TweakResult = withContext(Dispatchers.IO) {
        snapshotWmSize(context)
        val r = shell("wm size reset")
            ?: return@withContext TweakResult("Ukuran layar", false, "Shizuku belum siap")
        val back = readWmSize().second
        val ok = r.ok && back == null
        OptStore.addLog(context, "Ukuran layar direset ${if (ok) "✓" else "(override masih $back)"}")
        TweakResult("Ukuran layar", ok, if (ok) "Kembali ke bawaan ✓" else "Override masih terbaca: $back")
    }
}
