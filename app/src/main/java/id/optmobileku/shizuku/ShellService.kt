package id.optmobileku.shizuku

import java.util.concurrent.TimeUnit

/**
 * Implementasi IShellService. Dijalankan sebagai Shizuku user-service:
 * proses terpisah dengan UID shell (2000), sehingga perintahnya punya
 * hak level ADB: `settings put`, `cmd package`, `pm`, `dumpsys`, dll.
 *
 * Wajib punya konstruktor tanpa argumen (dibuat Shizuku lewat refleksi).
 */
class ShellService : IShellService.Stub() {

    override fun exec(cmd: String): String {
        return try {
            val proc = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            // Baca output di thread lain agar proses yang lama tidak
            // membuat pipe penuh; tunggu dengan batas waktu keras.
            val out = StringBuilder()
            val reader = Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(out) { out.append(line).append('\n') }
                    }
                } catch (_: Exception) {
                }
            }
            reader.isDaemon = true
            reader.start()
            val finished = proc.waitFor(120, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { proc.destroy() }
                reader.join(1000)
                return synchronized(out) { out.toString() } + "\n<<<EXIT:-998>>>"
            }
            reader.join(2000)
            val code = proc.exitValue()
            synchronized(out) { out.toString() } + "\n<<<EXIT:$code>>>"
        } catch (e: Exception) {
            "ERROR: ${e.message}\n<<<EXIT:-999>>>"
        }
    }
}
