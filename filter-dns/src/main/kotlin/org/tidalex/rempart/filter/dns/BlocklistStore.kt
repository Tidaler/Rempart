package org.tidalex.rempart.filter.dns

import android.content.Context
import org.tidalex.rempart.core.domain.DomainBlocklist
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Listes de blocage : une copie est embarquée dans l'APK (fonctionne hors ligne
 * dès l'installation) ; les mises à jour hebdomadaires sont enregistrées dans le
 * stockage privé de l'app au format binaire compact.
 */
object BlocklistStore {
    const val ASSET_NSFW = "blocklists/nsfw.txt.gz"
    const val ASSET_DOH = "blocklists/doh.txt.gz"
    private const val UPDATED_FILE = "blocklists/nsfw.bin"
    private const val MAGIC = 0x52454d50 // "REMP"

    @Volatile private var cachedBypass: DomainBlocklist? = null

    fun loadNsfw(context: Context): DomainBlocklist {
        val updated = File(context.filesDir, UPDATED_FILE)
        if (updated.exists()) {
            runCatching { return readBinary(updated) }
            updated.delete() // fichier corrompu : retour à la liste embarquée
        }
        return parseGz(context.assets.open(ASSET_NSFW))
    }

    fun loadBypass(context: Context): DomainBlocklist = cachedBypass ?: run {
        val builder = DomainBlocklist.parse(GZIPInputStream(context.assets.open(ASSET_DOH)).bufferedReader())
        builder.addAll(BypassResolvers.HOSTNAMES)
        builder.build().also { cachedBypass = it }
    }

    fun parseGz(input: InputStream): DomainBlocklist =
        GZIPInputStream(input).bufferedReader().use { DomainBlocklist.parse(it).build() }

    fun saveUpdated(context: Context, list: DomainBlocklist) {
        val target = File(context.filesDir, UPDATED_FILE)
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        writeBinary(tmp, list)
        check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) { "écriture de la liste impossible" }
    }

    fun writeBinary(file: File, list: DomainBlocklist) {
        DataOutputStream(file.outputStream().buffered()).use { out ->
            val hashes = list.toLongArray()
            out.writeInt(MAGIC)
            out.writeInt(hashes.size)
            hashes.forEach(out::writeLong)
        }
    }

    fun readBinary(file: File): DomainBlocklist = DataInputStream(file.inputStream().buffered()).use { inp ->
        check(inp.readInt() == MAGIC) { "format inconnu" }
        val n = inp.readInt()
        check(n in 0..5_000_000) { "taille invalide" }
        DomainBlocklist.fromHashes(LongArray(n) { inp.readLong() })
    }
}
