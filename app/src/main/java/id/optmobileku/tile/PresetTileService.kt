package id.optmobileku.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import id.optmobileku.MainActivity
import id.optmobileku.core.Optimizer
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tile Quick Settings (v0.5): ketuk tile untuk MEMUTAR preset
 * Hemat → Seimbang → Performa → Hemat…
 *
 * Jujur soal batas: tile hanya bisa menerapkan preset bila Shizuku
 * sedang jalan dan izinnya ada. Bila belum siap (atau belum ter-bind),
 * ketukan membuka aplikasi utama supaya user menyambungkan Shizuku
 * dulu — tile tidak pura-pura berhasil: subtitle-nya menampilkan
 * preset yang BENAR-BENAR tersimpan (OptStore), yang hanya berubah
 * bila applyPreset sukses penuh.
 *
 * Tambahkan tile lewat editor panel Quick Settings HP (ikon pensil →
 * cari "Preset" / Opt Mobileku).
 */
class PresetTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        when (ShizukuCtl.state(ctx)) {
            ShizukuCtl.State.READY -> cyclePreset()
            ShizukuCtl.State.NOT_BOUND -> ShizukuCtl.ensureBound(ctx) { ok, _ ->
                if (ok) cyclePreset() else openApp()
            }
            else -> openApp() // NOT_INSTALLED / NOT_RUNNING / DENIED: perlu layar aplikasi
        }
    }

    private fun cyclePreset() {
        val ctx = applicationContext
        val current = Optimizer.Preset.entries.firstOrNull { it.label == OptStore.getPreset(ctx) }
        val next = when (current) {
            Optimizer.Preset.HEMAT -> Optimizer.Preset.SEIMBANG
            Optimizer.Preset.SEIMBANG -> Optimizer.Preset.PERFORMA
            Optimizer.Preset.PERFORMA -> Optimizer.Preset.HEMAT
            null -> Optimizer.Preset.HEMAT // "Belum ada"/"Kustom" → mulai dari Hemat
        }
        scope.launch {
            runCatching { Optimizer.applyPreset(ctx, next) }
            refreshTile()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val ctx = applicationContext
        val ready = ShizukuCtl.state(ctx) == ShizukuCtl.State.READY
        tile.label = "Preset"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (ready) OptStore.getPreset(ctx) else "Shizuku belum siap"
        }
        tile.state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Minta sistem menyegarkan tile (dipanggil UI sesudah preset berubah). */
        fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, PresetTileService::class.java))
            }
        }
    }
}
