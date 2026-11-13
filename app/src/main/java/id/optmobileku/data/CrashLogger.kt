package id.optmobileku.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Penangkap crash lokal: menyimpan stack trace terakhir agar bisa disalin user. */
object CrashLogger {

    private const val FILE_NAME = "last_crash.txt"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        val appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(appContext.filesDir, FILE_NAME)
                    .writeText("Opt Mobileku crash $time (thread: ${thread.name})\n\n$sw")
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }

    fun clear(context: Context) {
        try { File(context.filesDir, FILE_NAME).delete() } catch (_: Exception) {}
    }
}
