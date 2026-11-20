package br.com.educatransito.domain

/** Deterministic protocol formatter. The database transaction owns sequence allocation. */
object ProtocolGenerator {
    fun format(year: Int, sequence: Long): String {
        require(year in 2000..9999) { "Ano inválido" }
        require(sequence > 0) { "Sequência deve ser positiva" }
        return "EDU-$year-${sequence.toString().padStart(6, '0')}"
    }
    fun normalizePlate(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }
}
