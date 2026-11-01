package com.namakkalmarket.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var welcomeTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
        MarketPriceWatchWorker.schedule(this)
        AppUpdateCheckWorker.schedule(this)
        welcomeTts = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val tamilResult = welcomeTts?.setLanguage(Locale("ta", "IN"))
                if (tamilResult != TextToSpeech.LANG_MISSING_DATA &&
                    tamilResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                    val greeting = if (java.time.LocalTime.now().hour < 12) {
                        "காலை வணக்கம்! நாமக்கல் உழவர் சந்தைக்கு உங்களை அன்புடன் வரவேற்கிறோம். இன்றைய காய்கறி விலைகளை அறிந்து பயன் பெறுங்கள்."
                    } else {
                        "மாலை வணக்கம்! நாமக்கல் உழவர் சந்தைக்கு உங்களை அன்புடன் வரவேற்கிறோம். இன்றைய காய்கறி விலைகளை அறிந்து பயன் பெறுங்கள்."
                    }
                    welcomeTts?.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "namakkal-welcome")
                }
            }
        }
        setContent { MarketScreen() }
    }

    override fun onDestroy() {
        welcomeTts?.stop()
        welcomeTts?.shutdown()
        welcomeTts = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SheetRepository(context) }
    var rows by remember { mutableStateOf(repo.cachedRows()) }
    var dateLabel by remember { mutableStateOf(repo.cachedDate()) }
    var selectedTab by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("விலைத் தகவலை ஏற்றுகிறது…") }
    var loading by remember { mutableStateOf(false) }
    var showRegistration by remember { mutableStateOf(false) }
    var registrationStatus by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val result = repo.load()
                rows = result.rows
                dateLabel = result.date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                status = if (result.fallback) "கடைசியாக கிடைத்த விலைத் தேதி" else "இன்றைய விலைத் தகவல்"
            } catch (e: Exception) {
                status = "Supabase விலைத் தரவைப் பெற முடியவில்லை: ${e.message ?: "மீண்டும் முயற்சிக்கவும்"}"
                rows = repo.cachedRows()
                dateLabel = repo.cachedDate()
            } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(
                title = { Column {
                    Text("நாமக்கல் உழவர் சந்தை", fontWeight = FontWeight.Bold)
                    Text("NAMAKKAL MARKET", style = MaterialTheme.typography.labelSmall)
                } },
                actions = {
                    TextButton(onClick = {
                        val social = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.facebook.com/ulavarsanthainamakkal"))
                        context.startActivity(social)
                    }) { Text("f") }
                    TextButton(onClick = {
                        val social = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.instagram.com/namakkal_ulavar_santhai/"))
                        context.startActivity(social)
                    }) { Text("📷") }
                    TextButton(onClick = {
                        val social = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://youtube.com/@ulavarsanthainkl"))
                        context.startActivity(social)
                    }) { Text("▶") }
                    TextButton(onClick = { refresh() }, enabled = !loading) { Text("புதுப்பி") }
                    TextButton(onClick = { (context as? Activity)?.finish() }) { Text("மூடு") }
                }
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(dateLabel ?: "விலை விவரம்", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { showRegistration = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("மொபைல் எண்ணைப் பதிவு செய்யுங்கள்")
                }
                if (showRegistration) {
                    MobileRegistrationDialog(
                        onDismiss = { showRegistration = false },
                        onSaved = { message ->
                            showRegistration = false
                            registrationStatus = message
                        }
                    )
                }
                registrationStatus?.let { message ->
                    AlertDialog(
                        onDismissRequest = { registrationStatus = null },
                        confirmButton = { TextButton(onClick = { registrationStatus = null }) { Text("சரி") } },
                        text = { Text(message) }
                    )
                }
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("இன்று", "வாரம்", "மாதம்").forEachIndexed { index, label ->
                        Tab(selectedTab == index, { selectedTab = index }, text = { Text(label) })
                    }
                }
                when (selectedTab) {
                    0 -> TodayList(rows, dateLabel, loading)
                    1 -> HistoryList(rows, weekly = true)
                    else -> HistoryList(rows, weekly = false)
                }
            }
        }
    }
}

@Composable
private fun TodayList(rows: List<PriceRow>, date: String?, loading: Boolean) {
    val context = LocalContext.current
    val validRows = rows.filter { it.low != null || it.high != null }
    if (loading && rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (validRows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("விலைத் தகவல் தற்போது இல்லை.\nஇணைய இணைப்பு மற்றும் தரவு அனுமதியைச் சரிபார்க்கவும்.", textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("காய்கறி", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("குறைந்த", Modifier.weight(.55f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Text("அதிக", Modifier.weight(.55f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Text("மாற்றம்", Modifier.width(58.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp)) {
            items(validRows) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.tamil, fontWeight = FontWeight.SemiBold)
                        Text(row.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(row.low ?: "—", Modifier.weight(.55f), textAlign = TextAlign.End)
                    Text(row.high ?: "—", Modifier.weight(.55f), textAlign = TextAlign.End)
                    Text(changeArrow(row), Modifier.width(58.dp), textAlign = TextAlign.End,
                        color = arrowColor(row))
                }
                HorizontalDivider()
            }
        }
        val share = remember(validRows, date) {
            buildString {
                append("நாமக்கல் உழவர் சந்தை விலை — ${date ?: ""}\n")
                validRows.forEach { append("${it.tamil}: ${it.low ?: "—"} - ${it.high ?: "—"} ${it.unit} ${changeArrow(it)}\n") }
            }
        }
        OutlinedButton(onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, share) }
            context.startActivity(Intent.createChooser(intent, "விலைப்பட்டியலைப் பகிரவும்"))
        }, Modifier.fillMaxWidth()) { Text("WhatsApp / பகிர்வு") }
    }
}

private fun changeArrow(row: PriceRow): String {
    val now = midpoint(row.low, row.high)
    val before = midpoint(row.previousLow, row.previousHigh)
    if (now == null || before == null) return "—"
    return when { now > before -> "↑"; now < before -> "↓"; else -> "→" }
}
private fun arrowColor(row: PriceRow): androidx.compose.ui.graphics.Color {
    val now = midpoint(row.low, row.high)
    val before = midpoint(row.previousLow, row.previousHigh)
    return when {
        now == null || before == null || now == before -> androidx.compose.ui.graphics.Color.Gray
        now > before -> androidx.compose.ui.graphics.Color(0xFFB3261E)
        else -> androidx.compose.ui.graphics.Color(0xFF16834A)
    }
}
private fun midpoint(low: String?, high: String?): Double? {
    val l = low?.toDoubleOrNull()
    val h = high?.toDoubleOrNull()
    return if (l != null && h != null) (l + h) / 2.0 else null
}

@Composable
private fun HistoryList(rows: List<PriceRow>, weekly: Boolean) {
    // Anchor history views to the latest date actually present in the database,
    // not the phone's calendar date (the current month may not have data yet).
    val latestAvailableDate = rows.flatMap { it.history }.maxOfOrNull { it.date }
    val cutoff = latestAvailableDate?.let { if (weekly) it.minusDays(6) else it.withDayOfMonth(1) }
    val title = when {
        latestAvailableDate == null -> if (weekly) "கிடைத்த கடைசி 7 நாட்கள்" else "கிடைத்த மாதம்"
        weekly -> "கிடைத்த கடைசி 7 நாட்கள்"
        else -> "கிடைத்த மாதம்: " + latestAvailableDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ta", "IN")))
    }
    val itemsWithHistory = rows.mapNotNull { row ->
        val points = row.history.filter { point ->
            cutoff != null && latestAvailableDate != null &&
                !point.date.isBefore(cutoff) && !point.date.isAfter(latestAvailableDate)
        }
        if (points.isEmpty()) null else row to points
    }
    Column(Modifier.fillMaxSize()) {
        Text(title, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (itemsWithHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("கிடைத்த விலைத் தேதிகளுக்கான வரலாறு இல்லை.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(itemsWithHistory) { (row, points) ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.tamil, fontWeight = FontWeight.SemiBold)
                            Text(row.unit, style = MaterialTheme.typography.labelSmall)
                            HistoryChart(points)
                            points.forEach { p ->
                                Text("${p.date.dayOfMonth}/${p.date.monthValue}: ${p.low?.let(::shortPrice) ?: "—"} – ${p.high?.let(::shortPrice) ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun shortPrice(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(Locale.US, value)

@Composable
private fun HistoryChart(points: List<DayPrice>) {
    val values = points.mapNotNull { p ->
        val lo = p.low; val hi = p.high
        if (lo != null && hi != null) (lo + hi) / 2.0 else lo ?: hi
    }
    if (values.size < 2) {
        Text("வரைபடத்திற்கு குறைந்தது 2 நாட்கள் விலை தேவை.", style = MaterialTheme.typography.labelSmall)
        return
    }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 1.0
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 8.dp)) {
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val coords = values.mapIndexed { i, v ->
            Offset(i * stepX, size.height - ((v - min) / span).toFloat() * size.height)
        }
        for (i in 0 until coords.lastIndex) {
            drawLine(lineColor, coords[i], coords[i + 1], strokeWidth = 4f, cap = StrokeCap.Round)
        }
        coords.forEach { drawCircle(lineColor, radius = 4f, center = it) }
    }
}


@Composable
private fun MobileRegistrationDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var mobile by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("மொபைல் எண் பதிவு") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("இன்றைய சந்தை விலை அறிவிப்புகளைப் பெற உங்கள் 10 இலக்க மொபைல் எண்ணை பதிவு செய்யுங்கள்.")
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { value -> mobile = value.filter { it.isDigit() }.take(10); error = null },
                    label = { Text("மொபைல் எண்") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text("எனது மொபைல் எண்ணை பதிவு செய்து சந்தை தகவல்களுக்கு தொடர்புகொள்ள ஒப்புக்கொள்கிறேன்.")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (!mobile.matches(Regex("^[6-9][0-9]{9}$"))) {
                    error = "சரியான 10 இலக்க இந்திய மொபைல் எண்ணை உள்ளிடவும்."
                } else if (!consent) {
                    error = "தொடர்வதற்கு உங்கள் ஒப்புதல் தேவை."
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            RegistrationRepository().register(mobile)
                            onSaved("உங்கள் மொபைல் எண் வெற்றிகரமாக பதிவு செய்யப்பட்டது.")
                        } catch (e: Exception) {
                            error = when {
                                e.message?.contains("409") == true -> "இந்த மொபைல் எண் ஏற்கனவே பதிவு செய்யப்பட்டுள்ளது."
                                else -> "பதிவு செய்ய முடியவில்லை. இணைய இணைப்பைச் சரிபார்த்து மீண்டும் முயற்சிக்கவும்."
                            }
                            busy = false
                        }
                    }
                }
            }) { Text(if (busy) "பதிவு செய்கிறது…" else "பதிவு") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("ரத்து") } }
    )
}
