package id.optmobileku

import android.app.Application
import id.optmobileku.data.CrashLogger

class OptApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
