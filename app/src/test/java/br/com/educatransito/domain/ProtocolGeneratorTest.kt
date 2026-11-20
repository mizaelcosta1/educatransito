package br.com.educatransito.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolGeneratorTest {
    @Test fun formatsSixDigitSequenceAndYear() {
        assertEquals("EDU-2026-000001", ProtocolGenerator.format(2026, 1))
        assertEquals("EDU-2026-000125", ProtocolGenerator.format(2026, 125))
    }
    @Test fun rejectsNonPositiveSequence() {
        assertThrows(IllegalArgumentException::class.java) { ProtocolGenerator.format(2026, 0) }
    }
    @Test fun normalizesBrazilianPlateInput() {
        assertEquals("ABC1D23", ProtocolGenerator.normalizePlate("abc-1d23"))
    }
}
