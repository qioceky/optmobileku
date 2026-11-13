package id.optmobileku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.optmobileku.core.HiddenScanner
import id.optmobileku.core.Optimizer
import kotlinx.coroutines.launch

/**
 * Tab Tersembunyi (v0.6): pemindai fitur tersembunyi.
 * - Kunci setting tersembunyi dari 3 namespace (pola refresh/gpu/game/
 *   vendor…), bisa dicari; nilai angka/boolean bisa diubah dengan
 *   snapshot + baca-balik (ikut Pulihkan Semua).
 * - Menu tersembunyi: kode rahasia (broadcast via shell) + aktivitas
 *   exported berpola rahasia yang bisa dibuka langsung.
 */
@Composable
fun HiddenTab(
    ready: Boolean,
    onCopyText: (String) -> Unit,
    onAfterAction: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keys by remember { mutableStateOf<List<HiddenScanner.HiddenKey>?>(null) }
    var acts by remember { mutableStateOf<List<HiddenScanner.HiddenActivity>?>(null) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Optimizer.TweakResult>>(emptyList()) }
    var detailTarget by remember { mutableStateOf<HiddenScanner.HiddenKey?>(null) }
    var editValue by remember { mutableStateOf("") }

    fun refreshKeys() {
        scope.launch { keys = runCatching { HiddenScanner.scanKeys() }.getOrDefault(emptyList()) }
    }
    fun refreshActs() {
        scope.launch { acts = runCatching { HiddenScanner.scanActivities(context) }.getOrDefault(emptyList()) }
    }

    LaunchedEffect(Unit) { refreshActs() }
    LaunchedEffect(ready) { if (ready) refreshKeys() }

    fun runAction(name: String, after: (() -> Unit)? = null, block: suspend () -> List<Optimizer.TweakResult>) {
        if (busy != null) return
        busy = name
        scope.launch {
            results = runCatching { block() }.getOrElse {
                listOf(Optimizer.TweakResult(name, false, "Error: ${it.message}"))
            }
            busy = null
            after?.invoke()
            onAfterAction()
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            NeoCard {
                Text("🔍 FITUR TERSEMBYI", fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(
                    "Pemindai dua arah: kunci setting yang tidak tampil di Pengaturan, dan menu/layar " +
                        "rahasia aplikasi. Mengubah kunci selalu disnapshot dulu (ikut Pulihkan Semua) " +
                        "dan dibaca-balik — bila sistem menimpa lagi, hasilnya akan berkata begitu.",
                    color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (!ready) {
            item {
                NeoCard(fill = Color(0xFFFFF3C4)) {
                    Text(
                        "Mode lihat saja — pindai kunci & kode rahasia butuh Shizuku aktif. " +
                            "Pindai menu aplikasi tetap jalan.",
                        fontSize = 12.5.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (busy != null || results.isNotEmpty()) {
            item { ResultsCard(busy = busy, results = results) }
        }

        // ---------- Kunci setting ----------
        item {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("KUNCI SETTING TERSEMBYI", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
                    Text(
                        "Dari system/secure/global, disaring pola menarik (refresh, GPU, game, vendor tran_*…).",
                        color = Muted, fontSize = 12.sp
                    )
                }
                NeoButton(
                    if (keys == null) "Pindai" else "Pindai Ulang",
                    modifier = Modifier.width(118.dp),
                    fill = Color.White,
                    enabled = ready && busy == null,
                ) { refreshKeys() }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cari kunci / nilai") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val ks = keys
        if (ks == null) {
            item { Text("Belum dipindai — aktifkan Shizuku lalu ketuk Pindai.", color = Muted, fontSize = 12.5.sp) }
        } else {
            val filtered = ks.filter {
                query.isBlank() || it.key.contains(query, ignoreCase = true) ||
                    (it.value?.contains(query, ignoreCase = true) == true)
            }
            item {
                Text(
                    "${filtered.size} dari ${ks.size} kunci • ${ks.count { it.editable }} bisa diubah",
                    color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
            items(filtered, key = { it.namespace + ":" + it.key }) { k ->
                NeoCard(onClick = {
                    detailTarget = k
                    editValue = k.value ?: ""
                }) {
                    Row {
                        Text(
                            k.namespace.uppercase(),
                            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Muted,
                            modifier = Modifier.padding(top = 2.dp, end = 7.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(k.key, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "= ${k.value ?: "(tidak ada)"}${if (k.editable) " • bisa diubah" else ""}",
                                color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                        Text("›", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }
        }

        // ---------- Menu tersembunyi ----------
        item {
            Spacer(Modifier.height(4.dp))
            Text("MENU TERSEMBYI — KODE RAHASIA", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
            Text(
                "Kode dikirim sebagai broadcast SECRET_CODE lewat shell — setara mengetik *#*#kode#*#* di dialer. " +
                    "Tidak semua HP punya penerimanya; aplikasi tidak bisa memastikan layarnya muncul.",
                color = Muted, fontSize = 12.sp
            )
        }
        items(HiddenScanner.SECRET_CODES, key = { it.code }) { sc ->
            NeoCard {
                Text(sc.title, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                Text("*#*#${sc.code}#*#*", fontFamily = FontFamily.Monospace, color = Muted, fontSize = 12.sp)
                Text(sc.desc, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.height(8.dp))
                NeoButton("Kirim Kode", fill = Color.White, enabled = ready && busy == null) {
                    runAction("Kode ${sc.code}") { listOf(HiddenScanner.sendSecretCode(context, sc.code)) }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("MENU TERSEMBYI — LAYAR APLIKASI", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
                    Text(
                        "Aktivitas exported yang namanya berpola engineer/test/debug/hidden/dll. Ketuk untuk mencoba membuka.",
                        color = Muted, fontSize = 12.sp
                    )
                }
                NeoButton("Pindai Ulang", modifier = Modifier.width(118.dp), fill = Color.White, enabled = busy == null) { refreshActs() }
            }
        }
        val ac = acts
        if (ac == null) {
            item { Text("Memindai layar aplikasi…", color = Muted, fontSize = 12.5.sp) }
        } else if (ac.isEmpty()) {
            item { Text("Tidak ditemukan layar berpola rahasia yang exported.", color = Muted, fontSize = 12.5.sp) }
        } else {
            item { Text("${ac.size} layar ditemukan", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            items(ac, key = { it.pkg + "/" + it.activity }) { a ->
                NeoCard(onClick = {
                    val ok = HiddenScanner.launchActivity(context, a)
                    results = listOf(
                        Optimizer.TweakResult(
                            "Buka ${a.shortName}", ok,
                            if (ok) "Intent terkirim — cek layar HP (aplikasi tujuan bisa saja langsung menutup)"
                            else "Gagal membuka — aktivitas mungkin menolak dibuka dari luar",
                        )
                    )
                }) {
                    Text(a.appLabel, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    Text(a.shortName, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text(a.pkg, color = Muted, fontSize = 11.5.sp)
                }
            }
        }
        item { Spacer(Modifier.height(2.dp)) }
    }

    // ---------- Dialog detail/ubah kunci ----------
    val target = detailTarget
    if (target != null) {
        val editableNow = target.editable
        val valueValid = editValue.trim().matches(Regex("^-?\\d+(\\.\\d+)?$|^(true|false)$", RegexOption.IGNORE_CASE))
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(target.key, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Namespace: ${target.namespace}\nNilai sekarang: ${target.value ?: "(tidak ada)"}", fontSize = 13.sp)
                    if (editableNow) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editValue,
                            onValueChange = { editValue = it },
                            label = { Text("Nilai baru (angka / true / false)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Nilai asal disnapshot dulu dan ikut Pulihkan Semua. " +
                                "Perhatian: sebagian kunci adalah cerminan sistem — diubah pun bisa ditimpa lagi oleh HP; baca-balik akan membuktikannya.",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        Text(
                            "Nilai ini bukan angka/boolean sederhana (teks/JSON/daftar) — hanya bisa dilihat dan disalin namanya, demi keamanan.",
                            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                if (editableNow) {
                    TextButton(
                        enabled = ready && busy == null && valueValid,
                        onClick = {
                            val t = target
                            detailTarget = null
                            runAction("Ubah kunci", after = { refreshKeys() }) {
                                listOf(HiddenScanner.setKey(context, t.namespace, t.key, editValue))
                            }
                        },
                    ) { Text("Terapkan", fontWeight = FontWeight.Black) }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onCopyText(target.key) }) { Text("Salin Nama") }
                    TextButton(onClick = { detailTarget = null }) { Text("Tutup") }
                }
            },
        )
    }
}
