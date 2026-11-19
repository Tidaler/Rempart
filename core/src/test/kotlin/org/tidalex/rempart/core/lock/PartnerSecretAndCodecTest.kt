package org.tidalex.rempart.core.lock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PartnerSecretAndCodecTest {

    @Test fun `secret never stores the code in clear`() {
        val s = PartnerSecret.create("MonCodeSecret42", iterations = 1000)
        assertThat(s.serialize()).doesNotContain("MonCodeSecret42")
        assertThat(s.serialize().uppercase()).doesNotContain("MONCODESECRET42")
        assertThat(s.matches("moncodesecret42")).isTrue()
        assertThat(s.matches("MonCodeSecret43")).isFalse()
    }

    @Test fun `same code gives different hashes (random salt)`() {
        val a = PartnerSecret.create("ABCD-EFGH-JK", iterations = 1000)
        val b = PartnerSecret.create("ABCD-EFGH-JK", iterations = 1000)
        assertThat(a.hashB64).isNotEqualTo(b.hashB64)
    }

    @Test fun `weak codes are refused`() {
        assertThat(PartnerSecret.validate("1234")).isNotNull()
        assertThat(PartnerSecret.validate("12345678")).isNotNull()
        assertThat(PartnerSecret.validate("aaaaaaaa")).isNotNull()
        assertThat(PartnerSecret.validate("7KQ2-M9XD-H4TB")).isNull()
    }

    @Test fun `generated codes are valid and readable`() {
        repeat(50) {
            val c = PartnerSecret.generate()
            assertThat(c).matches("[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}")
        }
    }

    @Test fun `state roundtrip`() {
        val clock = FakeClock()
        val engine = LockEngine(clock)
        val s = engine.requestRelease(engine.engage(engine.setPartner(LockState(), "7KQ2-M9XD-H4TB", "Paul", null)))
        val decoded = LockStateCodec.decode(LockStateCodec.encode(s))
        assertThat(decoded.phase).isEqualTo(Phase.RELEASING)
        assertThat(decoded.release).isEqualTo(s.release)
        assertThat(decoded.partner!!.matches("7KQ2M9XDH4TB")).isTrue()
        assertThat(decoded.partnerName).isEqualTo("Paul")
    }

    // --- Sécurité : données corrompues ou modifiées ---

    @Test fun `corrupted phase stays locked`() {
        assertThat(LockStateCodec.decode(mapOf("phase" to "???", "delayHours" to "72")).phase).isEqualTo(Phase.LOCKED)
    }

    @Test fun `releasing without valid countdown falls back to locked`() {
        val m = mapOf("phase" to "RELEASING", "release" to "100,500,0") // accumulé > requis : invalide
        assertThat(LockStateCodec.decode(m).phase).isEqualTo(Phase.LOCKED)
    }

    @Test fun `tampered delay outside bounds is reset to default`() {
        assertThat(LockStateCodec.decode(mapOf("phase" to "LOCKED", "delayHours" to "0")).delayHours).isEqualTo(72)
    }

    @Test fun `empty storage means fresh install, open`() {
        assertThat(LockStateCodec.decode(emptyMap()).phase).isEqualTo(Phase.OPEN)
    }
}
