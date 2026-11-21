package br.com.educatransito.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.TemporalAdjusters

data class ReportPeriod(val start: Long, val endExclusive: Long, val label: String) {
    fun contains(epochMillis: Long) = epochMillis >= start && epochMillis < endExclusive
    companion object {
        private val display = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        fun fromDates(first: LocalDate, last: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ReportPeriod {
            require(!last.isBefore(first)) { "A data final deve ser igual ou posterior à inicial." }
            return ReportPeriod(first.atStartOfDay(zone).toInstant().toEpochMilli(),
                last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                first.format(display) + " a " + last.format(display))
        }
        fun custom(first: String, last: String, zone: ZoneId = ZoneId.systemDefault()): ReportPeriod {
            val startDate = try { LocalDate.parse(first.trim(), display) } catch (_: Exception) {
                throw IllegalArgumentException("Informe a data inicial no formato DD/MM/AAAA.")
            }
            val endDate = try { LocalDate.parse(last.trim(), display) } catch (_: Exception) {
                throw IllegalArgumentException("Informe a data final no formato DD/MM/AAAA.")
            }
            return fromDates(startDate, endDate, zone)
        }
        fun preset(name: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ReportPeriod? =
            when (name) {
                "Hoje" -> fromDates(today, today, zone)
                "Semana" -> {
                    val first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    fromDates(first, first.plusDays(6), zone)
                }
                "Mês" -> fromDates(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), zone)
                else -> null
            }
    }
}
