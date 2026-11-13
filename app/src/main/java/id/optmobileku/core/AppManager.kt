package id.optmobileku.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import id.optmobileku.core.Optimizer.TweakResult
import id.optmobileku.data.OptStore
import id.optmobileku.shizuku.ShizukuCtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manajemen aplikasi Opt Mobileku v0.2 — semua lewat shell Shizuku,
 * semua reversibel, semua tercatat di log, dan status penting dibaca
 * balik untuk verifikasi (bukan sekadar percaya exit code).
 *
 * DAFTAR LINDUNG tidak bisa dinonaktifkan / dibekukan / dicopot dari
 * aplikasi ini: aplikasi inti sistem + peran dinamis (launcher, keyboard,
 * dialer bawaan) + Shizuku + Opt Mobileku sendiri.
 */
object AppManager {

    data class AppEntry(
        val pkg: String,
        val label: String,
        val isSystem: Boolean,
        val enabled: Boolean,
        val protectedReason: String?,
    )

    data class AppDetailState(
        val bucketLabel: String?,
        val dozeWhitelisted: Boolean?,
        val backgroundIgnored: Boolean?,
        val installedForUser0: Boolean?,
        val suspended: Boolean?,
    )

    // Package inti yang hardcoded dilindungi (di luar peran dinamis).
    private val HARD_PROTECTED = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.android.providers.downloads",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.android.shell",
        "moe.shizuku.privileged.api",
        "com.android.keychain",
        "com.android.certinstaller",
    )

    private fun shell(cmd: String): ShizukuCtl.ShellResult? = ShizukuCtl.execBlocking(cmd)

    /** Alasan perlindungan sebuah package, atau null bila boleh diubah. */
    fun protectedReason(context: Context, pkg: String): String? {
        if (pkg == context.packageName) return "Opt Mobileku sendiri"
        if (pkg in HARD_PROTECTED) return "Aplikasi inti sistem"
        // Launcher bawaan (peran dinamis)
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved?.activityInfo?.packageName == pkg) return "Launcher bawaan"
        }
        // Keyboard bawaan (peran dinamis)
        runCatching {
            val ime = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            if (ime != null && ime.startsWith("$pkg/")) return "Keyboard bawaan"
        }
        // Dialer bawaan (peran dinamis, API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (tm?.defaultDialerPackage == pkg) return "Dialer bawaan"
            }
        }
        return null
    }

    /** Muat daftar semua aplikasi (user + sistem). Berat — panggil dari IO. */
    suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
        packages.map { info ->
            val appInfo = info.applicationInfo
            val isSystem = appInfo != null &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val enabled = runCatching {
                pm.getApplicationEnabledSetting(info.packageName)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT).let {
                it != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    it != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER &&
                    (appInfo?.enabled ?: true)
            }
            AppEntry(
                pkg = info.packageName,
                label = runCatching { appInfo?.loadLabel(pm)?.toString() }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: info.packageName,
                isSystem = isSystem,
                enabled = enabled,
                protectedReason = protectedReason(context, info.packageName),
            )
        }.sortedWith(compareBy({ it.label.lowercase() }))
    }

    // ---------- Baca status detail (read-back) ----------

    private fun bucketLabelFromCode(code: Int): String = when (code) {
        10 -> "Aktif (10)"
        20 -> "Sering dipakai (20)"
        30 -> "Kadang (30)"
        40 -> "Jarang (40)"
        45 -> "Dibatasi (45)"
        50 -> "Tidak pernah (50)"
        else -> "Kode $code"
    }

    suspend fun loadDetail(pkg: String): AppDetailState = withContext(Dispatchers.IO) {
        val bucket = shell("am get-standby-bucket $pkg")?.takeIf { it.ok }
            ?.output?.trim()?.lines()?.firstOrNull()?.trim()?.toIntOrNull()
        val doze = shell("dumpsys deviceidle whitelist")?.takeIf { it.ok }?.output
            ?.lines()?.map { it.trim().removePrefix("system-excidle,").removePrefix("user,") }
            ?.any { it == pkg || it.endsWith(",$pkg") || it == "user,$pkg" }
        val appops = shell("cmd appops get $pkg RUN_ANY_IN_BACKGROUND")?.takeIf { it.ok }?.output
        val bgIgnored = appops?.let {
            it.contains("ignore", ignoreCase = true) && !it.contains("No operations", ignoreCase = true)
        }
        val installed0 = shell("cmd package list packages --user 0 $pkg")?.takeIf { it.ok }?.output
            ?.lines()?.any { it.trim() == "package:$pkg" }
        // Status suspend tidak punya query list yang stabil antar OEM;
        // cek lewat dumpsys package, cari "suspended=true" di blok user 0.
        val suspended = shell("dumpsys package $pkg")?.takeIf { it.ok }?.output
            ?.contains("suspended=true")
        AppDetailState(
            bucketLabel = bucket?.let { bucketLabelFromCode(it) },
            dozeWhitelisted = doze,
            backgroundIgnored = bgIgnored,
            installedForUser0 = installed0,
            suspended = suspended,
        )
    }

    // ---------- Aksi per aplikasi ----------

    private fun result(context: Context, name: String, ok: Boolean, detail: String): TweakResult {
        OptStore.addLog(context, "$name ${if (ok) "✓" else "GAGAL"} — $detail")
        return TweakResult(name, ok, detail)
    }

    suspend fun setStandbyBucket(context: Context, pkg: String, bucket: String, bucketLabel: String): TweakResult =
        withContext(Dispatchers.IO) {
            val name = "Bucket standby $pkg → $bucketLabel"
            val r = shell("am set-standby-bucket $pkg $bucket")
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            val back = shell("am get-standby-bucket $pkg")?.output?.trim()?.lines()?.firstOrNull()?.trim()
            val backCode = back?.toIntOrNull()
            val ok = r.ok && backCode != null &&
                bucketLabelFromCode(backCode).startsWith(bucketLabel.substringBefore(" ("))
            result(
                context, name, ok,
                if (ok) "Terbaca balik: ${bucketLabelFromCode(backCode!!)}"
                else "Terbaca balik: ${back ?: "gagal baca"} (exit ${r.exitCode})",
            )
        }

    suspend fun setDozeWhitelist(context: Context, pkg: String, add: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            val name = "Whitelist Doze $pkg → ${if (add) "ditambah" else "dicabut"}"
            val r = shell("dumpsys deviceidle whitelist ${if (add) "+" else "-"}$pkg")
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            val detail = loadDetail(pkg)
            val ok = r.ok && detail.dozeWhitelisted == add
            result(
                context, name, ok,
                if (ok) "Terbaca balik ✓" else "Terbaca balik: whitelist=${detail.dozeWhitelisted} — sebagian entri sistem memang tidak bisa dicabut",
            )
        }

    suspend fun setBackgroundIgnored(context: Context, pkg: String, ignore: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            val name = "Background $pkg → ${if (ignore) "dibatasi" else "diizinkan"}"
            val mode = if (ignore) "ignore" else "allow"
            val r1 = shell("cmd appops set $pkg RUN_ANY_IN_BACKGROUND $mode")
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            shell("cmd appops set $pkg RUN_IN_BACKGROUND $mode")
            val detail = loadDetail(pkg)
            val ok = r1.ok && detail.backgroundIgnored == ignore
            result(context, name, ok, if (ok) "Terbaca balik ✓" else "Terbaca balik tidak cocok (appops=${detail.backgroundIgnored})")
        }

    suspend fun forceStop(context: Context, pkg: String): TweakResult = withContext(Dispatchers.IO) {
        val name = "Paksa berhenti $pkg"
        val r = shell("am force-stop $pkg")
            ?: return@withContext result(context, name, false, "Shizuku belum siap")
        result(context, name, r.ok, if (r.ok) "Aplikasi dihentikan" else "Gagal (exit ${r.exitCode})")
    }

    suspend fun setSuspended(context: Context, pkg: String, suspend: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            val name = if (suspend) "Bekukan (suspend) $pkg" else "Cairkan (unsuspend) $pkg"
            val r = shell("pm ${if (suspend) "suspend" else "unsuspend"} --user 0 $pkg")
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            val detail = loadDetail(pkg)
            val ok = r.ok && detail.suspended == suspend
            result(context, name, ok, if (ok) "Terbaca balik ✓" else "Perintah jalan (exit ${r.exitCode}), baca-balik suspend=${detail.suspended}")
        }

    suspend fun setEnabled(context: Context, pkg: String, enable: Boolean): TweakResult =
        withContext(Dispatchers.IO) {
            val name = if (enable) "Aktifkan $pkg" else "Nonaktifkan $pkg"
            val r = (if (enable) shell("pm enable --user 0 $pkg") else shell("pm disable-user --user 0 $pkg"))
                ?: return@withContext result(context, name, false, "Shizuku belum siap")
            val nowEnabled = runCatching {
                context.packageManager.getApplicationEnabledSetting(pkg)
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT).let {
                it != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
                    it != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            val ok = r.ok && nowEnabled == enable
            result(context, name, ok, if (ok) "Terbaca balik ✓" else "Terbaca balik enabled=$nowEnabled (exit ${r.exitCode})")
        }

    suspend fun uninstallUser0(context: Context, pkg: String): TweakResult = withContext(Dispatchers.IO) {
        val name = "Copot untuk user ini $pkg"
        val r = shell("pm uninstall -k --user 0 $pkg")
            ?: return@withContext result(context, name, false, "Shizuku belum siap")
        val detail = loadDetail(pkg)
        val ok = r.ok && detail.installedForUser0 == false
        result(
            context, name, ok,
            if (ok) "Terbaca balik: sudah tidak terpasang untuk user 0 ✓ (bisa dipulihkan)"
            else "Terbaca balik installed=${detail.installedForUser0} (exit ${r.exitCode}): ${r.output.take(100)}",
        )
    }

    suspend fun installExisting(context: Context, pkg: String): TweakResult = withContext(Dispatchers.IO) {
        val name = "Pulihkan (install-existing) $pkg"
        val r = shell("cmd package install-existing --user 0 $pkg")
            ?: return@withContext result(context, name, false, "Shizuku belum siap")
        val detail = loadDetail(pkg)
        val ok = detail.installedForUser0 == true
        result(
            context, name, ok,
            if (ok) "Terbaca balik: terpasang lagi untuk user 0 ✓"
            else "Terbaca balik installed=${detail.installedForUser0} (exit ${r.exitCode}): ${r.output.take(100)}",
        )
    }

    // ================= v0.4 — Profil per aplikasi =================
    // Profil = kumpulan tiga knob yang sudah ada sejak v0.2 (bucket
    // standby, whitelist Doze, AppOps background), diterapkan sekaligus:
    //  - GAME : bucket Aktif + whitelist Doze + background diizinkan
    //           (aplikasi bebas jalan; untuk game & aplikasi penting)
    //  - TIDUR: bucket Dibatasi + whitelist dicabut + background ignore
    //           (hemat maksimal; notifikasi bisa telat — dinyatakan jujur)
    // Sebelum profil PERTAMA diterapkan ke sebuah aplikasi, keadaan trio
    // aslinya disnapshot; "Lepas Profil" memulihkan dari snapshot itu.

    const val PROFILE_GAME = "GAME"
    const val PROFILE_TIDUR = "TIDUR"

    private fun bucketNameLabel(code: Int): Pair<String, String>? = when (code) {
        10 -> "active" to "Aktif"
        20 -> "working_set" to "Sering dipakai"
        30 -> "frequent" to "Kadang"
        40 -> "rare" to "Jarang"
        45 -> "restricted" to "Dibatasi"
        50 -> "never" to "Tidak pernah"
        else -> null
    }

    private suspend fun ensureProfileSnapshot(context: Context, pkg: String) {
        if (OptStore.getProfileSnapshot(context, pkg) != null) return
        val bucketCode = shell("am get-standby-bucket $pkg")?.takeIf { it.ok }
            ?.output?.trim()?.lines()?.firstOrNull()?.trim()?.toIntOrNull()
        val detail = loadDetail(pkg)
        OptStore.saveProfileSnapshotOnce(context, pkg, bucketCode, detail.dozeWhitelisted, detail.backgroundIgnored)
    }

    private suspend fun applyProfileBundle(context: Context, pkg: String, profile: String): List<TweakResult> =
        when (profile) {
            PROFILE_GAME -> listOf(
                setStandbyBucket(context, pkg, "active", "Aktif"),
                setDozeWhitelist(context, pkg, true),
                setBackgroundIgnored(context, pkg, false),
            )
            else -> listOf( // TIDUR
                setStandbyBucket(context, pkg, "restricted", "Dibatasi"),
                setDozeWhitelist(context, pkg, false),
                setBackgroundIgnored(context, pkg, true),
            )
        }

    suspend fun applyProfile(context: Context, pkg: String, profile: String): List<TweakResult> =
        withContext(Dispatchers.IO) {
            protectedReason(context, pkg)?.let {
                return@withContext listOf(result(context, "Profil $pkg", false, "Dilindungi: $it"))
            }
            ensureProfileSnapshot(context, pkg)
            val results = applyProfileBundle(context, pkg, profile)
            if (results.all { it.success }) {
                OptStore.setAppProfile(context, pkg, profile)
                OptStore.addLog(context, "Profil $profile diterapkan ke $pkg ✓ (3/3)")
            } else {
                OptStore.addLog(
                    context,
                    "Profil $profile ke $pkg SEBAGIAN (${results.count { it.success }}/3) — penanda profil tidak disimpan",
                )
            }
            results
        }

    /** Lepas profil: pulihkan trio dari snapshot profil, lalu cabut penanda. */
    suspend fun removeProfile(context: Context, pkg: String): List<TweakResult> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<TweakResult>()
            val snap = OptStore.getProfileSnapshot(context, pkg)
            if (snap == null) {
                results += result(context, "Lepas profil $pkg", true, "Tidak ada snapshot trio — hanya penanda profil yang dicabut")
            } else {
                val (bucketCode, doze, bg) = snap
                if (bucketCode != null) {
                    val nl = bucketNameLabel(bucketCode)
                    if (nl != null) results += setStandbyBucket(context, pkg, nl.first, nl.second)
                    else results += result(context, "Pulihkan bucket $pkg", false, "Kode bucket asal ($bucketCode) tidak dikenal")
                }
                if (doze != null) results += setDozeWhitelist(context, pkg, doze)
                if (bg != null) results += setBackgroundIgnored(context, pkg, bg)
                if (results.isEmpty()) {
                    results += result(context, "Lepas profil $pkg", true, "Snapshot trio kosong — tidak ada yang perlu dipulihkan")
                }
            }
            OptStore.setAppProfile(context, pkg, null)
            results
        }

    /** Terapkan ulang semua profil tersimpan sekaligus (mis. sesudah restart HP). */
    suspend fun applyAllProfiles(context: Context): List<TweakResult> = withContext(Dispatchers.IO) {
        val profiles = OptStore.getAllAppProfiles(context)
        if (profiles.isEmpty()) {
            return@withContext listOf(
                TweakResult("Terapkan semua profil", false, "Belum ada aplikasi yang diberi profil"),
            )
        }
        val out = mutableListOf<TweakResult>()
        for ((pkg, profile) in profiles.toSortedMap()) {
            if (protectedReason(context, pkg) != null) {
                out += TweakResult("Profil $profile $pkg", false, "Dilindungi — dilewati")
                continue
            }
            ensureProfileSnapshot(context, pkg)
            val rs = applyProfileBundle(context, pkg, profile)
            val okCount = rs.count { it.success }
            out += TweakResult(
                "Profil $profile $pkg", okCount == rs.size,
                if (okCount == rs.size) "Diterapkan ✓ (3/3)" else "Sebagian: $okCount/3 berhasil — lihat Log Eksekusi",
            )
        }
        OptStore.addLog(context, "Terapkan ulang semua profil: ${out.count { it.success }}/${out.size} aplikasi penuh")
        out
    }
}
