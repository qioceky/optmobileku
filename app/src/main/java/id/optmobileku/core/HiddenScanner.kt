package id.optmobileku.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import id.optmobileku.core.Optimizer.TweakResult
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pemindai fitur tersembunyi (v0.6) — evolusi tombol Diagnosis refresh
 * rate menjadi pemindai umum:
 *
 * 1) KUNCI SETTING: saring `settings list` di system/secure/global
 *    dengan pola menarik (refresh/fps/gpu/render/animasi/thermal/game/
 *    layar/baterai/vendor tran_*). Nilai angka/boolean boleh diubah
 *    dengan pengaman penuh: snapshot generik "hidden:<ns>:<key>"
 *    (ikut Pulihkan Semua), konfirmasi di UI, dan baca-balik.
 * 2) MENU TERSEMBYI: kode rahasia (broadcast SECRET_CODE lewat shell)
 *    + aktivitas exported berpola engineer/test/debug/dll yang bisa
 *    dibuka langsung. Hasil buka layar TIDAK bisa diverifikasi dari
 *    aplikasi — dinyatakan jujur di UI & hasil aksi.
 */
object HiddenScanner {

    data class HiddenKey(val namespace: String, val key: String, val value: String?) {
        /** Hanya nilai skalar sederhana yang boleh diubah dari aplikasi. */
        val editable: Boolean
            get() {
                val v = value ?: return false
                return v.toLongOrNull() != null || v.toDoubleOrNull() != null ||
                    v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true)
            }
    }

    data class HiddenActivity(val pkg: String, val appLabel: String, val activity: String) {
        val shortName: String get() = activity.removePrefix(pkg)
    }

    data class SecretCode(val code: String, val title: String, val desc: String)

    val SECRET_CODES = listOf(
        SecretCode(
            "4636", "Info Telepon & Pengujian",
            "Menu Testing bawaan Android: info telepon, statistik pemakaian, baterai, WiFi.",
        ),
        SecretCode(
            "8255", "Monitor Layanan GTalk",
            "Layar debug layanan Google lama — di kebanyakan HP modern penerimanya sudah tidak ada.",
        ),
        SecretCode(
            "7594", "Perilaku Tombol Power",
            "Layar lama pengubah aksi tombol power — umumnya tidak aktif lagi di Android baru.",
        ),
    )

    private val KEY_PATTERNS = listOf(
        "refresh", "fps", "frame", "vsync", "gpu", "hwui", "render", "anim",
        "thermal", "game", "display", "screen", "touch", "battery", "power",
        "perf", "boost", "cpu", "hbm", "bright", "resolution", "density", "tran_",
    )

    private val ACT_PATTERNS = listOf(
        "engineer", "test", "debug", "hidden", "secret", "factory",
        "diagnos", "calibrat", "aging", "hardware",
    )

    private val SAFE_VALUE = Regex("^-?\\d+(\\.\\d+)?$|^(true|false)$", RegexOption.IGNORE_CASE)

    private fun shell(cmd: String): ShizukuCtl.ShellResult? = ShizukuCtl.execBlocking(cmd)

    private fun result(context: Context, name: String, ok: Boolean, detail: String): TweakResult {
        OptStore.addLog(context, "$name ${if (ok) "✓" else "GAGAL"} — $detail")
        return TweakResult(name, ok, detail)
    }

    // ---------- Kunci setting ----------

    suspend fun scanKeys(): List<HiddenKey> = withContext(Dispatchers.IO) {
        val out = mutableListOf<HiddenKey>()
        for (ns in listOf("system", "secure", "global")) {
            val r = shell("settings list $ns")?.takeIf { it.ok } ?: continue
            for (line in r.output.lines()) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                val k = line.substring(0, eq).trim()
                val v = line.substring(eq + 1)
                if (KEY_PATTERNS.any { k.lowercase().contains(it) }) {
                    out += HiddenKey(ns, k, v.takeIf { it.isNotEmpty() })
                }
            }
        }
        out.sortedWith(compareBy({ it.namespace }, { it.key }))
    }

    suspend fun setKey(context: Context, ns: String, key: String, newValue: String): TweakResult =
        withContext(Dispatchers.IO) {
            val name = "Kunci tersembunyi $ns/$key"
            if (!SAFE_VALUE.matches(newValue.trim())) {
                return@withContext result(context, name, false, "Nilai '$newValue' tidak diizinkan (hanya angka/true/false)")
            }
            val value = newValue.trim()
            val current = shell("settings get $ns $key")?.takeIf { it.ok }?.output?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" }
            // Snapshot generik — dipulihkan Pulihkan Semua (id hidden:<ns>:<key>).
            OptStore.snapshotOnce(context, "hidden:$ns:$key", current)
            val put = shell("settings put $ns $key $value")
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            val back = shell("settings get $ns $key")?.output?.trim()
            val ok = put.ok && back == value
            result(
                context, name, ok,
                if (ok) "→ $value ✓ terbaca balik (asal: ${current ?: "tidak ada"}; tercatat di snapshot)"
                else "Terbaca balik: ${back ?: "gagal baca"} — sistem mungkin menimpa nilai ini (cerminan vendor)",
            )
        }

    // ---------- Menu tersembunyi ----------

    suspend fun scanActivities(context: Context): List<HiddenActivity> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            }
        }.getOrDefault(emptyList())
        val out = mutableListOf<HiddenActivity>()
        for (info in packages) {
            if (info.packageName == context.packageName) continue
            val acts = info.activities ?: continue
            val label = runCatching { info.applicationInfo?.loadLabel(pm)?.toString() }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName
            for (a in acts) {
                if (!a.exported) continue
                val lname = a.name.lowercase()
                if (ACT_PATTERNS.any { lname.contains(it) }) {
                    out += HiddenActivity(info.packageName, label, a.name)
                }
            }
        }
        out.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.activity }))
    }

    /** Buka aktivitas exported langsung. True = intent terkirim tanpa error. */
    fun launchActivity(context: Context, entry: HiddenActivity): Boolean = runCatching {
        context.startActivity(
            Intent().setClassName(entry.pkg, entry.activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    suspend fun sendSecretCode(context: Context, code: String): TweakResult =
        withContext(Dispatchers.IO) {
            val name = "Kode rahasia *#*#$code#*#*"
            val r = shell("am broadcast -a android.provider.Telephony.SECRET_CODE -d android_secret_code://$code")
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            result(
                context, name, r.ok,
                if (r.ok) "Broadcast terkirim (exit 0) — cek layar HP; tidak semua HP punya penerima kode ini"
                else "Gagal mengirim broadcast (exit ${r.exitCode})",
            )
        }
}
