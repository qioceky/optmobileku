package id.optmobileku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sesi terminal disimpan di luar komposisi supaya isi konsol tidak
 * hilang saat user berpindah tab (tab dirender kondisional di App).
 */
private object TermSession {
    val lines = mutableStateListOf<String>()
    var welcomed = false
}

/**
 * Tab Terminal (v0.7): konsol shell lewat Shizuku (uid shell 2000,
 * BUKAN root) memakai jalur eksekusi yang sama dengan seluruh fitur
 * aplikasi — jadi semua perintah ikut tercatat di Log Eksekusi.
 *
 * Batas jujur: perintah berjalan satu kali lalu selesai (bukan sesi
 * interaktif); perintah yang tidak kunjung selesai dipotong service
 * di 120 detik. Pakai bentuk sekali-jalan: `top -n 1`, `ping -c 3`,
 * `logcat -d`.
 */
@Composable
fun TerminalTab(ready: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = TermSession.lines

    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(OptStore.readTermHistory(context)) }
    var histIdx by remember { mutableStateOf(-1) }
    var pendingDanger by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    if (!TermSession.welcomed) {
        TermSession.welcomed = true
        lines += "Terminal Opt Mobileku — shell via Shizuku (uid 2000, bukan root)."
        lines += "Semua perintah tercatat di Log Eksekusi (tab Log)."
        lines += "Contoh: getprop ro.product.model • dumpsys battery • top -n 1"
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    fun pushOutput(text: String) {
        var t = text
        if (t.length > 20_000) {
            t = t.take(20_000) + "\n…(output dipotong di tampilan — total ${text.length} karakter)"
        }
        if (t.isNotBlank()) lines.addAll(t.lines())
        while (lines.size > 500) lines.removeAt(0)
    }

    fun execute(cmdRaw: String, confirmed: Boolean = false) {
        val cmd = cmdRaw.trim()
        if (cmd.isEmpty() || running) return
        if (!confirmed && DANGER_PATTERNS.any { cmd.lowercase().contains(it) }) {
            pendingDanger = cmd
            return
        }
        running = true
        input = ""
        histIdx = -1
        lines += "$ $cmd"
        OptStore.addTermHistory(context, cmd)
        history = OptStore.readTermHistory(context)
        scope.launch {
            val r = withContext(Dispatchers.IO) { ShizukuCtl.execBlocking(cmd) }
            if (r == null) {
                lines += "(Shizuku belum siap — perintah tidak jalan)"
            } else {
                pushOutput(r.output)
                lines += "[exit ${r.exitCode}]"
            }
            running = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        NeoCard {
            Text("💻 TERMINAL SHELL", fontWeight = FontWeight.Black, fontSize = 14.5.sp)
            Text(
                "Jalankan perintah shell Android lewat Shizuku. Bukan sesi interaktif: perintah " +
                    "yang menggantung dipotong di 120 detik — pakai bentuk sekali-jalan " +
                    "(top -n 1, ping -c 3, logcat -d). Perintah destruktif meminta konfirmasi dulu.",
                color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (!ready) {
            Spacer(Modifier.height(9.dp))
            NeoCard(fill = Color(0xFFFFF3C4)) {
                Text(
                    "Shizuku belum siap — terminal hanya bisa dipakai setelah Shizuku tersambung (tab Beranda).",
                    fontSize = 12.5.sp, fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(9.dp))

        // Perintah cepat: ketuk untuk mengisi kotak input.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            QUICK_COMMANDS.forEach { (label, cmd) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.White)
                        .border(2.dp, Ink, RoundedCornerShape(99.dp))
                        .clickable { input = cmd }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Spacer(Modifier.height(9.dp))

        // Konsol
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14181D))
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { _, line ->
                    val color = when {
                        line.startsWith("$ ") -> OptYellow
                        line.startsWith("[exit") -> Color(0xFF9AA5B1)
                        line.startsWith("(") -> Color(0xFFFFB3B3)
                        else -> Color(0xFFE8ECF1)
                    }
                    Text(
                        line,
                        color = color,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (line.startsWith("$ ")) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))

        // Baris input + navigasi riwayat
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeoButton("▲", modifier = Modifier.width(46.dp), fill = Color.White, enabled = history.isNotEmpty() && !running) {
                if (history.isNotEmpty()) {
                    histIdx = if (histIdx == -1) history.lastIndex else maxOf(0, histIdx - 1)
                    input = history[histIdx]
                }
            }
            NeoButton("▼", modifier = Modifier.width(46.dp), fill = Color.White, enabled = history.isNotEmpty() && !running) {
                if (history.isNotEmpty() && histIdx != -1) {
                    histIdx = minOf(history.lastIndex, histIdx + 1)
                    input = history[histIdx]
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Perintah shell…", fontSize = 13.sp) },
                singleLine = true,
                enabled = ready && !running,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { execute(input) }),
                modifier = Modifier.weight(1f),
            )
            NeoButton(
                if (running) "…" else "Kirim",
                modifier = Modifier.width(84.dp),
                enabled = ready && !running && input.isNotBlank(),
            ) { execute(input) }
        }
        Spacer(Modifier.height(7.dp))
        Row {
            TextButton(onClick = { lines.clear() }) { Text("Bersihkan Konsol", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text(
                if (running) "Menjalankan…" else "${history.size} perintah di riwayat",
                color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)
            )
        }
    }

    val danger = pendingDanger
    if (danger != null) {
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            title = { Text("Perintah berpotensi destruktif", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "Perintah ini termasuk pola yang bisa menghapus/menonaktifkan sesuatu:\n\n$danger\n\nJalankan juga?",
                    fontFamily = FontFamily.Monospace, fontSize = 12.5.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDanger = null
                    execute(danger, confirmed = true)
                }) { Text("Ya, Jalankan", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { pendingDanger = null }) { Text("Batal") } }
        )
    }
}

private val DANGER_PATTERNS = listOf(
    "rm -rf", "rm -fr", "pm uninstall", "pm clear", "pm disable",
    "settings delete", "reboot", "wipe", "mkfs", "dd if=", "powerctl",
)

private val QUICK_COMMANDS = listOf(
    "Info perangkat" to "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release",
    "Baterai" to "dumpsys battery",
    "Proses teratas" to "top -n 1 -m 15",
    "Penyimpanan" to "df -h /data",
    "Aplikasi user" to "pm list packages -3",
    "Refresh rate" to "settings get system peak_refresh_rate; settings get system tran_refresh_mode",
    "Jaringan" to "ip addr show wlan0",
    "Log sistem (akhir)" to "logcat -d -t 100",
)
