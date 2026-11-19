package org.tidalex.rempart.filter.dns.packet

import java.io.ByteArrayOutputStream

/** Analyse et construction de messages DNS (RFC 1035), juste ce qu'il faut pour filtrer. */
object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_CNAME = 5
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val CLASS_IN = 1
    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    data class Question(val name: String, val type: Int, val klass: Int)

    data class Query(val id: Int, val flags: Int, val question: Question, val questionEnd: Int)

    data class Record(val name: String, val type: Int, val ttl: Long, val rdata: ByteArray, val target: String? = null)

    class MalformedException(msg: String) : Exception(msg)

    /** Analyse une requête : une seule question attendue. */
    fun parseQuery(msg: ByteArray): Query {
        if (msg.size < 12) throw MalformedException("trop court")
        val flags = u16(msg, 2)
        if ((flags and 0x8000) != 0) throw MalformedException("pas une requête")
        if (u16(msg, 4) != 1) throw MalformedException("une seule question supportée")
        val (name, end) = readName(msg, 12)
        if (end + 4 > msg.size) throw MalformedException("question tronquée")
        return Query(u16(msg, 0), flags, Question(name, u16(msg, end), u16(msg, end + 2)), end + 4)
    }

    /** Lit les enregistrements de la section réponse d'une réponse DNS. */
    fun parseAnswers(msg: ByteArray): List<Record> {
        if (msg.size < 12) throw MalformedException("trop court")
        val qd = u16(msg, 4)
        val an = u16(msg, 6)
        var pos = 12
        repeat(qd) { pos = readName(msg, pos).second + 4 }
        val out = ArrayList<Record>(an)
        repeat(an) {
            val (name, p) = readName(msg, pos)
            if (p + 10 > msg.size) throw MalformedException("réponse tronquée")
            val type = u16(msg, p)
            val ttl = (u16(msg, p + 4).toLong() shl 16) or u16(msg, p + 6).toLong()
            val rdLen = u16(msg, p + 8)
            val rdStart = p + 10
            if (rdStart + rdLen > msg.size) throw MalformedException("rdata tronquée")
            val target = if (type == TYPE_CNAME) readName(msg, rdStart).first else null
            out += Record(name, type, ttl, msg.copyOfRange(rdStart, rdStart + rdLen), target)
            pos = rdStart + rdLen
        }
        return out
    }

    fun rcode(msg: ByteArray): Int = if (msg.size < 4) -1 else msg[3].toInt() and 0x0F

    /**
     * Réponse « bloquée » : 0.0.0.0 pour A, :: pour AAAA, réponse vide sinon
     * (HTTPS/SVCB inclus, pour ne pas fuiter d'indices d'adresse).
     */
    fun blockedResponse(query: ByteArray, q: Query, ttl: Long = 60): ByteArray {
        val answers = when (q.question.type) {
            TYPE_A -> listOf(Record(q.question.name, TYPE_A, ttl, ByteArray(4)))
            TYPE_AAAA -> listOf(Record(q.question.name, TYPE_AAAA, ttl, ByteArray(16)))
            else -> emptyList()
        }
        return response(query, q, RCODE_NOERROR, answers)
    }

    fun errorResponse(query: ByteArray, q: Query, rcode: Int): ByteArray = response(query, q, rcode, emptyList())

    /** Construit une réponse reprenant la question d'origine et des réponses données. */
    fun response(query: ByteArray, q: Query, rcode: Int, answers: List<Record>): ByteArray {
        val out = ByteArrayOutputStream(q.questionEnd + answers.size * 32)
        val rd = q.flags and 0x0100
        val opcode = q.flags and 0x7800
        out.write16(q.id)
        out.write16(0x8000 or opcode or rd or 0x0080 or (rcode and 0xF)) // QR, opcode, RD, RA, rcode
        out.write16(1)
        out.write16(answers.size)
        out.write16(0)
        out.write16(0)
        out.write(query, 12, q.questionEnd - 12)
        for (a in answers) {
            out.write16(0xC00C) // pointeur vers le nom de la question
            out.write16(a.type)
            out.write16(CLASS_IN)
            out.write16(((a.ttl ushr 16) and 0xFFFF).toInt())
            out.write16((a.ttl and 0xFFFF).toInt())
            out.write16(a.rdata.size)
            out.write(a.rdata)
        }
        return out.toByteArray()
    }

    /** Requête pour un autre nom (utilisée pour SafeSearch). */
    fun buildQuery(id: Int, name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream(32 + name.length)
        out.write16(id)
        out.write16(0x0100) // RD
        out.write16(1); out.write16(0); out.write16(0); out.write16(0)
        for (label in name.trimEnd('.').split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            require(b.size in 1..63) { "label invalide" }
            out.write(b.size); out.write(b)
        }
        out.write(0)
        out.write16(type)
        out.write16(CLASS_IN)
        return out.toByteArray()
    }

    /** Lit un nom (avec compression), retourne (nom, position après le nom). */
    fun readName(msg: ByteArray, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var pos = start
        var end = -1
        var jumps = 0
        while (true) {
            if (pos >= msg.size) throw MalformedException("nom tronqué")
            val len = msg[pos].toInt() and 0xFF
            when {
                len == 0 -> { if (end < 0) end = pos + 1; break }
                (len and 0xC0) == 0xC0 -> {
                    if (pos + 1 >= msg.size) throw MalformedException("pointeur tronqué")
                    if (++jumps > 16) throw MalformedException("boucle de compression")
                    if (end < 0) end = pos + 2
                    pos = ((len and 0x3F) shl 8) or (msg[pos + 1].toInt() and 0xFF)
                }
                (len and 0xC0) != 0 -> throw MalformedException("label étendu non supporté")
                else -> {
                    if (pos + 1 + len > msg.size) throw MalformedException("label tronqué")
                    if (sb.isNotEmpty()) sb.append('.')
                    for (i in pos + 1..pos + len) sb.append((msg[i].toInt() and 0xFF).toChar())
                    pos += 1 + len
                    if (sb.length > 255) throw MalformedException("nom trop long")
                }
            }
        }
        return sb.toString().lowercase() to end
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun ByteArrayOutputStream.write16(v: Int) { write((v ushr 8) and 0xFF); write(v and 0xFF) }
}
