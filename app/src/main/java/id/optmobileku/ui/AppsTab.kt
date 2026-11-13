package id.optmobileku.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import id.optmobileku.core.AppManager
import id.optmobileku.core.Optimizer
import id.optmobileku.data.OptStore
import kotlinx.coroutines.launch

/**
 * Tab Aplikasi (v0.2): daftar semua aplikasi + detail per aplikasi.
 * Aksi destruktif (nonaktifkan, copot user-0) wajib konfirmasi, dan
 * aplikasi dalam daftar lindung tidak punya tombol destruktif sama sekali.
 */
@Composable
fun AppsTab(
    ready: Boolean,
    onAfterAction: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppManager.AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(0) } // 0 semua, 1 pengguna, 2 sistem, 3 nonaktif
    var selected by remember { mutableStateOf<AppManager.AppEntry?>(null) }
    var detail by remember { mutableStateOf<AppManager.AppDetailState?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Optimizer.TweakResult>>(emptyList()) }
    var confirmUninstall by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun refreshProfiles() {
        profiles = OptStore.getAllAppProfiles(context)
    }

    fun loadApps() {
        scope.launch {
            apps = AppManager.loadApps(context)
            refreshProfiles()
        }
    }

    LaunchedEffect(Unit) { refreshProfiles(); loadApps() }

    fun loadDetail(pkg: String) {
        if (!ready) {
            detail = null
            return
        }
        scope.launch { detail = AppManager.loadDetail(pkg) }
    }

    LaunchedEffect(selected) {
        detail = null
        selected?.let { loadDetail(it.pkg) }
    }

    fun doAction(name: String, block: suspend () -> Optimizer.TweakResult) {
        doActionList(name) { listOf(block()) }
    }

    fun doActionList(name: String, block: suspend () -> List<Optimizer.TweakResult>) {
        if (busy != null) return
        busy = name
        scope.launch {
            results = runCatching { block() }.getOrElse {
                listOf(Optimizer.TweakResult(name, false, "Error: ${it.message}"))
            }
            busy = null
            loadApps()
            selected?.let { loadDetail(it.pkg) }
            // Segarkan entri terpilih dari daftar baru (status enabled bisa berubah)
            selected?.let { sel ->
                scope.launch {
                    val fresh = AppManager.loadApps(context)
                    apps = fresh
                    selected = fresh.firstOrNull { it.pkg == sel.pkg } ?: sel
                    refreshProfiles()
                    onAfterAction()
                }
            } ?: onAfterAction()
        }
    }

    val sel = selected
    if (sel == null) {
        Column(Modifier.fillMaxSize()) {
            if (!ready) {
                NeoCard(fill = Color(0xFFFFF3C4)) {
                    Text("Mode lihat saja — aktifkan Shizuku untuk mengubah aplikasi. Daftar tetap bisa ditelusuri.", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(9.dp))
            }
            if (profiles.isNotEmpty()) {
                val nGame = profiles.values.count { it == AppManager.PROFILE_GAME }
                val nTidur = profiles.values.count { it == AppManager.PROFILE_TIDUR }
                NeoCard {
                    Text("PROFIL APLIKASI (v0.4)", fontWeight = FontWeight.Black, fontSize = 12.5.sp)
                    Text(
                        "Tersimpan: 🎮 $nGame Mode Game • 😴 $nTidur Mode Tidur. " +
                            "Profil = bucket + whitelist Doze + background, diterapkan sekaligus. " +
                            "Terapkan ulang berguna sesudah HP restart.",
                        color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    NeoButton("Terapkan Ulang Semua Profil", fill = Color.White, enabled = ready && busy == null) {
                        doActionList("Terapkan semua profil") { AppManager.applyAllProfiles(context) }
                    }
                }
                Spacer(Modifier.height(9.dp))
            }
            if (busy != null || results.isNotEmpty()) {
                ResultsCard(busy = busy, results = results)
                Spacer(Modifier.height(9.dp))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Cari aplikasi / package") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Semua", "Pengguna", "Sistem", "Nonaktif").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (filter == i) OptYellow else Color.White)
                            .border(2.dp, Ink, RoundedCornerShape(99.dp))
                            .clickable { filter = i }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
            Spacer(Modifier.height(9.dp))
            val all = apps
            if (all == null) {
                Text("Memuat daftar aplikasi…", color = Muted, fontSize = 13.sp)
            } else {
                val filtered = all.filter { e ->
                    val matchesQuery = query.isBlank() ||
                        e.label.contains(query, ignoreCase = true) ||
                        e.pkg.contains(query, ignoreCase = true)
                    val matchesFilter = when (filter) {
                        1 -> !e.isSystem
                        2 -> e.isSystem
                        3 -> !e.enabled
                        else -> true
                    }
                    matchesQuery && matchesFilter
                }
                Text("${filtered.size} aplikasi", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(filtered, key = { it.pkg }) { e ->
                        NeoCard(onClick = { selected = e }) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(e.label, fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                                    Text(e.pkg, color = Muted, fontSize = 11.5.sp)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(top = 5.dp)
                                    ) {
                                        Chip(if (e.isSystem) "Sistem" else "Pengguna", OptBlue)
                                        if (!e.enabled) Chip("Nonaktif", Color(0xFFFFE0E0))
                                        if (e.protectedReason != null) Chip("🔒 ${e.protectedReason}", Color(0xFFEEEEEE))
                                        when (profiles[e.pkg]) {
                                            AppManager.PROFILE_GAME -> Chip("🎮 Game", Color(0xFFDFF7E9))
                                            AppManager.PROFILE_TIDUR -> Chip("😴 Tidur", Color(0xFFE3E8FF))
                                        }
                                    }
                                }
                                Text("›", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ---------- Detail per aplikasi ----------
    Column(Modifier.fillMaxSize()) {
        NeoButton("← Kembali ke daftar", fill = Color.White) { selected = null }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                NeoCard {
                    Text(sel.label, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(sel.pkg, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Chip(if (sel.isSystem) "Sistem" else "Pengguna", OptBlue)
                        Chip(if (sel.enabled) "Aktif" else "Nonaktif", if (sel.enabled) Color(0xFFDFF7E9) else Color(0xFFFFE0E0))
                        if (sel.protectedReason != null) Chip("🔒 ${sel.protectedReason}", Color(0xFFEEEEEE))
                        when (profiles[sel.pkg]) {
                            AppManager.PROFILE_GAME -> Chip("🎮 Mode Game", Color(0xFFDFF7E9))
                            AppManager.PROFILE_TIDUR -> Chip("😴 Mode Tidur", Color(0xFFE3E8FF))
                        }
                    }
                    if (ready) {
                        Spacer(Modifier.height(7.dp))
                        val d = detail
                        Text(
                            if (d == null) "Memuat status…"
                            else "Bucket standby: ${d.bucketLabel ?: "?"}\n" +
                                "Whitelist Doze: ${boolTextId(d.dozeWhitelisted)}\n" +
                                "Background dibatasi: ${boolTextId(d.backgroundIgnored)}\n" +
                                "Terpasang untuk user ini: ${boolTextId(d.installedForUser0)}\n" +
                                "Dibekukan (suspend): ${boolTextId(d.suspended)}",
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            if (sel.protectedReason == null) {
                item {
                    Text("PROFIL APLIKASI (v0.4) — diterapkan sekaligus", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "🎮 Mode Game: bucket Aktif + bebas Doze + background diizinkan.\n" +
                            "😴 Mode Tidur: bucket Dibatasi + Doze penuh + background dibatasi (notifikasi bisa telat).\n" +
                            "Keadaan asal aplikasi disnapshot dulu; Lepas Profil memulihkannya.",
                        color = Muted, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BucketButton("🎮 Mode Game", Modifier.weight(1f), ready && busy == null) {
                            doActionList("Profil Game") { AppManager.applyProfile(context, sel.pkg, AppManager.PROFILE_GAME) }
                        }
                        BucketButton("😴 Mode Tidur", Modifier.weight(1f), ready && busy == null) {
                            doActionList("Profil Tidur") { AppManager.applyProfile(context, sel.pkg, AppManager.PROFILE_TIDUR) }
                        }
                        if (profiles[sel.pkg] != null) {
                            BucketButton("Lepas Profil", Modifier.weight(1f), ready && busy == null) {
                                doActionList("Lepas profil") { AppManager.removeProfile(context, sel.pkg) }
                            }
                        }
                    }
                }
                item {
                    Text("BUCKET STANDBY (jarang dipakai = makin dibatasi)", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Mengubah bucket memengaruhi seberapa bebas aplikasi jalan di latar.", color = Muted, fontSize = 12.sp)
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val buckets = listOf(
                            "active" to "Aktif", "working_set" to "Sering dipakai",
                            "frequent" to "Kadang", "rare" to "Jarang", "restricted" to "Dibatasi",
                        )
                        // Dua baris agar muat: 3 + 2
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                buckets.take(3).forEach { (value, label) ->
                                    BucketButton(label, Modifier.weight(1f), ready && busy == null) {
                                        doAction("Bucket $label") { AppManager.setStandbyBucket(context, sel.pkg, value, label) }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                buckets.drop(3).forEach { (value, label) ->
                                    BucketButton(label, Modifier.weight(1f), ready && busy == null) {
                                        doAction("Bucket $label") { AppManager.setStandbyBucket(context, sel.pkg, value, label) }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    ActionCard(
                        title = "Whitelist Doze",
                        desc = if (detail?.dozeWhitelisted == true) "Saat ini aplikasi BEBAS dari Doze (boleh jalan di latar)."
                        else "Saat ini aplikasi tunduk pada Doze (dihemat Android).",
                        buttonText = if (detail?.dozeWhitelisted == true) "Cabut dari Whitelist" else "Masukkan Whitelist",
                        enabled = ready && busy == null,
                    ) {
                        doAction("Whitelist Doze") {
                            AppManager.setDozeWhitelist(context, sel.pkg, detail?.dozeWhitelisted != true)
                        }
                    }
                }
                item {
                    ActionCard(
                        title = "Background Aplikasi",
                        desc = if (detail?.backgroundIgnored == true) "Saat ini background DIBATASI (AppOps ignore) — notifikasi bisa telat."
                        else "Saat ini background diizinkan.",
                        buttonText = if (detail?.backgroundIgnored == true) "Izinkan Background" else "Batasi Background",
                        enabled = ready && busy == null,
                    ) {
                        doAction("Background") {
                            AppManager.setBackgroundIgnored(context, sel.pkg, detail?.backgroundIgnored != true)
                        }
                    }
                }
                item {
                    ActionCard(
                        title = "Paksa Berhenti",
                        desc = "Hentikan aplikasi sekarang (bisa dibuka lagi seperti biasa).",
                        buttonText = "Paksa Berhenti",
                        enabled = ready && busy == null,
                    ) { doAction("Paksa berhenti") { AppManager.forceStop(context, sel.pkg) } }
                }
                item {
                    ActionCard(
                        title = if (detail?.suspended == true) "Cairkan (Unsuspend)" else "Bekukan (Suspend)",
                        desc = "Suspend membuat aplikasi tidak bisa dibuka/di-notifikasi sampai dicairkan — lebih ringan dari nonaktifkan.",
                        buttonText = if (detail?.suspended == true) "Cairkan" else "Bekukan",
                        enabled = ready && busy == null,
                    ) {
                        doAction("Suspend") { AppManager.setSuspended(context, sel.pkg, detail?.suspended != true) }
                    }
                }
                item {
                    ActionCard(
                        title = if (sel.enabled) "Nonaktifkan Aplikasi" else "Aktifkan Aplikasi",
                        desc = if (sel.enabled) "Aplikasi hilang dari launcher dan tidak jalan sama sekali sampai diaktifkan lagi."
                        else "Aplikasi ini sedang nonaktif.",
                        buttonText = if (sel.enabled) "Nonaktifkan" else "Aktifkan",
                        enabled = ready && busy == null,
                        danger = sel.enabled,
                    ) {
                        if (sel.enabled) confirmDisable = true
                        else doAction("Aktifkan") { AppManager.setEnabled(context, sel.pkg, true) }
                    }
                }
                item {
                    if (detail?.installedForUser0 == false) {
                        ActionCard(
                            title = "Pulihkan Aplikasi",
                            desc = "Aplikasi ini sedang tercopot untuk user ini. Pulihkan dari APK sistem (install-existing).",
                            buttonText = "↩️ Pulihkan Aplikasi",
                            enabled = ready && busy == null,
                        ) { doAction("Pulihkan aplikasi") { AppManager.installExisting(context, sel.pkg) } }
                    } else {
                        ActionCard(
                            title = "Copot untuk User Ini",
                            desc = "pm uninstall --user 0: aplikasi hilang untuk user ini, APK sistem tetap ada dan BISA dipulihkan. Data aplikasi untuk user ini ikut terhapus.",
                            buttonText = "Copot (User Ini Saja)",
                            enabled = ready && busy == null,
                            danger = true,
                        ) { confirmUninstall = true }
                    }
                }
            } else {
                item {
                    NeoCard(fill = Color(0xFFEEEEEE)) {
                        Text(
                            "🔒 Aplikasi ini dilindungi (${sel.protectedReason}) — tombol ubah sengaja tidak disediakan demi keamanan HP.",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item { ResultsCard(busy = busy, results = results) }
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Nonaktifkan ${sel.label}?", fontWeight = FontWeight.Black) },
            text = { Text("Aplikasi akan hilang dari launcher dan berhenti total sampai kamu mengaktifkannya lagi dari sini. Lanjutkan?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    doAction("Nonaktifkan") { AppManager.setEnabled(context, sel.pkg, false) }
                }) { Text("Ya, Nonaktifkan", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Batal") } }
        )
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Copot ${sel.label} untuk user ini?", fontWeight = FontWeight.Black) },
            text = {
                Text("Ini pm uninstall --user 0: aplikasi hilang untuk user ini dan datanya terhapus, TAPI APK sistem tetap ada — kamu bisa memulihkannya kapan pun dari layar ini (tombol Pulihkan). Lanjutkan?")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    doAction("Copot user-0") { AppManager.uninstallUser0(context, sel.pkg) }
                }) { Text("Ya, Copot", fontWeight = FontWeight.Black) }
            },
            dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun Chip(text: String, fill: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(fill)
            .border(1.8.dp, Ink, RoundedCornerShape(99.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) { Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BucketButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color.White else Color(0xFFEFEEE8))
            .border(2.5.dp, Ink, RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = if (enabled) Ink else Muted)
    }
}

@Composable
private fun ActionCard(
    title: String,
    desc: String,
    buttonText: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    NeoCard {
        Text(title, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(desc, color = Muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(9.dp))
        NeoButton(
            buttonText,
            fill = if (danger) OptRed else Color.White,
            textColor = if (danger) Color.White else Ink,
            enabled = enabled,
        ) { onClick() }
    }
}

private fun boolTextId(v: Boolean?): String = when (v) {
    true -> "Ya"
    false -> "Tidak"
    null -> "?"
}
