package org.tidalex.rempart.core.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Code du partenaire de confiance. Seule une empreinte PBKDF2 (sel aléatoire,
 * 120 000 itérations) est stockée : même en lisant les données de l'app
 * (téléphone rooté, sauvegarde), on ne retrouve pas le code.
 */
data class PartnerSecret(val saltB64: String, val hashB64: String, val iterations: Int) {

    /** Une empreinte abîmée ne fait jamais planter l'appli : elle ne correspond à rien (on reste verrouillé). */
    fun matches(candidate: String): Boolean = runCatching {
        val salt = Base64.getDecoder().decode(saltB64)
        val expected = Base64.getDecoder().decode(hashB64)
        val actual = derive(normalize(candidate), salt, iterations)
        MessageDigest.isEqual(expected, actual) // comparaison en temps constant
    }.getOrDefault(false)

    fun serialize(): String = "$iterations:$saltB64:$hashB64"

    companion object {
        const val MIN_LENGTH = 8
        private const val DEFAULT_ITERATIONS = 120_000
        private val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()

        /** Majuscules, sans espaces ni tirets : « abcd-efgh » == « ABCDEFGH ». */
        fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

        /** Problème du code proposé, ou null s'il est acceptable (le texte est traduit par l'app). */
        fun validate(code: String): CodeProblem? {
            val n = normalize(code)
            return when {
                n.length < MIN_LENGTH -> CodeProblem.TOO_SHORT
                n.toSet().size < 4 -> CodeProblem.TOO_SIMPLE
                n in WEAK -> CodeProblem.TOO_COMMON
                else -> null
            }
        }

        private val WEAK = setOf("12345678", "123456789", "1234567890", "00000000", "11111111", "PASSWORD", "AZERTYUI", "QWERTYUI", "87654321", "12341234")

        fun create(code: String, iterations: Int = DEFAULT_ITERATIONS, random: SecureRandom = SecureRandom()): PartnerSecret {
            require(validate(code) == null) { "code partenaire refusé : ${validate(code)}" }
            val salt = ByteArray(16).also(random::nextBytes)
            val hash = derive(normalize(code), salt, iterations)
            val enc = Base64.getEncoder()
            return PartnerSecret(enc.encodeToString(salt), enc.encodeToString(hash), iterations)
        }

        fun deserialize(s: String): PartnerSecret? {
            val p = s.split(':')
            if (p.size != 3) return null
            return PartnerSecret(p[1], p[2], p[0].toIntOrNull() ?: return null)
        }

        /** Code aléatoire lisible (alphabet Crockford, sans I/L/O/U) : « 7KQ2-M9XD-H4TB ». */
        fun generate(random: SecureRandom = SecureRandom()): String =
            (0 until 12).map { CROCKFORD[random.nextInt(CROCKFORD.size)] }
                .chunked(4).joinToString("-") { it.joinToString("") }

        private fun derive(code: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(code.toCharArray(), salt, iterations, 256)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }
}

enum class CodeProblem { TOO_SHORT, TOO_SIMPLE, TOO_COMMON }
