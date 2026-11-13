package id.optmobileku

import android.app.Application
import id.optmobileku.data.CrashLogger
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl

class OptApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        // Log eksekusi rinci (v0.3.2): setiap perintah shell yang dijalankan
        // aplikasi dicatat perintah + kode exit + outputnya ke tab Log,
        // supaya error terlihat apa adanya, bukan cuma ringkasan.
        val appContext = applicationContext
        ShizukuCtl.execLogger = { cmd, exitCode, output, durationMs ->
            OptStore.addExecLog(appContext, cmd, exitCode, output, durationMs)
        }
    }
}
