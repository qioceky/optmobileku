package id.optmobileku.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import id.optmobileku.BuildConfig
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Pengatur integrasi Shizuku: status, minta izin, dan bind ke
 * ShellService (user-service, UID shell).
 *
 * Panggilan binder (ensureBound/exec) JANGAN dari main thread.
 * Pola meniru ShizukuCtl RuangApung yang terbukti jalan di lapangan.
 */
object ShizukuCtl {

    enum class State {
        NOT_INSTALLED, // aplikasi Shizuku belum terpasang
        NOT_RUNNING,   // terpasang tapi service-nya belum jalan
        DENIED,        // izin Shizuku untuk aplikasi ini belum diberikan
        NOT_BOUND,     // izin ada, tapi user-service belum terhubung
        READY,         // siap dipakai
    }

    data class ShellResult(val output: String, val exitCode: Int) {
        val ok: Boolean get() = exitCode == 0
    }

    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 1001

    private val binderThreads = Executors.newCachedThreadPool()

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var bound = false

    fun isShizukuAppInstalled(context: Context): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        }.getOrDefault(false)

    fun state(context: Context): State {
        if (!isShizukuAppInstalled(context)) return State.NOT_INSTALLED
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return State.NOT_RUNNING
        val perm = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        if (perm != PackageManager.PERMISSION_GRANTED) return State.DENIED
        if (!bound || service == null) return State.NOT_BOUND
        return State.READY
    }

    fun isReady(): Boolean = bound && service != null

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    fun openInStore(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PKG"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** Pastikan user-service ter-bind. Callback tiba di thread binder. */
    fun ensureBound(context: Context, cb: (ok: Boolean, err: String?) -> Unit) {
        binderThreads.execute {
            try {
                if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                    cb(false, "Service Shizuku belum jalan")
                    return@execute
                }
                val perm = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
                if (perm != PackageManager.PERMISSION_GRANTED) {
                    cb(false, "Izin Shizuku belum diberikan")
                    return@execute
                }
                if (bound && service != null) {
                    cb(true, null)
                    return@execute
                }
                val latch = CountDownLatch(1)
                val ref = AtomicReference<IBinder?>()
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        ref.set(binder)
                        latch.countDown()
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        service = null
                        bound = false
                    }
                }
                val args = UserServiceArgs(
                    ComponentName(context.packageName, ShellService::class.java.name),
                )
                    .version(BuildConfig.VERSION_CODE)
                    .processNameSuffix("shell")
                    .daemon(false)
                    .debuggable(BuildConfig.DEBUG)
                Shizuku.bindUserService(args, conn)
                if (!latch.await(20, TimeUnit.SECONDS)) {
                    cb(false, "Timeout menghubungkan service Shizuku")
                    return@execute
                }
                val binder = ref.get()
                if (binder == null) {
                    cb(false, "Binder Shizuku null")
                    return@execute
                }
                val svc = IShellService.Stub.asInterface(binder)
                    ?: run { cb(false, "Binder Shizuku null"); return@execute }
                service = svc
                bound = true
                cb(true, null)
            } catch (e: Exception) {
                cb(false, e.message ?: e.toString())
            }
        }
    }

    /**
     * Hook log eksekusi (v0.3.2): dipanggil untuk SETIAP perintah yang
     * lewat execBlocking — (perintah, kode exit, output mentah, durasi ms).
     * Kode exit null = perintah tidak sempat jalan (service belum siap).
     * Diisi OptApplication dengan penulis ke OptStore; ShizukuCtl sendiri
     * tidak tahu-menahu soal penyimpanan.
     */
    @Volatile
    var execLogger: ((cmd: String, exitCode: Int?, output: String, durationMs: Long) -> Unit)? = null

    private fun logExec(cmd: String, exitCode: Int?, output: String, startedAt: Long) {
        runCatching {
            execLogger?.invoke(cmd, exitCode, output, System.currentTimeMillis() - startedAt)
        }
    }

    /**
     * Jalankan perintah shell dan tunggu hasilnya. HARUS dipanggil dari
     * background thread. Null bila service belum siap.
     */
    fun execBlocking(cmd: String): ShellResult? {
        val startedAt = System.currentTimeMillis()
        val s = service
        if (s == null || !bound) {
            logExec(cmd, null, "", startedAt)
            return null
        }
        val raw = runCatching { s.exec(cmd) }.getOrElse {
            val res = ShellResult("ERROR: ${it.message}", -1)
            logExec(cmd, res.exitCode, res.output, startedAt)
            return res
        }
        val marker = "<<<EXIT:"
        val idx = raw.lastIndexOf(marker)
        if (idx < 0) {
            val res = ShellResult(raw.trim(), -1)
            logExec(cmd, res.exitCode, res.output, startedAt)
            return res
        }
        val codeStr = raw.substring(idx + marker.length).removeSuffix(">>>").trim()
        val output = raw.substring(0, idx).trim()
        val res = ShellResult(output, codeStr.toIntOrNull() ?: -1)
        logExec(cmd, res.exitCode, res.output, startedAt)
        return res
    }
}
