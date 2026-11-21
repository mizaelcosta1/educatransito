package br.com.educatransito.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OrientationRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: OrientationRepository
    private val timestamp = Instant.parse("2026-11-20T15:30:00Z").toEpochMilli()
    private val databaseNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = OrientationRepository(database) { timestamp }
    }

    @After
    fun tearDown() {
        database.close()
        databaseNames.forEach(context::deleteDatabase)
    }

    private fun draft(
        name: String = "Pessoa de teste",
        attachments: List<PendingAttachment> = emptyList()
    ) = OrientationDraft(
        plate = "abc-1d23",
        make = "Marca de teste",
        model = "Modelo de teste",
        color = "Azul",
        citizenName = name,
        location = "Local de teste",
        primaryTopic = "Uso correto do cinto de segurança",
        additionalTopics = listOf("Respeito à faixa de pedestres"),
        description = "Relato de teste da ação educativa.",
        attachments = attachments
    )

    private fun attachment(id: String = UUID.randomUUID().toString()) = PendingAttachment(
        id, "$id.jpg", "image/jpeg", "imagem de teste.jpg", 10_240
    )

    @Test
    fun concurrentSavesAllocateDistinctMonotonicProtocols() = runBlocking {
        val saved = coroutineScope {
            (1..16).map { async(Dispatchers.IO) { repository.save(draft(name = "Pessoa $it")) } }.awaitAll()
        }
        assertEquals(16, saved.map { it.orientation.protocol }.toSet().size)
        assertEquals((1L..16L).toList(), saved.map { it.orientation.protocolSequence }.sorted())
        assertEquals(16, repository.observeAll().first().size)
        saved.forEach {
            assertEquals("ABC1D23", it.orientation.vehiclePlate)
            assertEquals(1, database.orientationDao().auditFor(it.orientation.id).size)
        }
    }

    @Test
    fun failedAttachmentInsertRollsBackCitizenRecordAndCounter() = runBlocking {
        val shared = attachment()
        repository.save(draft(attachments = listOf(shared)))
        val error = runCatching { repository.save(draft(attachments = listOf(shared))) }.exceptionOrNull()
        assertNotNull("A duplicate primary key must reject the complete second save", error)
        assertEquals(1, repository.observeAll().first().size)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM citizens").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        val next = repository.save(draft())
        assertEquals("EDU-2026-000002", next.orientation.protocol)
    }

    @Test
    fun sameNameDoesNotMergeCitizensAndVehicleSnapshotsRemainStable() = runBlocking {
        val first = repository.save(draft())
        val second = repository.save(draft().copy(make = "Outra marca", color = "Branco"))
        assertNotEquals(first.citizen?.id, second.citizen?.id)
        val reopened = requireNotNull(repository.getRecord(first.orientation.id))
        assertEquals("Marca de teste", reopened.orientation.vehicleMake)
        assertEquals("Azul", reopened.orientation.vehicleColor)
        assertEquals(listOf("Respeito à faixa de pedestres"), reopened.additionalTopicLabels)
    }

    @Test
    fun anonymousOrientationDoesNotCreateCitizen() = runBlocking {
        val result = repository.save(draft(name = "").copy(plate = "", audience = "Pedestre"))
        assertNull(result.citizen)
        assertNull(result.orientation.citizenId)
        assertNull(result.vehicle)
    }

    @Test
    fun addingAttachmentsPreservesProtocolAndCreatesTraceableAudit() = runBlocking {
        val original = repository.save(draft())
        val withImage = repository.addAttachments(original.orientation.id, listOf(attachment()))
        assertEquals(original.orientation.protocol, withImage.orientation.protocol)
        assertEquals(original.orientation.occurredAt, withImage.orientation.occurredAt)
        assertEquals(1, withImage.attachments.size)
        val log = database.orientationDao().auditFor(original.orientation.id)
        assertEquals(listOf("CREATE", "ADD_ATTACHMENTS"), log.map { it.action })
        assertTrue(requireNotNull(log.last().afterJson).contains(withImage.attachments.single().id))
    }

    @Test
    fun attachmentsAndTextSurviveDatabaseReopen() = runBlocking {
        database.close()
        val name = "reopen-${UUID.randomUUID()}.db".also(databaseNames::add)
        database = diskDatabase(name)
        repository = OrientationRepository(database) { timestamp }
        val saved = repository.save(draft(attachments = listOf(attachment())))
        database.close()
        database = diskDatabase(name)
        repository = OrientationRepository(database) { timestamp }
        val restored = requireNotNull(repository.getByProtocol(saved.orientation.protocol))
        assertEquals(saved, restored)
        assertEquals("EDU-2026-000002", repository.save(draft()).orientation.protocol)
    }

    @Test
    fun migrationPreservesV1RecordsAndCounterAdvancesPastExistingProtocols() = runBlocking {
        database.close()
        val name = "migration-${UUID.randomUUID()}.db".also(databaseNames::add)
        createLegacyDatabase(name)
        database = diskDatabase(name)
        repository = OrientationRepository(database) { timestamp }
        val original = requireNotNull(repository.getRecord(1))
        assertEquals("EDU-2026-000125", original.orientation.protocol)
        assertEquals("Relato legado", original.orientation.description)
        assertEquals("Marca legada", original.orientation.vehicleMake)
        assertEquals("Pessoa legada", original.citizen?.fullName)
        assertTrue(original.attachments.isEmpty())
        assertEquals("EDU-2026-000126", repository.save(draft()).orientation.protocol)
    }

    @Test
    fun attachmentCountLimitRejectsWholeAddition() = runBlocking {
        val original = repository.save(draft(attachments = (1..10).map { attachment() }))
        val error = runCatching { repository.addAttachments(original.orientation.id, listOf(attachment())) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(10, repository.getRecord(original.orientation.id)?.attachments?.size)
        assertEquals(1, database.orientationDao().auditFor(original.orientation.id).size)
    }

    @Test
    fun validationRejectsUnsafeAttachmentPathsAndEmptyNarrative() {
        assertTrue(runCatching { draft().copy(description = " ").validate() }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            AttachmentPolicy.validate(listOf(attachment().copy(localFileName = "../other-file")))
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            AttachmentPolicy.validate(listOf(attachment().copy(sizeBytes = AttachmentPolicy.MAX_IMAGE_BYTES + 1)))
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals("joao da silva", OrientationRepository.normalizeName(" João   da Silva "))
    }

    private fun diskDatabase(name: String) = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_1_2).build()

    /** Recreates the released v1 schema without relying on a previously exported Room JSON file. */
    private fun createLegacyDatabase(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            listOf(
                "CREATE TABLE citizens (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fullName TEXT NOT NULL, normalizedName TEXT NOT NULL, phone TEXT)",
                "CREATE INDEX index_citizens_normalizedName ON citizens (normalizedName)",
                "CREATE TABLE vehicles (plate TEXT NOT NULL PRIMARY KEY, make TEXT NOT NULL, model TEXT NOT NULL, color TEXT NOT NULL)",
                "CREATE UNIQUE INDEX index_vehicles_plate ON vehicles (plate)",
                "CREATE TABLE orientations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, protocol TEXT NOT NULL, protocolYear INTEGER NOT NULL, protocolSequence INTEGER NOT NULL, citizenId INTEGER, vehiclePlate TEXT, occurredAt INTEGER NOT NULL, location TEXT NOT NULL, audience TEXT NOT NULL, primaryTopic TEXT NOT NULL, additionalTopics TEXT NOT NULL, description TEXT NOT NULL, agentNotes TEXT NOT NULL, signatureReceived INTEGER NOT NULL, deletedAt INTEGER)",
                "CREATE UNIQUE INDEX index_orientations_protocol ON orientations (protocol)",
                "CREATE INDEX index_orientations_vehiclePlate ON orientations (vehiclePlate)",
                "CREATE INDEX index_orientations_citizenId ON orientations (citizenId)",
                "CREATE INDEX index_orientations_occurredAt ON orientations (occurredAt)",
                "CREATE TABLE audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, orientationId INTEGER NOT NULL, changedAt INTEGER NOT NULL, action TEXT NOT NULL, beforeJson TEXT, afterJson TEXT, reason TEXT NOT NULL)",
                "CREATE INDEX index_audit_log_orientationId ON audit_log (orientationId)",
                "CREATE INDEX index_audit_log_changedAt ON audit_log (changedAt)",
                "CREATE TABLE protocol_counters (year INTEGER NOT NULL PRIMARY KEY, lastSequence INTEGER NOT NULL)",
                "INSERT INTO citizens VALUES (1, 'Pessoa legada', 'pessoa legada', NULL)",
                "INSERT INTO vehicles VALUES ('ABC1D23', 'Marca legada', 'Modelo legado', 'Azul')",
                "INSERT INTO orientations VALUES (1, 'EDU-2026-000125', 2026, 125, 1, 'ABC1D23', $timestamp, 'Local legado', 'Motorista', 'Uso do cinto', '', 'Relato legado', '', 0, NULL)",
                // A restored or inconsistent old counter must not cause a duplicate protocol.
                "INSERT INTO protocol_counters VALUES (2026, 2)"
            ).forEach(legacy::execSQL)
            legacy.version = 1
        }
    }
}
