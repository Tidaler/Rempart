package org.tidalex.rempart.filter.dns

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tidalex.rempart.core.domain.DomainBlocklist
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Mise à jour hebdomadaire des listes, uniquement en Wi-Fi/réseau non limité.
 * Seules des listes publiques sont téléchargées depuis GitHub ; rien n'est envoyé.
 * Une mise à jour ne peut qu'AJOUTER des domaines à la liste embarquée :
 * une source vide ou altérée ne peut pas affaiblir la protection.
 */
class BlocklistUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val builder = DomainBlocklist.Builder()
        var downloaded = 0
        for (url in SOURCES) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                    setRequestProperty("User-Agent", "Rempart-blocklist-updater")
                }
                conn.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line -> DomainBlocklist.parseLine(line)?.let { builder.add(it); downloaded++ } }
                }
            }
        }
        if (downloaded < MIN_EXPECTED) return@withContext Result.retry()
        // Union avec la liste embarquée : jamais moins protégé qu'à l'installation.
        GzipLines.forEach(applicationContext.assets.open(BlocklistStore.ASSET_NSFW)) { builder.add(it) }
        val list = builder.build()
        BlocklistStore.saveUpdated(applicationContext, list)
        FilterSettings(applicationContext).apply { listUpdatedAt = System.currentTimeMillis(); listSize = list.size }
        RempartVpnService.reloadBlocklist(applicationContext)
        Result.success()
    }

    companion object {
        private const val MIN_EXPECTED = 20_000
        val SOURCES = listOf(
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/nsfw-onlydomains.txt",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
        )

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("blocklist-update", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

internal object GzipLines {
    fun forEach(input: java.io.InputStream, f: (String) -> Unit) =
        java.util.zip.GZIPInputStream(input).bufferedReader().use { r -> r.forEachLine { l -> DomainBlocklist.parseLine(l)?.let(f) } }
}
