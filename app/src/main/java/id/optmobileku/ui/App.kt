package id.optmobileku.ui

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.optmobileku.BuildConfig
import id.optmobileku.core.Optimizer
import id.optmobileku.data.CrashLogger
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Composable
fun OptMobilekuApp() {
    OptMobilekuTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var tab by remember { mutableStateOf(0) }
        var shizukuState by remember { mutableStateOf(ShizukuCtl.state(context)) }
        var current by remember { mutableStateOf<Optimizer.CurrentState?>(null) }
        var presetLabel by remember { mutableStateOf(OptStore.getPreset(context)) }
        var hasSnapshot by remember { mutableStateOf(OptStore.hasSnapshot(context)) }
        var busy by remember { mutableStateOf<String?>(null) }
        var results by remember { mutableStateOf<List<Optimizer.TweakResult>>(emptyList()) }
        var logLines by remember { mutableStateOf(OptStore.readLog(context)) }
        var execLines by remember { mutableStateOf(OptStore.readExecLog(context)) }
        var showRestoreConfirm by remember { mutableStateOf(false) }
        var showDexoptConfirm by remember { mutableStateOf(false) }

        fun reloadLocal() {
            presetLabel = OptStore.getPreset(context)
            hasSnapshot = OptStore.hasSnapshot(context)
            logLines = OptStore.readLog(context)
            execLines = OptStore.readExecLog(context)
        }

        fun loadCurrent() {
            if (!ShizukuCtl.isReady()) return
            scope.launch {
                current = Optimizer.readCurrentState()
                reloadLocal()
            }
        }

        fun refreshShizuku() {
            shizukuState = ShizukuCtl.state(context)
            when (shizukuState) {
                ShizukuCtl.State.NOT_BOUND -> {
                    ShizukuCtl.ensureBound(context) { ok, _ ->
                        scope.launch {
                            shizukuState = ShizukuCtl.state(context)
                            if (ok) loadCurrent()
                        }
                    }
                }
                ShizukuCtl.State.READY -> loadCurrent()
                else -> Unit
            }
        }

        // Reaksi hasil dialog izin Shizuku.
        DisposableEffect(Unit) {
            val listener = Shizuku.OnRequestPermissionResultListener { _, _ ->
                refreshShizuku()
            }
            runCatching { Shizuku.addRequestPermissionResultListener(listener) }
            onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
        }

        LaunchedEffect(Unit) { refreshShizuku() }

        fun doAction(name: String, block: suspend () -> List<Optimizer.TweakResult>) {
            if (busy != null) return
            busy = name
            scope.launch {
                results = runCatching { block() }.getOrElse {
                    listOf(Optimizer.TweakResult(name, false, "Error: ${it.message}"))
                }
                busy = null
                if (ShizukuCtl.isReady()) current = Optimizer.readCurrentState()
                reloadLocal()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Paper)
        ) {
            val scrollState = rememberScrollState()
            Column(
                Modifier
                    .weight(1f)
                    .then(if (tab != 2) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(OptYellow)
                            .border(2.5.dp, Ink, RoundedCornerShape(9.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) { Text("⚡", fontSize = 18.sp) }
                    Text(
                        " Opt Mobileku",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("v${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))

                when (tab) {
                    0 -> BerandaTab(
                        context = context,
                        shizukuState = shizukuState,
                        current = current,
                        presetLabel = presetLabel,
                        hasSnapshot = hasSnapshot,
                        busy = busy,
                        results = results,
                        onRefreshShizuku = { refreshShizuku() },
                        onRequestPermission = { ShizukuCtl.requestPermission() },
                        onInstallShizuku = { ShizukuCtl.openInStore(context) },
                        onGoInfo = { tab = 4 },
                        onPreset = { preset ->
                            doAction("Preset ${preset.label}") { Optimizer.applyPreset(context, preset) }
                        },
                        onRestore = { showRestoreConfirm = true },
                    )
                    1 -> OptimasiTab(
                        ready = shizukuState == ShizukuCtl.State.READY,
                        current = current,
                        busy = busy,
                        results = results,
                        onAnim = { scale ->
                            doAction("Skala animasi") {
                                Optimizer.setAnimationScale(context, scale).also {
                                    if (it.all { r -> r.success }) OptStore.setPreset(context, "Kustom")
                                }
                            }
                        },
                        onPowerSaver = { on ->
                            doAction("Penghemat baterai") {
                                listOf(Optimizer.setPowerSaver(context, on)).also {
                                    if (it.all { r -> r.success }) OptStore.setPreset(context, "Kustom")
                                }
                            }
                        },
                        onAdaptive = { on ->
                            doAction("Baterai adaptif") {
                                listOf(Optimizer.setAdaptiveBattery(context, on)).also {
                                    if (it.all { r -> r.success }) OptStore.setPreset(context, "Kustom")
                                }
                            }
                        },
                        onTrim = {
                            doAction("Pangkas cache") { listOf(Optimizer.trimCaches(context)) }
                        },
                        onDexopt = { showDexoptConfirm = true },
                        onAfterAction = { reloadLocal() },
                    )
                    2 -> AppsTab(
                        ready = shizukuState == ShizukuCtl.State.READY,
                        onAfterAction = { reloadLocal() },
                    )
                    3 -> LogTab(
                        logLines = logLines,
                        execLines = execLines,
                        onCopyLog = {
                            val combined = buildString {
                                append("=== LOG PERUBAHAN ===\n")
                                append(logLines.joinToString("\n"))
                                append("\n\n=== LOG EKSEKUSI (perintah + exit + output) ===\n")
                                append(execLines.joinToString("\n"))
                            }
                            copyToClipboard(context, "optmobileku-log", combined)
                        },
                        onClearLog = {
                            OptStore.clearLog(context)
                            reloadLocal()
                        },
                        onCopyCrash = {
                            CrashLogger.read(context)?.let { copyToClipboard(context, "optmobileku-crash", it) }
                        },
                        hasCrash = CrashLogger.read(context) != null,
                    )
                    else -> InfoTab()
                }
            }

            // Tab bar bawah
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Paper)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf("Beranda", "Optimasi", "Aplikasi", "Log", "Info")
                tabs.forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (tab == i) OptYellow else Color.White)
                            .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
                            .clickable { tab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (showRestoreConfirm) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = false },
                title = { Text("Pulihkan Semua?", fontWeight = FontWeight.Black) },
                text = {
                    Text("Semua setting yang pernah diubah Opt Mobileku akan dikembalikan ke nilai asalnya (dari snapshot). Preset aktif direset.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirm = false
                        doAction("Pulihkan Semua") { Optimizer.restoreAll(context) }
                    }) { Text("Ya, Pulihkan", fontWeight = FontWeight.Black) }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = false }) { Text("Batal") }
                }
            )
        }

        if (showDexoptConfirm) {
            AlertDialog(
                onDismissRequest = { showDexoptConfirm = false },
                title = { Text("Optimasi Aplikasi (ART)?", fontWeight = FontWeight.Black) },
                text = {
                    Text("Android akan mengompilasi ulang aplikasi di latar. HP bisa terasa sibuk/hangat selama beberapa menit — itu normal dan berhenti sendiri. Lanjutkan?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDexoptConfirm = false
                        doAction("Optimasi ART") { listOf(Optimizer.runBgDexopt(context)) }
                    }) { Text("Jalankan", fontWeight = FontWeight.Black) }
                },
                dismissButton = {
                    TextButton(onClick = { showDexoptConfirm = false }) { Text("Batal") }
                }
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

// ---------------- Beranda ----------------

@Composable
private fun BerandaTab(
    context: Context,
    shizukuState: ShizukuCtl.State,
    current: Optimizer.CurrentState?,
    presetLabel: String,
    hasSnapshot: Boolean,
    busy: String?,
    results: List<Optimizer.TweakResult>,
    onRefreshShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onInstallShizuku: () -> Unit,
    onGoInfo: () -> Unit,
    onPreset: (Optimizer.Preset) -> Unit,
    onRestore: () -> Unit,
) {
    // Kartu Shizuku
    val (dotOn, title, desc) = when (shizukuState) {
        ShizukuCtl.State.READY -> Triple(true, "Shizuku Siap ✓", "Optimasi bisa dijalankan. Semua penulisan diverifikasi baca-balik.")
        ShizukuCtl.State.NOT_INSTALLED -> Triple(false, "Shizuku Belum Terpasang", "Opt Mobileku butuh aplikasi Shizuku untuk optimasi tanpa root.")
        ShizukuCtl.State.NOT_RUNNING -> Triple(false, "Shizuku Belum Jalan", "Aktifkan Shizuku dulu lewat Wireless Debugging (lihat tab Info), lalu kembali ke sini.")
        ShizukuCtl.State.DENIED -> Triple(null, "Izin Shizuku Belum Diberikan", "Ketuk tombol di bawah dan setujui dialog izin Shizuku.")
        ShizukuCtl.State.NOT_BOUND -> Triple(null, "Menghubungkan ke Shizuku…", "Sedang menyambungkan service shell. Bila lama, ketuk Segarkan.")
    }
    NeoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(dotOn)
            Text("  $title", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Text(desc, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(9.dp))
        when (shizukuState) {
            ShizukuCtl.State.NOT_INSTALLED -> NeoButton("Pasang Shizuku", fill = OptYellow) { onInstallShizuku() }
            ShizukuCtl.State.NOT_RUNNING -> NeoButton("Lihat Panduan", fill = Color.White) { onGoInfo() }
            ShizukuCtl.State.DENIED -> NeoButton("Minta Izin Shizuku", fill = OptYellow) { onRequestPermission() }
            else -> NeoButton("Segarkan Status", fill = Color.White) { onRefreshShizuku() }
        }
    }
    Spacer(Modifier.height(11.dp))

    // Kartu perangkat
    NeoCard {
        Text(deviceTitle(), fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(deviceInfo(context), color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
        if (current != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Animasi saat ini: ${current.animationScale ?: "bawaan (1.0)"} • " +
                    "Penghemat: ${boolText(current.powerSaverOn)} • Adaptif: ${boolText(current.adaptiveBatteryOn)}",
                fontSize = 12.5.sp, fontWeight = FontWeight.Bold
            )
        } else if (shizukuState == ShizukuCtl.State.READY) {
            Text("Membaca setting saat ini…", color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Spacer(Modifier.height(11.dp))

    Text("PRESET 1-TAP", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Text(
        "Preset aktif: $presetLabel",
        color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)
    )
    val ready = shizukuState == ShizukuCtl.State.READY
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PresetButton("🔋\nHemat", Modifier.weight(1f), ready && busy == null) { onPreset(Optimizer.Preset.HEMAT) }
        PresetButton("⚖️\nSeimbang", Modifier.weight(1f), ready && busy == null) { onPreset(Optimizer.Preset.SEIMBANG) }
        PresetButton("🎮\nPerforma", Modifier.weight(1f), ready && busy == null) { onPreset(Optimizer.Preset.PERFORMA) }
    }
    if (!ready) {
        Text(
            "Preset aktif setelah Shizuku siap. Tanpa Shizuku, aplikasi ini mode lihat saja.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }
    Spacer(Modifier.height(12.dp))

    ResultsCard(busy = busy, results = results)

    NeoButton(
        text = if (hasSnapshot) "↩️  Pulihkan Semua ke Awal" else "Belum Ada yang Diubah",
        fill = if (hasSnapshot) OptRed else Color.White,
        textColor = if (hasSnapshot) Color.White else Muted,
        enabled = hasSnapshot && busy == null,
    ) { onRestore() }
    if (hasSnapshot) {
        Text(
            "Snapshot nilai asal tersimpan — pemulihan selalu tersedia.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun PresetButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier = modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(start = 3.dp, top = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) Color.White else Color(0xFFEFEEE8))
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
                .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontWeight = FontWeight.Black, fontSize = 13.sp, color = if (enabled) Ink else Muted)
        }
    }
}

@Composable
fun ResultsCard(busy: String?, results: List<Optimizer.TweakResult>) {
    if (busy != null) {
        NeoCard(fill = OptBlue) {
            Text("⏳ Sedang menjalankan: $busy…", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }
        Spacer(Modifier.height(11.dp))
        return
    }
    if (results.isEmpty()) return
    NeoCard(fill = if (results.all { it.success }) Color(0xFFDFF7E9) else Color(0xFFFFE9E9)) {
        Text("Hasil terakhir:", fontWeight = FontWeight.Black, fontSize = 13.5.sp)
        results.forEach { r ->
            Text(
                "${if (r.success) "✓" else "✕"} ${r.name} — ${r.detail}",
                fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
    Spacer(Modifier.height(11.dp))
}

// ---------------- Optimasi ----------------

@Composable
private fun OptimasiTab(
    ready: Boolean,
    current: Optimizer.CurrentState?,
    busy: String?,
    results: List<Optimizer.TweakResult>,
    onAnim: (String) -> Unit,
    onPowerSaver: (Boolean) -> Unit,
    onAdaptive: (Boolean) -> Unit,
    onTrim: () -> Unit,
    onDexopt: () -> Unit,
    onAfterAction: () -> Unit,
) {
    if (!ready) {
        NeoCard(fill = Color(0xFFFFF3C4)) {
            Text("Mode lihat saja", fontWeight = FontWeight.Black, fontSize = 15.sp)
            Text(
                "Aktifkan Shizuku dulu (tab Beranda / Info) — tanpa Shizuku, tombol optimasi sengaja dimatikan daripada pura-pura jalan.",
                color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(11.dp))
    }

    Text("KELANCARAN", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    NeoCard {
        Text("Skala Animasi", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Jendela + transisi + animator sekaligus. Saat ini: ${current?.animationScale ?: "bawaan (1.0)"}",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0" to "0 (mati)", "0.5" to "0.5x", "1" to "1x").forEach { (value, label) ->
                val isCurrent = current?.animationScale?.toFloatOrNull() == value.toFloatOrNull()
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCurrent) OptYellow else Color.White)
                        .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
                        .then(if (ready && busy == null) Modifier.clickable { onAnim(value) } else Modifier)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            }
        }
    }
    Spacer(Modifier.height(11.dp))

    NeoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Optimasi Aplikasi (ART)", fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(
                    "Kompilasi ulang semua aplikasi pakai profil (bg-dexopt) — jalan di latar, HP sibuk beberapa menit.",
                    color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        NeoButton("▶ Jalankan bg-dexopt", enabled = ready && busy == null) { onDexopt() }
    }
    Spacer(Modifier.height(11.dp))

    NeoCard {
        Text("Pangkas Cache", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Bersihkan cache SEMUA aplikasi sekaligus. Hanya cache — data aplikasi tidak disentuh.",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        NeoButton("Bersihkan Cache", fill = Color.White, enabled = ready && busy == null) { onTrim() }
    }
    Spacer(Modifier.height(12.dp))

    Text("BATERAI", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    ToggleCard(
        title = "Penghemat Baterai",
        desc = "Mode hemat daya sistem (cmd power set-mode).",
        on = current?.powerSaverOn,
        enabled = ready && busy == null,
    ) { onPowerSaver(it) }
    Spacer(Modifier.height(11.dp))
    ToggleCard(
        title = "Baterai Adaptif",
        desc = "Android membatasi aplikasi yang jarang dipakai secara otomatis.",
        on = current?.adaptiveBatteryOn,
        enabled = ready && busy == null,
    ) { onAdaptive(it) }

    NetDisplaySection(ready = ready, onAfterAction = onAfterAction)

    ResultsCard(busy = busy, results = results)
}

@Composable
private fun ToggleCard(
    title: String,
    desc: String,
    on: Boolean?,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    NeoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(desc, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
                Text(
                    when (on) {
                        true -> "Status: ON ✓"
                        false -> "Status: OFF"
                        null -> "Status: belum terbaca"
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Saklar neobrutal sederhana
            val target = !(on ?: false)
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (on == true) OptGreen else Color.White)
                    .border(2.5.dp, Ink, RoundedCornerShape(99.dp))
                    .then(if (enabled) Modifier.clickable { onChange(target) } else Modifier)
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(if (on == true) "ON" else "OFF", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
            }
        }
    }
}

// ---------------- Log ----------------

@Composable
private fun LogTab(
    logLines: List<String>,
    execLines: List<String>,
    onCopyLog: () -> Unit,
    onClearLog: () -> Unit,
    onCopyCrash: () -> Unit,
    hasCrash: Boolean,
) {
    val anyLog = logLines.isNotEmpty() || execLines.isNotEmpty()
    Text("LOG PERUBAHAN", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    if (logLines.isEmpty()) {
        NeoCard {
            Text("Belum ada perubahan tercatat.", color = Muted, fontSize = 13.sp)
        }
    } else {
        NeoCard {
            logLines.asReversed().forEach { line ->
                Text(line, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("LOG EKSEKUSI — PERINTAH + OUTPUT", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    NeoCard {
        Text(
            "Setiap perintah shell yang dijalankan aplikasi, apa adanya: perintahnya ($), " +
                "kode exit (0 = sukses), durasi, dan output mentahnya. Output sangat panjang " +
                "dipotong dengan penanda. exit=TIDAK-JALAN berarti Shizuku belum siap saat itu.",
            color = Muted, fontSize = 12.5.sp
        )
    }
    Spacer(Modifier.height(9.dp))
    if (execLines.isEmpty()) {
        NeoCard {
            Text("Belum ada eksekusi tercatat.", color = Muted, fontSize = 13.sp)
        }
    } else {
        NeoCard {
            execLines.asReversed().forEach { line ->
                Text(
                    line,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(11.dp))
    NeoButton("📋 Salin Log (Perubahan + Eksekusi)", fill = Color.White, enabled = anyLog) { onCopyLog() }
    Spacer(Modifier.height(9.dp))
    NeoButton("🗑️ Hapus Log", fill = Color.White, enabled = anyLog) { onClearLog() }
    if (hasCrash) {
        Spacer(Modifier.height(9.dp))
        NeoButton("📋 Salin Log Crash Terakhir", fill = OptRed, textColor = Color.White) { onCopyCrash() }
    }
}

// ---------------- Info ----------------

@Composable
private fun InfoTab() {
    Text("PANDUAN SHIZUKU", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    NeoCard {
        Text(
            "1. Pasang aplikasi Shizuku (Play Store / GitHub RikkaApps).\n" +
                "2. Buka Shizuku → \"Start via Wireless debugging\" → aktifkan Opsi Pengembang + Debugging nirkabel, lalu \"Pairing\" dan masukkan kode yang muncul.\n" +
                "3. Ketuk \"Start\" di Shizuku sampai statusnya running.\n" +
                "4. Kembali ke Opt Mobileku (tab Beranda) → Minta Izin → setujui.\n" +
                "5. Setelah HP restart, ulangi langkah 2–3 (Shizuku tanpa root memang harus distart ulang tiap restart).",
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(11.dp))
    Text("BATAS JUJUR", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    NeoCard {
        Text(
            "• Shizuku = hak shell/ADB, BUKAN root. Aplikasi ini tidak bisa (dan tidak mengklaim bisa) mengubah governor CPU/GPU, kernel, atau \"FPS unlock\".\n" +
                "• Tidak ada \"RAM booster\" yang menutup aplikasi berulang-ulang — itu justru membuat HP lebih lambat.\n" +
                "• Setiap penulisan setting dibaca-balik. Bila HP kamu mengabaikan sebuah setting, aplikasi ini bilang terus terang, bukan pura-pura berhasil.\n" +
                "• Semua perubahan bisa dibatalkan lewat tombol Pulihkan Semua di Beranda.",
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(11.dp))
    Text("ROADMAP", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))
    NeoCard {
        Text(
            "v0.1: fondasi Shizuku + preset + animasi/baterai/ART/cache + snapshot & pulihkan.\n" +
                "v0.2 (ini): manajemen aplikasi & bloatware — tab Aplikasi (bucket standby, Doze, background, suspend, nonaktifkan/copot reversibel + daftar lindung).\n" +
                "v0.3.2 (ini): Log Eksekusi rinci — setiap perintah shell tercatat dengan kode exit, durasi, dan output mentahnya di tab Log (Salin Log kini menggabungkan Log Perubahan + Log Eksekusi).\n" +
                "v0.3.1: perbaikan refresh rate untuk HP Transsion (Infinix/Tecno) — menulis jalur generik + kunci vendor tran_*, tombol 144Hz, dan Diagnosis Refresh Rate ke tab Log.\n" +
                "v0.3: tampilan & jaringan — DNS pribadi, penghemat data, scan WiFi, font, refresh rate eksperimental, wm density/size + reset.\n" +
                "v0.4: profil per aplikasi (Mode Game / Mode Tidur).",
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(11.dp))
    NeoCard(fill = OptBlue) {
        Text(
            "Opt Mobileku v${BuildConfig.VERSION_NAME} • Tanpa iklan, tanpa internet, tanpa analytics. Semua data hanya di HP ini.",
            fontSize = 12.5.sp, fontWeight = FontWeight.Bold
        )
    }
}

// ---------------- Info perangkat ----------------

private fun deviceTitle(): String {
    val mfr = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    return "$mfr ${Build.MODEL}"
}

private fun deviceInfo(context: Context): String {
    val sb = StringBuilder()
    sb.append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        sb.append("\nRAM total: ${formatBytes(mi.totalMem)}")
    }
    runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        sb.append("\nPenyimpanan: ${formatBytes(free)} bebas dari ${formatBytes(total)}")
    }
    runCatching {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            sb.append("\nBaterai: ${level * 100 / scale}%")
        }
    }
    return sb.toString()
}

private fun boolText(v: Boolean?): String = when (v) {
    true -> "ON"
    false -> "OFF"
    null -> "?"
}
