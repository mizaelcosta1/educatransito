package br.com.educatransito.reports

import br.com.educatransito.data.OrientationRecord

/** Only saved, active records contribute to a report. Counts are actions, not unique people. */
data class ReportSummary(
    val records: List<OrientationRecord>,
    val audienceCounts: Map<String, Int>,
    val topicCounts: Map<String, Int>
) {
    val total: Int get() = records.size

    companion object {
        fun from(records: List<OrientationRecord>): ReportSummary {
            val active = records.filter { it.orientation.deletedAt == null }
                .sortedWith(compareBy({ it.orientation.occurredAt }, { it.orientation.protocol }))
            val audiences = active.groupingBy { it.orientation.audience }.eachCount().toSortedMap()
            val topics = active.flatMap { record ->
                (listOf(record.orientation.primaryTopic) + record.additionalTopicLabels)
                    .map(String::trim).filter(String::isNotBlank).distinct()
            }.groupingBy { it }.eachCount().toSortedMap()
            return ReportSummary(active, audiences, topics)
        }
    }
}
