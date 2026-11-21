package br.com.educatransito

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import br.com.educatransito.data.AppDatabase
import br.com.educatransito.data.OrientationDraft
import br.com.educatransito.data.OrientationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class OrientationViewModelTest {
    private lateinit var app: Application
    private lateinit var database: AppDatabase
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        resetDatabaseSingleton()
        app.deleteDatabase("educatransito.db")
        database = AppDatabase.get(app)
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        database.close()
        resetDatabaseSingleton()
        app.deleteDatabase("educatransito.db")
        Dispatchers.resetMain()
    }

    private fun model(state: SavedStateHandle = SavedStateHandle()): OrientationViewModel {
        val model = OrientationViewModel(app, state)
        stores += ViewModelStore().also { it.put("orientation", model) }
        return model
    }

    private fun validDraft() = OrientationDraft(
        plate = "ABC1D23",
        citizenName = "Pessoa de teste",
        location = "Local de teste",
        primaryTopic = "Uso correto do cinto de segurança",
        description = "Relato de teste da orientação educativa."
    )

    @Test
    fun doubleTapSavesOnceAndCancellingPdfKeepsSavedOrientation() = runBlocking {
        val state = SavedStateHandle()
        val vm = model(state)
        vm.updateDraft { validDraft() }
        vm.save(true)
        vm.save(true)
        waitUntil { vm.busy == null }
        val records = OrientationRepository(database).observeAll().first()
        assertEquals(1, records.size)
        assertEquals(1L, records.single().orientation.protocolSequence)
        assertEquals("detail", vm.page)
        assertEquals(OrientationDraft(), vm.draft)
        assertNotNull(vm.reportToCreate)

        vm.reportPickerLaunched()
        vm.exportPdf(null)
        assertNull(state.get<String>("pendingReport"))
        assertTrue(vm.message.orEmpty().contains("continua salva"))
        assertEquals(1, OrientationRepository(database).observeAll().first().size)
    }

    @Test
    fun savedSelectionReloadsAfterViewModelRecreation() = runBlocking {
        val first = model()
        first.updateDraft { validDraft() }
        first.save()
        waitUntil { first.busy == null }
        val id = first.selectedId
        assertTrue(id > 0)
        val reopened = model(SavedStateHandle(mapOf("selectedId" to id, "page" to "detail")))
        waitUntil { reopened.selectedRecord != null }
        assertEquals(first.selectedRecord, reopened.selectedRecord)
        assertEquals("detail", reopened.page)
        assertEquals(1, OrientationRepository(database).observeAll().first().size)
    }

    @Test
    fun savedStateRestoresUnsavedDraftAndValidationRetainsIt() = runBlocking {
        val state = SavedStateHandle()
        val first = model(state)
        val draft = validDraft().copy(location = "", additionalTopics = listOf("Respeito à faixa de pedestres"))
        first.updateDraft { draft }
        val restored = model(SavedStateHandle(mapOf("draft" to state.get<String>("draft"))))
        assertEquals(draft, restored.draft)
        restored.save()
        waitUntil { restored.busy == null }
        assertEquals(draft, restored.draft)
        assertTrue(restored.message.orEmpty().contains("local"))
        assertTrue(OrientationRepository(database).observeAll().first().isEmpty())
    }

    private suspend fun waitUntil(condition: () -> Boolean) = withTimeout(10_000) {
        // Room's executor is real; yield to it while the test Main dispatcher resumes UI state immediately.
        while (!condition()) delay(10)
    }

    private fun resetDatabaseSingleton() {
        // Isolates the production singleton across Robolectric's reused JVM without adding test-only app APIs.
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            (get(null) as? AppDatabase)?.close()
            set(null, null)
        }
    }
}
