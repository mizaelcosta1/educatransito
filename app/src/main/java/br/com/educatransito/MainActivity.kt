package br.com.educatransito

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.educatransito.domain.EducationalTopics

private val Navy = Color(0xFF102A43)
private val Blue = Color(0xFF1F5A85)
private val Gold = Color(0xFFE6A817)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EducatransitoApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducatransitoApp() {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Gold, background = Color(0xFFF4F7FA))) {
        var page by remember { mutableStateOf("home") }
        Scaffold(containerColor = Color(0xFFF4F7FA), topBar = {
            CenterAlignedTopAppBar(title = { Text("EDUCATRÂNSITO", fontWeight = FontWeight.Bold, color = Color.White) }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Navy))
        }) { padding ->
            when (page) {
                "new" -> NewOrientationScreen(Modifier.padding(padding), onBack = { page = "home" })
                "plate" -> SearchScreen("CONSULTAR PLACA", "Digite a placa do veículo", Modifier.padding(padding), onBack = { page = "home" })
                "history" -> SearchScreen("HISTÓRICO DE ORIENTAÇÕES", "Nome ou número do protocolo", Modifier.padding(padding), onBack = { page = "home" })
                "reports" -> ReportsScreen(Modifier.padding(padding), onBack = { page = "home" })
                else -> HomeScreen(Modifier.padding(padding), onSelect = { page = it })
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Histórico de orientações educativas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Registro preventivo de ações de educação para o trânsito.", color = Color(0xFF526579))
        Surface(color = Color(0xFFE2F3E8), shape = MaterialTheme.shapes.small) { Text("  ● MODO OFFLINE  ", color = Color(0xFF176B3A), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(7.dp)) }
        HomeAction("NOVA ORIENTAÇÃO", "Registrar uma ação educativa", Icons.Default.Assignment) { onSelect("new") }
        HomeAction("CONSULTAR PLACA", "Localizar histórico de um veículo", Icons.Default.DirectionsCar) { onSelect("plate") }
        HomeAction("HISTÓRICO DE ORIENTAÇÕES", "Pesquisar por cidadão ou protocolo", Icons.Default.History) { onSelect("history") }
        HomeAction("FICHAS E RELATÓRIOS", "Comprovantes, impressão e estatísticas", Icons.Default.BarChart) { onSelect("reports") }
        Text("O histórico de um veículo não comprova que o atual condutor recebeu orientações anteriores.", color = Color(0xFF526579), fontSize = 12.sp)
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = Color(0xFFEAF1F7), shape = MaterialTheme.shapes.medium) { Icon(icon, null, tint = Blue, modifier = Modifier.padding(12.dp).size(28.dp)) }
            Column(Modifier.weight(1f)) { Text(title, color = Navy, fontWeight = FontWeight.Bold); Text(subtitle, color = Color(0xFF617386), fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewOrientationScreen(modifier: Modifier, onBack: () -> Unit) {
    var plate by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("Motorista") }
    var description by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nova orientação", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        OutlinedTextField(value = plate, onValueChange = { plate = it.uppercase().filter(Char::isLetterOrDigit).take(7) }, label = { Text("Placa do veículo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome completo do cidadão") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Local da abordagem") }, modifier = Modifier.fillMaxWidth())
        Text("Público orientado", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Motorista", "Motociclista", "Pedestre", "Ciclista").forEach { FilterChip(selected = audience == it, onClick = { audience = it }, label = { Text(it) }) } }
        Text("Orientação principal", fontWeight = FontWeight.SemiBold)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(value = topic, onValueChange = {}, readOnly = true, label = { Text("Selecione uma orientação") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                EducationalTopics.defaults.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { topic = item; expanded = false }) }
            }
        }
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição complementar / observações") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("SALVAR ORIENTAÇÃO") }
        Text("O cadastro e a persistência ainda estão em implementação.", color = Color(0xFF7A5A13), fontSize = 12.sp)
        TextButton(onClick = onBack) { Text("Voltar") }
    }
}

@Composable
private fun SearchScreen(title: String, hint: String, modifier: Modifier, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(hint) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("PESQUISAR") }
        Text("A consulta será habilitada após a conexão com o banco local.", color = Color(0xFF617386))
        TextButton(onClick = onBack) { Text("Voltar") }
    }
}

@Composable
private fun ReportsScreen(modifier: Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fichas e relatórios", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Comprovantes PDF • três por folha A4 • QR Code • relatórios por período")
        Text("A geração dos documentos será conectada aos registros locais na próxima etapa.", color = Color(0xFF617386))
        TextButton(onClick = onBack) { Text("Voltar") }
    }
}
