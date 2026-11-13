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

    private val ALL_TRACKED = ANIM_KEYS + ADAPTIVE_KEY + LOW_POWER_KEY

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
        if (results.isNotEmpty() && results.all { it.success }) {
            OptStore.setPreset(context, "Belum ada (sudah dipulihkan)")
        }
        OptStore.addLog(context, "Pulihkan Semua: ${results.count { it.success }}/${results.size} kunci kembali ke asal")
        results
    }
}
