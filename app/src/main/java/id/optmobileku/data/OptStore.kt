package id.optmobileku.data

import android.content.Context
import android.os.Build
import id.optmobileku.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Penyimpanan lokal Opt Mobileku (file di filesDir, tanpa database):
 *
 * - `snapshot.json` — nilai ASAL setiap setting yang disentuh aplikasi,
 *   dicatat SEKALI sebelum perubahan pertama pada kunci itu. Inilah bahan
 *   tombol "Pulihkan Semua". Nilai JSON null = setting tadinya tidak ada
 *   (dipulihkan dengan `settings delete`).
 * - `opt_log.txt` — log perubahan: satu baris per aksi, terbaru di bawah.
 * - SharedPreferences kecil untuk preset aktif.
 */
object OptStore {

    private const val SNAPSHOT_FILE = "snapshot.json"
    private const val LOG_FILE = "opt_log.txt"
    private const val PREFS = "optmobileku"
    private const val KEY_PRESET = "preset_aktif"

    // ---------- Snapshot ----------

    private fun snapshotFile(context: Context) = File(context.filesDir, SNAPSHOT_FILE)

    @Synchronized
    fun readSnapshot(context: Context): JSONObject {
        val f = snapshotFile(context)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    @Synchronized
    private fun writeSnapshot(context: Context, json: JSONObject) {
        runCatching { snapshotFile(context).writeText(json.toString()) }
    }

    fun hasSnapshot(context: Context): Boolean = readSnapshot(context).length() > 0

    /** Simpan nilai asal kunci [id] HANYA bila belum pernah disimpan. */
    @Synchronized
    fun snapshotOnce(context: Context, id: String, originalValue: String?) {
        val json = readSnapshot(context)
        if (json.has(id)) return
        json.put(id, originalValue ?: JSONObject.NULL)
        writeSnapshot(context, json)
    }

    fun snapshotValue(context: Context, id: String): Pair<Boolean, String?> {
        val json = readSnapshot(context)
        if (!json.has(id)) return false to null
        if (json.isNull(id)) return true to null
        return true to json.optString(id)
    }

    // ---------- Log ----------

    private const val EXEC_LOG_FILE = "exec_log.txt"

    /** Batas karakter output per perintah di log eksekusi. */
    private const val EXEC_OUTPUT_CAP = 1000

    private fun nowStamp(): String =
        SimpleDateFormat("dd-MM HH:mm", Locale.US).format(Date())

    @Synchronized
    fun addLog(context: Context, text: String) {
        runCatching {
            val f = File(context.filesDir, LOG_FILE)
            f.appendText("[${nowStamp()}] $text\n")
            // Jaga ukuran: potong ke 1.000 baris terakhir.
            val lines = f.readLines()
            if (lines.size > 1000) f.writeText(lines.takeLast(1000).joinToString("\n") + "\n")
        }
    }

    fun readLog(context: Context): List<String> {
        val f = File(context.filesDir, LOG_FILE)
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }

    fun clearLog(context: Context) {
        runCatching { File(context.filesDir, LOG_FILE).delete() }
        runCatching { File(context.filesDir, EXEC_LOG_FILE).delete() }
    }

    // ---------- Log eksekusi (v0.3.2) ----------
    // Setiap perintah shell yang dijalankan aplikasi dicatat apa adanya:
    // perintahnya, kode exit, durasi, dan output mentahnya (stdout+stderr
    // sudah digabung di ShellService). exitCode null = perintah TIDAK
    // sempat jalan (Shizuku belum siap). Output dipotong di EXEC_OUTPUT_CAP
    // karakter dengan penanda jelas, supaya file tidak meledak oleh
    // perintah ber-output raksasa seperti `settings list`.

    @Synchronized
    fun addExecLog(context: Context, cmd: String, exitCode: Int?, output: String, durationMs: Long) {
        runCatching {
            val f = File(context.filesDir, EXEC_LOG_FILE)
            val sb = StringBuilder()
            sb.append("[${nowStamp()}] \$ ").append(cmd).append('\n')
            sb.append("  exit=").append(exitCode?.toString() ?: "TIDAK-JALAN (Shizuku belum siap)")
                .append(" • ").append(durationMs).append("ms\n")
            val out = output.trim()
            if (out.isEmpty()) {
                sb.append("  out> (kosong)\n")
            } else {
                val shown = if (out.length > EXEC_OUTPUT_CAP) {
                    out.take(EXEC_OUTPUT_CAP) + "\n  …(output dipotong; total ${out.length} karakter)"
                } else {
                    out
                }
                shown.lines().forEach { sb.append("  out> ").append(it).append('\n') }
            }
            f.appendText(sb.toString())
            // Jaga ukuran: potong ke 1.500 baris terakhir.
            val lines = f.readLines()
            if (lines.size > 1500) f.writeText(lines.takeLast(1500).joinToString("\n") + "\n")
        }
    }

    fun readExecLog(context: Context): List<String> {
        val f = File(context.filesDir, EXEC_LOG_FILE)
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }

    // ---------- Profil per aplikasi (v0.4) ----------
    // app_profiles.json: {pkg: "GAME"|"TIDUR"} — penanda profil aktif.
    // app_profile_snapshots.json: {pkg: {bucket: kode|null, doze: bool|null,
    // bg: bool|null}} — keadaan trio (bucket/whitelist/background) SEBELUM
    // profil pertama diterapkan; bahan tombol "Lepas Profil".

    private const val PROFILES_FILE = "app_profiles.json"
    private const val PROFILE_SNAP_FILE = "app_profile_snapshots.json"

    private fun readJson(context: Context, name: String): JSONObject {
        val f = File(context.filesDir, name)
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
    }

    private fun writeJson(context: Context, name: String, json: JSONObject) {
        runCatching { File(context.filesDir, name).writeText(json.toString()) }
    }

    @Synchronized
    fun getAllAppProfiles(context: Context): Map<String, String> {
        val json = readJson(context, PROFILES_FILE)
        val out = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            json.optString(k).takeIf { it.isNotBlank() }?.let { out[k] = it }
        }
        return out
    }

    fun getAppProfile(context: Context, pkg: String): String? =
        getAllAppProfiles(context)[pkg]

    @Synchronized
    fun setAppProfile(context: Context, pkg: String, profile: String?) {
        val json = readJson(context, PROFILES_FILE)
        if (profile == null) json.remove(pkg) else json.put(pkg, profile)
        writeJson(context, PROFILES_FILE, json)
    }

    /** Simpan keadaan trio pkg HANYA bila belum pernah disimpan. */
    @Synchronized
    fun saveProfileSnapshotOnce(
        context: Context,
        pkg: String,
        bucketCode: Int?,
        dozeWhitelisted: Boolean?,
        backgroundIgnored: Boolean?,
    ) {
        val json = readJson(context, PROFILE_SNAP_FILE)
        if (json.has(pkg)) return
        val entry = JSONObject()
        entry.put("bucket", bucketCode ?: JSONObject.NULL)
        entry.put("doze", dozeWhitelisted ?: JSONObject.NULL)
        entry.put("bg", backgroundIgnored ?: JSONObject.NULL)
        json.put(pkg, entry)
        writeJson(context, PROFILE_SNAP_FILE, json)
    }

    /** (bucketCode, dozeWhitelisted, backgroundIgnored) asal, atau null bila tak ada snapshot. */
    fun getProfileSnapshot(context: Context, pkg: String): Triple<Int?, Boolean?, Boolean?>? {
        val json = readJson(context, PROFILE_SNAP_FILE)
        if (!json.has(pkg)) return null
        val e = json.optJSONObject(pkg) ?: return null
        fun intOrNull(k: String): Int? = if (e.isNull(k)) null else e.optInt(k)
        fun boolOrNull(k: String): Boolean? = if (e.isNull(k)) null else e.optBoolean(k)
        return Triple(intOrNull("bucket"), boolOrNull("doze"), boolOrNull("bg"))
    }

    /**
     * Teks ekspor log lengkap (v0.5): header info perangkat + Log
     * Perubahan + Log Eksekusi, dalam satu dokumen teks. Inilah isi file
     * yang disimpan/dibagikan tombol Ekspor Log di tab Log — format file
     * dipilih karena log yang ditempel ke chat rawan terpotong.
     */
    fun buildExportText(context: Context): String {
        val stamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Opt Mobileku — Ekspor Log")
            appendLine("Diekspor: $stamp")
            appendLine("Aplikasi: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Preset aktif: ${getPreset(context)}")
            appendLine()
            appendLine("=== LOG PERUBAHAN ===")
            appendLine(readLog(context).joinToString("\n"))
            appendLine()
            appendLine("=== LOG EKSEKUSI (perintah + exit + output) ===")
            appendLine(readExecLog(context).joinToString("\n"))
        }
    }

    /** Isi snapshot.json utama dalam bentuk rapi, untuk diekspor user. */
    fun exportSnapshotJson(context: Context): String {
        val f = snapshotFile(context)
        if (!f.exists()) return "{}"
        return runCatching { JSONObject(f.readText()).toString(2) }.getOrDefault("{}")
    }

    // ---------- Preset aktif ----------

    fun getPreset(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PRESET, "Belum ada") ?: "Belum ada"

    fun setPreset(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRESET, name).apply()
    }
}
