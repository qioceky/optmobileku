package id.optmobileku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.optmobileku.core.Optimizer
import kotlinx.coroutines.launch

/**
 * Seksi JARINGAN & TAMPILAN (v0.3) di tab Optimasi.
 * Punya state + busy sendiri; setiap aksi tetap lewat Optimizer
 * (snapshot + read-back + log). Aksi wm wajib konfirmasi karena
 * tampilannya langsung berubah.
 */
@Composable
fun NetDisplaySection(
    ready: Boolean,
    onAfterAction: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<Optimizer.NetDisplayState?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Optimizer.TweakResult>>(emptyList()) }
    var dnsHost by remember { mutableStateOf("") }
    var wmConfirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    fun reload() {
        if (!ready) return
        scope.launch {
            state = Optimizer.readNetDisplayState()
            dnsHost = state?.dnsHost ?: dnsHost
        }
    }

    LaunchedEffect(ready) { if (ready) reload() }

    fun doAction(name: String, block: suspend () -> List<Optimizer.TweakResult>) {
        if (busy != null) return
        busy = name
        scope.launch {
            results = runCatching { block() }.getOrElse {
                listOf(Optimizer.TweakResult(name, false, "Error: ${it.message}"))
            }
            busy = null
            state = if (ready) Optimizer.readNetDisplayState() else null
            onAfterAction()
        }
    }

    val enabled = ready && busy == null
    val s = state

    // ---------------- JARINGAN ----------------
    Spacer(Modifier.height(14.dp))
    Text("JARINGAN", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))

    NeoCard {
        Text("DNS Pribadi", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Mode saat ini: ${dnsModeLabel(s?.dnsMode)}${s?.dnsHost?.let { " • $it" } ?: ""}",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SegButton("Mati", s?.dnsMode == "off", enabled, Modifier.weight(1f)) {
                doAction("DNS mode Mati") { listOf(Optimizer.setPrivateDnsMode(context, "off", "Mati")) }
            }
            SegButton("Otomatis", s?.dnsMode == null || s?.dnsMode == "opportunistic", enabled, Modifier.weight(1f)) {
                doAction("DNS mode Otomatis") { listOf(Optimizer.setPrivateDnsMode(context, "opportunistic", "Otomatis")) }
            }
            SegButton("Hostname", s?.dnsMode == "hostname", enabled, Modifier.weight(1f)) {
                doAction("DNS mode Hostname") { listOf(Optimizer.setPrivateDnsMode(context, "hostname", "Hostname")) }
            }
        }
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(
            value = dnsHost,
            onValueChange = { dnsHost = it },
            label = { Text("Hostname DNS (mis. dns.google / one.one.one.one)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        NeoButton("Terapkan Hostname DNS", fill = Color.White, enabled = enabled && dnsHost.isNotBlank()) {
            doAction("DNS hostname") { Optimizer.setPrivateDnsHost(context, dnsHost) }
        }
    }
    Spacer(Modifier.height(11.dp))

    ToggleCardV03(
        title = "Penghemat Data (semua aplikasi)",
        desc = "Restrict background: aplikasi tidak bisa pakai data di latar kecuali diizinkan per aplikasi di Pengaturan sistem.",
        on = s?.restrictBackground,
        enabled = enabled,
    ) { on -> doAction("Penghemat data") { listOf(Optimizer.setRestrictBackground(context, on)) } }
    Spacer(Modifier.height(11.dp))

    ToggleCardV03(
        title = "Scan WiFi Selalu Aktif",
        desc = "Bila OFF, hemat baterai tapi lokasi berbasis WiFi jadi kurang akurat. Bawaan Android biasanya ON.",
        on = s?.wifiScanAlways,
        enabled = enabled,
    ) { on -> doAction("Scan WiFi") { listOf(Optimizer.setWifiScanAlways(context, on)) } }

    // ---------------- TAMPILAN ----------------
    Spacer(Modifier.height(14.dp))
    Text("TAMPILAN", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
    Spacer(Modifier.height(7.dp))

    NeoCard {
        Text("Skala Font", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Ukuran teks sistem. Saat ini: ${s?.fontScale ?: "bawaan (1.0)"}",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("0.85" to "Kecil", "1" to "Normal", "1.15" to "Besar", "1.3" to "XL").forEach { (v, label) ->
                SegButton(label, s?.fontScale?.toFloatOrNull() == v.toFloatOrNull(), enabled, Modifier.weight(1f)) {
                    doAction("Skala font") { listOf(Optimizer.setFontScale(context, v)) }
                }
            }
        }
    }
    Spacer(Modifier.height(11.dp))

    NeoCard(fill = Color(0xFFFFF3C4)) {
        Text("Refresh Rate — EKSPERIMENTAL ⚠️", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Menulis DUA jalur sekaligus — generik Android (peak_refresh_rate) dan jalur " +
                "vendor Transsion/XOS (tran_refresh_mode dkk). Hasil tiap kunci dibaca-balik satu-satu di bawah.\n" +
                "Catatan terverifikasi di Infinix (XOS): setting ini menentukan PLAFON refresh. " +
                "Di tampilan umum, XOS menahan refresh di 90Hz dari pabriknya (dari Pengaturan bawaan pun sama); " +
                "120/144Hz-nya aktif di game & aplikasi terpilih. Jadi plafon 144 di sini = plafon 144 di Pengaturan bawaan.\n" +
                "Saat ini: puncak ${s?.peakHz ?: "bawaan"} • minimum ${s?.minHz ?: "bawaan"} • " +
                "mode XOS ${s?.tranRefreshMode ?: "tidak terbaca"}",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("60", "90", "120", "144").forEach { hz ->
                SegButton("${hz}Hz", s?.peakHz?.toFloatOrNull() == hz.toFloatOrNull(), enabled, Modifier.weight(1f)) {
                    doAction("Refresh rate $hz") { Optimizer.setPeakRefreshRate(context, hz) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NeoButton("Lepas Semua", fill = Color.White, enabled = enabled, modifier = Modifier.weight(1f)) {
                doAction("Refresh rate lepas") { Optimizer.clearPeakRefreshRate(context) }
            }
            NeoButton("Diagnosis ke Log", fill = Color.White, enabled = enabled, modifier = Modifier.weight(1f)) {
                doAction("Diagnosis refresh") { listOf(Optimizer.diagnoseRefreshRate(context)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        NeoButton(
            if (s?.minHz != null) "Matikan Paksa Refresh Tetap" else "Paksa Refresh Tetap (boros baterai)",
            fill = Color.White, enabled = enabled,
        ) {
            doAction("Paksa refresh tetap") {
                listOf(Optimizer.setForceConstantRefresh(context, s?.minHz == null))
            }
        }
    }
    Spacer(Modifier.height(11.dp))

    NeoCard {
        Text("Kepadatan Layar (wm density)", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Fisik: ${s?.densityPhysical ?: "?"} dpi • Override: ${s?.densityOverride ?: "tidak ada (bawaan)"}\n" +
                "Mengubah dpi mengubah ukuran tampilan seketika. Tombol Reset selalu tersedia.",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SegButton("−20 dpi", false, enabled, Modifier.weight(1f)) {
                val cur = (s?.densityOverride ?: s?.densityPhysical)?.toIntOrNull()
                if (cur != null) {
                    wmConfirm = "Kepadatan jadi ${cur - 20} dpi? Tampilan akan berubah seketika." to {
                        doAction("Kepadatan layar") { listOf(Optimizer.setWmDensity(context, cur - 20)) }
                    }
                }
            }
            SegButton("+20 dpi", false, enabled, Modifier.weight(1f)) {
                val cur = (s?.densityOverride ?: s?.densityPhysical)?.toIntOrNull()
                if (cur != null) {
                    wmConfirm = "Kepadatan jadi ${cur + 20} dpi? Tampilan akan berubah seketika." to {
                        doAction("Kepadatan layar") { listOf(Optimizer.setWmDensity(context, cur + 20)) }
                    }
                }
            }
            SegButton("Reset", false, enabled, Modifier.weight(1f)) {
                doAction("Kepadatan reset") { listOf(Optimizer.resetWmDensity(context)) }
            }
        }
    }
    Spacer(Modifier.height(11.dp))

    NeoCard {
        Text("Ukuran Layar (wm size)", fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(
            "Fisik: ${s?.sizePhysical ?: "?"} • Override: ${s?.sizeOverride ?: "tidak ada (bawaan)"}\n" +
                "Mode 90% memberi sedikit ruang ekstra & bisa membantu sebagian game. Reset untuk kembali.",
            color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SegButton("90%", false, enabled, Modifier.weight(1f)) {
                wmConfirm = "Ukuran layar jadi 90% dari fisik? Tampilan akan berubah seketika." to {
                    doAction("Ukuran layar") { listOf(Optimizer.setWmSizePercent(context, 90)) }
                }
            }
            SegButton("Reset", false, enabled, Modifier.weight(1f)) {
                doAction("Ukuran reset") { listOf(Optimizer.resetWmSize(context)) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    ResultsCard(busy = busy, results = results)

    val confirm = wmConfirm
    if (confirm != null) {
        AlertDialog(
            onDismissRequest = { wmConfirm = null },
            title = { Text("Ubah Tampilan?", fontWeight = FontWeight.Black) },
            text = { Text(confirm.first) },
            confirmButton = {
                TextButton(onClick = {
                    wmConfirm = null
                    confirm.second()
                }) { Text("Ya, Ubah", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { wmConfirm = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun SegButton(
    label: String,
    isCurrent: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) OptYellow else Color.White)
            .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, color = if (enabled) Ink else Muted)
    }
}

@Composable
private fun ToggleCardV03(
    title: String,
    desc: String,
    on: Boolean?,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    NeoCard {
        Row {
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

private fun dnsModeLabel(mode: String?): String = when (mode) {
    "off" -> "Mati"
    "hostname" -> "Hostname"
    "opportunistic" -> "Otomatis"
    null -> "Otomatis (bawaan)"
    else -> mode
}
