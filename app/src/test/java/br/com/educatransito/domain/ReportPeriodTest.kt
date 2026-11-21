package br.com.educatransito.domain

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReportPeriodTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    @Test fun includesWholeLastDayAndExcludesFollowingMidnight() {
        val period = ReportPeriod.custom("01/02/2026", "28/02/2026", zone)
        assertTrue(period.contains(period.start))
        assertTrue(period.contains(period.endExclusive - 1))
        assertFalse(period.contains(period.start - 1))
        assertFalse(period.contains(period.endExclusive))
    }
    @Test fun weekStartsOnMondayAcrossMonthBoundary() {
        val period = ReportPeriod.preset("Semana", LocalDate.of(2026, 11, 1), zone)!!
        assertEquals("26/10/2026 a 01/11/2026", period.label)
    }
    @Test(expected = IllegalArgumentException::class)
    fun invalidCalendarDateIsRejected() { ReportPeriod.custom("31/02/2026", "05/03/2026", zone) }
    @Test(expected = IllegalArgumentException::class)
    fun reversedPeriodIsRejected() { ReportPeriod.custom("20/11/2026", "01/11/2026", zone) }
}
