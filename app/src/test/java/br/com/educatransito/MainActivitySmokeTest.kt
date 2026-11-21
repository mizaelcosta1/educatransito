package br.com.educatransito

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode

/** Exercises the real Activity, Compose controls, ViewModel and Room save transaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun saveFromFormThenReopenInHistory() {
        compose.onNodeWithText("NOVA ORIENTAÇÃO").assertIsDisplayed()
        screenshot("home-screen.png")
        compose.onNodeWithText("NOVA ORIENTAÇÃO").performClick()

        enter("Placa (opcional)", "ABC1D23")
        enter("Nome do cidadão (opcional)", "Pessoa fictícia de teste")
        enter("Local da abordagem *", "Praça de teste")
        compose.onNodeWithText("Orientação principal *").performScrollTo().performClick()
        compose.onNodeWithText("Uso correto do cinto de segurança").performClick()
        enter("Relato da orientação *", "Relato fictício: explicação sobre o uso correto do cinto de segurança.")
        compose.onNodeWithText("ANEXAR IMAGEM").assertExists()
        compose.onNodeWithText("ANEXAR VÍDEO").assertExists()
        compose.onNodeWithText("SALVAR ORIENTAÇÃO").performScrollTo().assertIsEnabled()
        screenshot("new-orientation-screen.png")
        compose.onNodeWithText("SALVAR ORIENTAÇÃO").performClick()

        awaitText("Orientação salva")
        compose.onNodeWithText("Pessoa fictícia de teste").assertExists()
        compose.onNodeWithText("ABC1D23").assertExists()
        screenshot("saved-orientation-screen.png")

        // Recreation exercises SavedStateHandle and repopulation from the persisted record.
        compose.activityRule.scenario.recreate()
        awaitText("Orientação salva")
        compose.onNodeWithText("SALVAR RELATÓRIO SIMPLES").assertExists().assertIsEnabled()
        compose.onNodeWithText("SALVAR RELATÓRIO DETALHADO").assertExists().assertIsEnabled()
        compose.onNodeWithText("Voltar").performClick()

        awaitText("Histórico de orientações")
        compose.onNodeWithText("Nome ou número do protocolo").performTextInput("Pessoa fictícia")
        awaitText("1 orientação(ões) encontrada(s)")
        compose.onNodeWithText("Pessoa fictícia de teste").performClick()
        awaitText("Orientação salva")
        compose.onNodeWithText("Relato fictício: explicação sobre o uso correto do cinto de segurança.").assertExists()
    }

    private fun enter(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextInput(text)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun screenshot(name: String) {
        // Robolectric does not schedule PixelCopy's forced redraw reliably. Draw the
        // actual decor hierarchy to a native bitmap after Compose has become idle.
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            check(decor.width > 0 && decor.height > 0)
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also {
                decor.draw(Canvas(it))
            }
        }
        val folder = File("build/qa").apply { mkdirs() }
        try {
            File(folder, name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
