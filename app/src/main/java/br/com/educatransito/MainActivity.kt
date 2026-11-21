package br.com.educatransito

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.educatransito.data.*
import br.com.educatransito.domain.EducationalTopics
import br.com.educatransito.domain.ProtocolGenerator
import br.com.educatransito.domain.ReportPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Navy = Color(0xFF102A43)
private val Blue = Color(0xFF1F5A85)
private val Gold = Color(0xFFE6A817)
private val Muted = Color(0xFF526579)
private val DateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy • HH:mm", Locale("pt", "BR"))
    .withZone(ZoneId.systemDefault())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EducatransitoApp() }
    }
}

private class LocalDocumentPicker : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducatransitoApp(vm: OrientationViewModel = viewModel()) {
    val context = LocalContext.current
    val records by vm.records.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val mediaPicker = rememberLauncherForActivityResult(LocalDocumentPicker()) { vm.importSelectedMedia(it) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { vm.exportPdf(it) }
    val copyPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.exportAttachment(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { message -> snackbar.showSnackbar(message); vm.clearMessage() }
    }
    LaunchedEffect(vm.reportToCreate) {
        vm.reportToCreate?.let {
            vm.reportPickerLaunched()
            try { pdfPicker.launch(it) } catch (_: ActivityNotFoundException) {
                vm.showMessage("O seletor de arquivos não está disponível neste aparelho.")
            }
        }
    }
    LaunchedEffect(vm.mediaToCreate) {
        vm.mediaToCreate?.let { (name, mime) ->
            vm.mediaCopyPickerLaunched()
            try {
                copyPicker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = mime
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, name)
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                })
            } catch (_: ActivityNotFoundException) { vm.showMessage("O seletor de arquivos não está disponível neste aparelho.") }
        }
    }
    val pickMedia: (String, Long?) -> Unit = { kind, id ->
        if (vm.prepareMediaPicker(kind, id)) {
            try { mediaPicker.launch(arrayOf(kind + "/*")) }
            catch (_: ActivityNotFoundException) { vm.showMessage("O seletor de arquivos não está disponível neste aparelho.") }
        }
    }
    BackHandler(enabled = vm.page != "home" || vm.busy != null) {
        if (vm.busy == null) vm.navigate(if (vm.page == "detail") "history" else "home")
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Gold, background = Color(0xFFF4F7FA))) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            CenterAlignedTopAppBar(
                title = { Text("EDUCATRÂNSITO", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp) },
                navigationIcon = {
                    if (vm.page != "home") TextButton(enabled = vm.busy == null, onClick = {
                        vm.navigate(if (vm.page == "detail") "history" else "home")
                    }) { Text("Voltar", color = Color.White) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Navy)
            )
        }) { padding ->
            val modifier = Modifier.padding(padding).imePadding()
            when (vm.page) {
                "new" -> NewOrientationScreen(vm, modifier, onMedia = { pickMedia(it, null) })
                "plate" -> HistoryScreen(records, vm.loaded, true, modifier, vm::openRecord)
                "history" -> HistoryScreen(records, vm.loaded, false, modifier, vm::openRecord)
                "detail" -> {
                    val record = records.firstOrNull { it.orientation.id == vm.selectedId } ?: vm.selectedRecord
                    if (record != null) RecordScreen(record, modifier,
                        onReport = { vm.requestReport(listOf(record), it, "Orientação " + record.orientation.protocol) },
                        onMedia = { pickMedia(it, record.orientation.id) },
                        onOpen = { attachment ->
                            try {
                                val file = vm.media.file(attachment.localFileName)
                                if (!file.isFile) vm.showMessage("Este anexo não está mais disponível no aparelho.")
                                else openDocument(context, FileProvider.getUriForFile(context, context.packageName + ".files", file),
                                    attachment.mimeType, vm::showMessage)
                            } catch (_: Exception) { vm.showMessage("Não foi possível abrir este anexo.") }
                        },
                        onCopy = vm::requestAttachmentCopy,
                        lastPdf = vm.lastPdfUri?.let { uri -> { openDocument(context, uri, "application/pdf", vm::showMessage) } }
                    ) else LoadingOrEmpty("Carregando orientação…", modifier)
                }
                "reports" -> ReportsScreen(records, vm.loaded, modifier,
                    onReport = { selected, detailed, label -> vm.requestReport(selected, detailed, label) },
                    onOpenPdf = vm.lastPdfUri?.let { uri -> { openDocument(context, uri, "application/pdf", vm::showMessage) } })
                else -> HomeScreen(records.size, modifier, vm::navigate)
            }
        }
        vm.busy?.let { status ->
            Dialog(onDismissRequest = {}) {
                Surface(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(status)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(count: Int, modifier: Modifier, onSelect: (String) -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Histórico de orientações educativas", style = MaterialTheme.typography.headlineSmall, color = Navy, fontWeight = FontWeight.Bold)
        Text("Registre o relato, guarde anexos e salve relatórios no celular.", color = Muted)
        Surface(color = Color(0xFFE2F3E8), shape = MaterialTheme.shapes.small) {
            Text("OFFLINE  •  " + count + " orientação(ões) salva(s)", color = Color(0xFF176B3A),
                modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
        }
        HomeAction("NOVA ORIENTAÇÃO", "Relato, imagens e vídeos", Icons.Default.Assignment) { onSelect("new") }
        HomeAction("CONSULTAR PLACA", "Histórico do veículo", Icons.Default.DirectionsCar) { onSelect("plate") }
        HomeAction("HISTÓRICO DE ORIENTAÇÕES", "Cidadão, protocolo e anexos", Icons.Default.History) { onSelect("history") }
        HomeAction("FICHAS E RELATÓRIOS", "Salvar PDF simples ou detalhado", Icons.Default.BarChart) { onSelect("reports") }
        Text("Finalidade exclusivamente educativa. O histórico de uma placa não comprova que o condutor atual recebeu orientações anteriores.",
            color = Muted, fontSize = 12.sp)
        Text("Versão 0.2.0 • desenvolvimento", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = Blue, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Navy, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewOrientationScreen(vm: OrientationViewModel, modifier: Modifier, onMedia: (String) -> Unit) {
    val form = vm.draft
    var expanded by remember { mutableStateOf(false) }
    var extras by rememberSaveable { mutableStateOf(false) }
    var vehicleDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Nova orientação")
        Text("Preencha local, orientação principal e relato. O protocolo será gerado ao salvar.", color = Muted)
        Input(form.plate, "Placa (opcional)", 10, capitalization = KeyboardCapitalization.Characters) { text ->
            vm.updateDraft { it.copy(plate = ProtocolGenerator.normalizePlate(text).take(7)) }
        }
        TextButton(onClick = { vehicleDetails = !vehicleDetails }) { Text(if (vehicleDetails) "Ocultar dados do veículo" else "Adicionar marca, modelo e cor") }
        if (vehicleDetails) {
            Input(form.make, "Marca", 100) { value -> vm.updateDraft { it.copy(make = value) } }
            Input(form.model, "Modelo", 100) { value -> vm.updateDraft { it.copy(model = value) } }
            Input(form.color, "Cor", 100) { value -> vm.updateDraft { it.copy(color = value) } }
        }
        Input(form.citizenName, "Nome do cidadão (opcional)", 200) { value -> vm.updateDraft { it.copy(citizenName = value) } }
        Input(form.location, "Local da abordagem *", 300, singleLine = false) { value -> vm.updateDraft { it.copy(location = value) } }
        Text("Público orientado", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Motorista", "Motociclista", "Pedestre", "Ciclista").forEach { label ->
                FilterChip(selected = form.audience == label, onClick = { vm.updateDraft { it.copy(audience = label) } }, label = { Text(label) })
            }
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(value = form.primaryTopic, onValueChange = {}, readOnly = true,
                label = { Text("Orientação principal *") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                EducationalTopics.defaults.forEach { topic ->
                    DropdownMenuItem(text = { Text(topic) }, onClick = {
                        vm.updateDraft { it.copy(primaryTopic = topic, additionalTopics = it.additionalTopics - topic) }
                        expanded = false
                    })
                }
            }
        }
        TextButton(onClick = { extras = !extras }) { Text("Orientações complementares (" + form.additionalTopics.size + ")") }
        if (extras) EducationalTopics.defaults.filter { it != form.primaryTopic }.forEach { topic ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = topic in form.additionalTopics, onCheckedChange = { selected ->
                    vm.updateDraft { it.copy(additionalTopics = if (selected) it.additionalTopics + topic else it.additionalTopics - topic) }
                })
                Text(topic, modifier = Modifier.weight(1f))
            }
        }
        Input(form.description, "Relato da orientação *", 20_000, singleLine = false, minLines = 4) { value ->
            vm.updateDraft { it.copy(description = value) }
        }
        AttachmentButtons(form.attachments.size, onMedia)
        form.attachments.forEach { attachment ->
            AttachmentCard(attachment.displayName, attachment.mimeType, attachment.sizeBytes) {
                TextButton(onClick = { vm.removeDraftAttachment(attachment.id) }) { Text("Remover") }
            }
        }
        Input(form.agentNotes, "Observações do agente (opcional)", 10_000, singleLine = false, minLines = 2) { value ->
            vm.updateDraft { it.copy(agentNotes = value) }
        }
        Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("SALVAR ORIENTAÇÃO") }
        Text("Os botões abaixo salvam a orientação antes de gerar o PDF.", color = Muted, fontSize = 13.sp)
        ReportButtons(onSimple = { vm.save(false) }, onDetailed = { vm.save(true) })
        TextButton(onClick = { confirmDiscard = true }) { Text("Limpar formulário") }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text("Limpar formulário?") }, text = { Text("Os campos e os anexos ainda não salvos serão removidos.") },
        confirmButton = { TextButton(onClick = { vm.discardDraft(); confirmDiscard = false }) { Text("Limpar") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Manter") } })
}

@Composable
private fun Input(value: String, label: String, maxLength: Int, singleLine: Boolean = true, minLines: Int = 1,
                  capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.take(maxLength)) }, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = capitalization))
}

@Composable
private fun AttachmentButtons(count: Int, onMedia: (String) -> Unit) {
    Text("Anexos do relato (" + count + "/10)", fontWeight = FontWeight.SemiBold)
    OutlinedButton(onClick = { onMedia("image") }, enabled = count < 10, modifier = Modifier.fillMaxWidth()) { Text("ANEXAR IMAGEM") }
    OutlinedButton(onClick = { onMedia("video") }, enabled = count < 10, modifier = Modifier.fillMaxWidth()) { Text("ANEXAR VÍDEO") }
    Text("Imagem até 20 MB • vídeo até 200 MB. Selecione arquivos salvos no aparelho.", fontSize = 12.sp, color = Muted)
}

@Composable
private fun AttachmentCard(name: String, mime: String, size: Long, actions: @Composable RowScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text((if (mime.startsWith("image/")) "Imagem" else "Vídeo") + " • " + formatSize(size), color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = actions)
        }
    }
}

@Composable
private fun ReportButtons(enabled: Boolean = true, onSimple: () -> Unit, onDetailed: () -> Unit) {
    OutlinedButton(onClick = onSimple, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("SALVAR RELATÓRIO SIMPLES") }
    OutlinedButton(onClick = onDetailed, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("SALVAR RELATÓRIO DETALHADO") }
}

@Composable
private fun HistoryScreen(records: List<OrientationRecord>, loaded: Boolean, byPlate: Boolean,
                          modifier: Modifier, onOpen: (OrientationRecord) -> Unit) {
    var query by rememberSaveable(byPlate) { mutableStateOf("") }
    val matches = remember(records, query, byPlate) {
        if (byPlate) {
            val plate = ProtocolGenerator.normalizePlate(query)
            if (plate.length != 7) emptyList() else records.filter { it.orientation.vehiclePlate == plate }
        } else {
            val normalized = OrientationRepository.normalizeName(query)
            records.filter { normalized.isBlank() ||
                it.orientation.protocol.contains(query.trim(), ignoreCase = true) ||
                OrientationRepository.normalizeName(it.citizen?.fullName.orEmpty()).contains(normalized) }
        }
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(if (byPlate) "Consultar placa" else "Histórico de orientações") }
        item {
            Input(query, if (byPlate) "Digite a placa completa" else "Nome ou número do protocolo", if (byPlate) 10 else 200) {
                query = if (byPlate) ProtocolGenerator.normalizePlate(it).take(7) else it
            }
        }
        item {
            Text(if (byPlate) "O histórico da placa não comprova que o condutor atual recebeu as orientações anteriores."
                else "Nomes iguais aparecem em registros separados. Toque no protocolo para conferir os dados e os anexos.",
                color = Muted, fontSize = 13.sp)
        }
        item { Text(if (loaded) matches.size.toString() + " orientação(ões) encontrada(s)" else "Carregando histórico…", fontWeight = FontWeight.SemiBold) }
        if (loaded && matches.isEmpty()) item {
            Text(if (byPlate && query.length < 7) "Digite os 7 caracteres da placa para consultar."
                else "Nenhuma orientação encontrada.", color = Muted)
        }
        items(matches, key = { it.orientation.id }) { record ->
            Card(onClick = { onOpen(record) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(record.orientation.protocol, fontWeight = FontWeight.Bold, color = Blue)
                    Text(DateFormat.format(Instant.ofEpochMilli(record.orientation.occurredAt)), fontSize = 13.sp, color = Muted)
                    Text(record.orientation.primaryTopic)
                    Text(record.citizen?.fullName ?: "Cidadão não identificado", fontSize = 14.sp)
                    record.citizen?.let { Text("Cadastro local nº " + it.id, fontSize = 12.sp, color = Muted) }
                    Text((record.orientation.vehiclePlate ?: "Sem placa") + " • " + record.attachments.size + " anexo(s)", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RecordScreen(record: OrientationRecord, modifier: Modifier, onReport: (Boolean) -> Unit,
                         onMedia: (String) -> Unit, onOpen: (AttachmentEntity) -> Unit,
                         onCopy: (AttachmentEntity) -> Unit, lastPdf: (() -> Unit)?) {
    val item = record.orientation
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Orientação salva")
        Surface(color = Color(0xFFE2F3E8), shape = MaterialTheme.shapes.medium) {
            Text(item.protocol, color = Color(0xFF176B3A), modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
        }
        Detail("Data e horário", DateFormat.format(Instant.ofEpochMilli(item.occurredAt)))
        Detail("Cidadão", record.citizen?.fullName ?: "Não identificado")
        record.citizen?.let { Detail("Cadastro local", it.id.toString()) }
        Detail("Placa", item.vehiclePlate ?: "Não informada")
        val vehicle = listOf(item.vehicleMake, item.vehicleModel, item.vehicleColor).filter(String::isNotBlank).joinToString(" • ")
        if (vehicle.isNotBlank()) Detail("Veículo", vehicle)
        Detail("Local", item.location)
        Detail("Público", item.audience)
        Detail("Orientação principal", item.primaryTopic)
        if (record.additionalTopicLabels.isNotEmpty()) Detail("Orientações complementares", record.additionalTopicLabels.joinToString("\n"))
        Detail("Relato", item.description)
        AttachmentButtons(record.attachments.size, onMedia)
        record.attachments.sortedBy { it.addedAt }.forEach { attachment ->
            AttachmentCard(attachment.displayName, attachment.mimeType, attachment.sizeBytes) {
                TextButton(onClick = { onOpen(attachment) }) { Text("Abrir") }
                TextButton(onClick = { onCopy(attachment) }) { Text("Salvar cópia") }
            }
        }
        if (item.agentNotes.isNotBlank()) Detail("Observações", item.agentNotes)
        HorizontalDivider()
        ReportButtons(onSimple = { onReport(false) }, onDetailed = { onReport(true) })
        if (lastPdf != null) TextButton(onClick = lastPdf, modifier = Modifier.fillMaxWidth()) { Text("ABRIR ÚLTIMO PDF SALVO") }
        Text("O PDF detalhado inclui imagens. Os vídeos aparecem identificados e podem ser abertos ou salvos separadamente.",
            color = Muted, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportsScreen(records: List<OrientationRecord>, loaded: Boolean, modifier: Modifier,
                          onReport: (List<OrientationRecord>, Boolean, String) -> Unit, onOpenPdf: (() -> Unit)?) {
    var periodName by rememberSaveable { mutableStateOf("Todos") }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
    var start by rememberSaveable { mutableStateOf(today) }
    var end by rememberSaveable { mutableStateOf(today) }
    val periodResult = remember(periodName, start, end) { runCatching {
        if (periodName == "Escolher período") ReportPeriod.custom(start, end) else ReportPeriod.preset(periodName)
    } }
    val period = periodResult.getOrNull()
    val selected = remember(records, period, periodResult.isSuccess) {
        if (periodResult.isFailure) emptyList() else records.filter { period == null || period.contains(it.orientation.occurredAt) }
    }
    val label = period?.label ?: "Todos os registros salvos"
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Relatórios de atividades")
        Text("Escolha o período e salve o PDF em uma pasta do celular.", color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos", "Hoje", "Semana", "Mês", "Escolher período").forEach { name ->
                FilterChip(selected = periodName == name, onClick = { periodName = name }, label = { Text(name) })
            }
        }
        if (periodName == "Escolher período") {
            Input(start, "Data inicial (DD/MM/AAAA)", 10) { start = it }
            Input(end, "Data final (DD/MM/AAAA)", 10) { end = it }
        }
        periodResult.exceptionOrNull()?.let { Text(it.message ?: "Revise as datas.", color = MaterialTheme.colorScheme.error) }
        Text(label, color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (loaded) selected.size.toString() + " orientações" else "Carregando…", fontSize = 26.sp, color = Navy, fontWeight = FontWeight.Bold)
                listOf("Motorista", "Motociclista", "Pedestre", "Ciclista").forEach { audience ->
                    Text(audience + ": " + selected.count { it.orientation.audience == audience })
                }
            }
        }
        Text("Simples: totais por público e categoria, com histórico cronológico.", color = Muted)
        Text("Detalhado: dados de cada orientação, relato completo, observações, imagens e lista dos vídeos.", color = Muted)
        ReportButtons(enabled = loaded && selected.isNotEmpty() && periodResult.isSuccess,
            onSimple = { onReport(selected, false, label) }, onDetailed = { onReport(selected, true, label) })
        if (loaded && selected.isEmpty() && periodResult.isSuccess) Text("Salve uma orientação ou escolha outro período para gerar o relatório.", color = Muted)
        if (onOpenPdf != null) TextButton(onClick = onOpenPdf, modifier = Modifier.fillMaxWidth()) { Text("ABRIR ÚLTIMO PDF SALVO") }
        Text("Os totais por público contam atendimentos registrados. Uma pessoa pode aparecer em mais de uma orientação.", fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = Navy)
    }
}

@Composable
private fun SectionTitle(title: String) { Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy) }

@Composable
private fun LoadingOrEmpty(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

private fun formatSize(bytes: Long): String = if (bytes >= 1024 * 1024)
    String.format(Locale("pt", "BR"), "%.1f MB", bytes / (1024.0 * 1024.0)) else (bytes / 1024).coerceAtLeast(1).toString() + " KB"

private fun openDocument(context: Context, uri: Uri, mime: String, onError: (String) -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Documento EDUCATRÂNSITO", uri)
        })
    } catch (_: ActivityNotFoundException) {
        onError("Instale ou selecione um aplicativo capaz de abrir este tipo de arquivo.")
    } catch (_: Exception) { onError("Não foi possível abrir o arquivo selecionado.") }
}
