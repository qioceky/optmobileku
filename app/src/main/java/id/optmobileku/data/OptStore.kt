package id.optmobileku.data

import android.content.Context
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

    // ---------- Preset aktif ----------

    fun getPreset(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PRESET, "Belum ada") ?: "Belum ada"

    fun setPreset(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRESET, name).apply()
    }
}
