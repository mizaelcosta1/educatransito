package br.com.educatransito.reports

import br.com.educatransito.data.CitizenEntity
import br.com.educatransito.data.OrientationEntity
import br.com.educatransito.data.OrientationRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReportSummaryTest {
    @Test fun countsOnlyActiveRecordsAndDeduplicatesCategoriesWithinEachAction() {
        val first = reportRecord(id = 1, occurredAt = 3_000)
        val second = reportRecord(id = 2, occurredAt = 1_000).let {
            it.copy(orientation = it.orientation.copy(additionalTopics = "[\"Cinto\",\"Pedestres\",\"Pedestres\"]"))
        }
        val deleted = reportRecord(id = 3).let { it.copy(orientation = it.orientation.copy(deletedAt = 4_000)) }
        val summary = ReportSummary.from(listOf(first, deleted, second))
        assertEquals(2, summary.total)
        assertEquals(listOf(2L, 1L), summary.records.map { it.orientation.id })
        assertEquals(mapOf("Motorista" to 2), summary.audienceCounts)
        assertEquals(mapOf("Cinto" to 2, "Pedestres" to 1), summary.topicCounts)
    }

    @Test fun emptyPeriodsHaveNoInventedCounts() {
        val summary = ReportSummary.from(emptyList())
        assertEquals(0, summary.total)
        assertEquals(emptyMap<String, Int>(), summary.topicCounts)
        assertEquals(emptyMap<String, Int>(), summary.audienceCounts)
    }
}

internal fun reportRecord(id: Long = 1, occurredAt: Long = 1_000): OrientationRecord = OrientationRecord(
    orientation = OrientationEntity(
        id = id,
        protocol = "EDU-2026-${id.toString().padStart(6, '0')}",
        protocolYear = 2026,
        protocolSequence = id,
        citizenId = id,
        vehiclePlate = "ABC1D23",
        occurredAt = occurredAt,
        location = "Praça de teste",
        audience = "Motorista",
        primaryTopic = "Cinto",
        additionalTopics = "[]",
        description = "RELATO DE TESTE: orientação educativa realizada.",
        agentNotes = "Observação exclusiva do relatório detalhado."
    ),
    citizen = CitizenEntity(id = id, fullName = "Cidadão fictício para teste", normalizedName = "cidadao ficticio para teste"),
    vehicle = null,
    attachments = emptyList()
)
