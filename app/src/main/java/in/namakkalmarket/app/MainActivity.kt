package com.namakkalmarket.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var googleAuthorizationLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var afterGoogleAuthorization: (() -> Unit)? = null
    private var welcomeTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
        googleAuthorizationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                runCatching {
                    Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)
                }.onSuccess { authorizationResult ->
                    authorizationResult.accessToken?.let { GoogleTokenStore(this).save(it) }
                    afterGoogleAuthorization?.invoke()
                }
            }
            afterGoogleAuthorization = null
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
        setContent { MarketScreen(onConnectGoogle = { onAuthorized -> requestGoogleSheetAccess(onAuthorized) }) }
    }

    private fun requestGoogleSheetAccess(onAuthorized: () -> Unit) {
        afterGoogleAuthorization = onAuthorized
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/spreadsheets.readonly")))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        googleAuthorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                } else {
                    result.accessToken?.let { GoogleTokenStore(this).save(it) }
                    afterGoogleAuthorization?.invoke()
                    afterGoogleAuthorization = null
                }
            }
            .addOnFailureListener {
                afterGoogleAuthorization = null
            }
    }

    override fun onDestroy() {
        welcomeTts?.stop()
        welcomeTts?.shutdown()
        welcomeTts = null
        super.onDestroy()
    }
}


@Composable
private fun AppLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val green = androidx.compose.ui.graphics.Color(0xFF16852B)
        val lightGreen = androidx.compose.ui.graphics.Color(0xFF76C51D)
        val darkGreen = androidx.compose.ui.graphics.Color(0xFF075A25)
        val orange = androidx.compose.ui.graphics.Color(0xFFFFB52E)
        drawCircle(lightGreen, radius = w * 0.48f)
        drawCircle(androidx.compose.ui.graphics.Color.White, radius = w * 0.405f)
        // sunrise and farmland
        drawCircle(orange, radius = w * 0.16f, center = Offset(w * 0.68f, h * 0.48f))
        for (i in 0..4) {
            val x = w * (0.52f + i * 0.075f)
            drawLine(orange, Offset(x, h * 0.19f), Offset(x, h * 0.27f), strokeWidth = w * 0.018f, cap = StrokeCap.Round)
        }
        for (i in 0..2) {
            val y = h * (0.65f + i * 0.075f)
            drawLine(darkGreen, Offset(w * 0.49f, y), Offset(w * 0.91f, y - h * 0.04f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        }
        // farmer silhouette, head wrap and body
        drawCircle(darkGreen, radius = w * 0.085f, center = Offset(w * 0.29f, h * 0.31f))
        drawOval(darkGreen, topLeft = Offset(w * 0.18f, h * 0.40f), size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.39f))
        drawLine(lightGreen, Offset(w * 0.20f, h * 0.27f), Offset(w * 0.39f, h * 0.27f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
        drawLine(lightGreen, Offset(w * 0.19f, h * 0.31f), Offset(w * 0.38f, h * 0.31f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        // phone in farmer's hand
        drawLine(darkGreen, Offset(w * 0.36f, h * 0.56f), Offset(w * 0.54f, h * 0.48f), strokeWidth = w * 0.055f, cap = StrokeCap.Round)
        drawLine(darkGreen, Offset(w * 0.54f, h * 0.48f), Offset(w * 0.60f, h * 0.38f), strokeWidth = w * 0.055f, cap = StrokeCap.Round)
        drawLine(darkGreen, Offset(w * 0.56f, h * 0.34f), Offset(w * 0.64f, h * 0.38f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketScreen(onConnectGoogle: ((() -> Unit) -> Unit)) {
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
    val registrationPrefs = remember { context.getSharedPreferences("namakkal_market_user", android.content.Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) { if (!registrationPrefs.getBoolean("registration_prompt_seen", false)) showRegistration = true }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val result = repo.load()
                rows = result.rows
                dateLabel = result.date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                status = if (result.fallback) "கடைசியாக கிடைத்த விலைத் தேதி" else "இன்றைய விலைத் தகவல்"
            } catch (e: Exception) {
                status = "விலைத் தரவைப் பெற முடியவில்லை: ${e.message ?: "மீண்டும் முயற்சிக்கவும்"}"
                rows = repo.cachedRows()
                dateLabel = repo.cachedDate()
            } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme {
        Scaffold(topBar = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppLogo(Modifier.size(42.dp))
                    Text(
                        "நாமக்கல் உழவர் சந்தை",
                        Modifier.weight(1f).padding(start = 10.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                )
            }
        }, bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                color = androidx.compose.ui.graphics.Color(0xFFF0E8FA),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 2.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(2.dp)) {
                    listOf("இன்று", "வாரம்", "மாதம்", "வெளியேறு").forEachIndexed { index, label ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (index == selectedTab) androidx.compose.ui.graphics.Color(0xFF5634B4) else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    if (index == 3) (context as? android.app.Activity)?.finish()
                                    else selectedTab = index
                                }
                                .padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(listOf("⌂", "▦", "▣", "↪")[index], fontSize = 14.sp,
                                color = if (index == selectedTab) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF624BA4))
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                color = if (index == selectedTab) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF624BA4),
                                maxLines = 1)
                        }
                    }
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
                val trendValues = rows.mapNotNull { row ->
                    val now = midpoint(row.low, row.high)
                    val before = midpoint(row.previousLow, row.previousHigh)
                    if (now == null || before == null || before == 0.0) null else (now - before) / before * 100.0
                }
                val overallTrend = trendValues.takeIf { it.isNotEmpty() }?.average()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                dateLabel ?: "விலை விவரம்",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (overallTrend != null) {
                                Surface(
                                    color = if (overallTrend >= 0) androidx.compose.ui.graphics.Color(0xFFE5F5EC) else androidx.compose.ui.graphics.Color(0xFFFCE9E8),
                                    shape = MaterialTheme.shapes.extraLarge
                                ) {
                                    Text(
                                        (if (overallTrend >= 0) "▲ " else "▼ ") + "%.2f%%".format(Locale.US, kotlin.math.abs(overallTrend)),
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        color = if (overallTrend >= 0) androidx.compose.ui.graphics.Color(0xFF16834A) else androidx.compose.ui.graphics.Color(0xFFB3261E),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text("விலைத் தகவல்", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                WeatherAlertCard()
                if (showRegistration) {
                    MobileRegistrationDialog(
                        onDismiss = {\n                            showRegistration = false\n                            registrationPrefs.edit().putBoolean("registration_prompt_seen", true).apply()\n                        },
                        onSaved = { message ->
                            showRegistration = false
                            registrationPrefs.edit()\n                                .putBoolean("mobile_registered", true)\n                                .putBoolean("registration_prompt_seen", true)\n                                .apply()
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
                when (selectedTab) {
                    0 -> TodayList(rows, dateLabel, loading)
                    1 -> HistoryList(rows, weekly = true)
                    else -> HistoryList(rows, weekly = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayList(rows: List<PriceRow>, date: String?, loading: Boolean) {
    val brinjalRows = rows.filter { it.tamil.startsWith("கத்தரி") && (it.low != null || it.high != null) }
    val brinjalCombined = brinjalRows.firstOrNull()?.copy(
        tamil = "கத்தரி",
        english = "",
        low = brinjalRows.mapNotNull { it.low?.toDoubleOrNull() }.minOrNull()?.let { formatPrice(it) },
        high = brinjalRows.mapNotNull { it.high?.toDoubleOrNull() }.maxOrNull()?.let { formatPrice(it) },
        previousLow = brinjalRows.mapNotNull { it.previousLow?.toDoubleOrNull() }.minOrNull()?.let { formatPrice(it) },
        previousHigh = brinjalRows.mapNotNull { it.previousHigh?.toDoubleOrNull() }.maxOrNull()?.let { formatPrice(it) }
    )
    val validRows = rows.filter { (it.low != null || it.high != null) && !it.tamil.startsWith("கத்தரி") } +
        listOfNotNull(brinjalCombined)
    val orderedRows = validRows.sortedBy { if (it.tamil == "கத்தரி") 0 else rows.indexOfFirst { source -> source.tamil == it.tamil } + 1 }
    val changedRows = orderedRows.mapNotNull { row ->
        val now = midpoint(row.low, row.high)
        val before = midpoint(row.previousLow, row.previousHigh)
        if (now == null || before == null || before == 0.0) null else row to ((now - before) / before * 100.0)
    }
    val avgChange = changedRows.map { it.second }.takeIf { it.isNotEmpty() }?.average()
    val marketUp = avgChange != null && avgChange >= 0.0
    val filteredRows = orderedRows
    if (loading && rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("காய்கறி", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("இன்றைய விலை", Modifier.width(112.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            Text("மாற்றம்", Modifier.width(78.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()
        if (filteredRows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (validRows.isEmpty()) "விலைத் தகவல் தற்போது இல்லை." else "பொருத்தமான காய்கறிகள் இல்லை.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp)) {
                items(filteredRows) { row ->
                    val change = changedRows.firstOrNull { it.first == row }?.second
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = androidx.compose.ui.graphics.Color(0xFFF0E8FA)
                        ) {
                            val photoRes = vegetableImageRes(row.tamil)
                            Box(contentAlignment = Alignment.Center) {
                                if (photoRes != null) {
                                    Image(
                                        painter = painterResource(photoRes),
                                        contentDescription = row.tamil,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Text(vegetableEmoji(row.tamil), fontSize = 22.sp)
                                }
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 7.dp)) {
                            Text(row.tamil, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                        Column(Modifier.width(86.dp), horizontalAlignment = Alignment.End) {
                            Text("₹${row.low ?: "—"} – ${row.high ?: "—"}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End)
                            Text(row.unit, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.width(50.dp), horizontalAlignment = Alignment.End) {
                            if (change == null) {
                                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(
                                    (if (change > 0) "▲ " else if (change < 0) "▼ " else "• ") +
                                        "%.1f%%".format(Locale.US, kotlin.math.abs(change)),
                                    color = if (change > 0) androidx.compose.ui.graphics.Color(0xFF16834A)
                                        else if (change < 0) androidx.compose.ui.graphics.Color(0xFFB3261E)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
private fun formatPrice(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun vegetableImageRes(name: String): Int? = when {
    // Match specific Tamil names before broad "வாழை" matching.
    name.contains("வாழை இலை") -> R.drawable.vegetable_banana_leaf
    name.contains("தேங்காய்") || name.contains("தேங்கா") -> R.drawable.vegetable_coconut
    name.contains("கொய்யா") -> R.drawable.vegetable_guava
    name.contains("மலைவாழை") -> R.drawable.vegetable_hill_banana
    name.contains("பச்சை வாழை") || name.contains("வாழைப்பழ") || name.contains("வாழைப்பூ") -> R.drawable.vegetable_raw_banana
    else -> null
}

private fun vegetableEmoji(name: String): String = when {
    // Specific matches must come before broad family names.
    name.contains("வாழை இலை") -> "🍃"
    name.contains("தேங்காய்") || name.contains("தேங்கா") -> "🥥"
    name.contains("கொய்யா") -> "🍐"
    name.contains("மலைவாழை") || name.contains("பச்சை வாழை") || name.contains("வாழைப்பழ") -> "🍌"
    name.contains("கத்தரி") -> "🍆"
    name.contains("தக்காளி") -> "🍅"
    name.contains("வெண்டை") -> "🥒"
    name.contains("அவரை") || name.contains("பீன்ஸ்") || name.contains("கொத்தவரை") -> "🫛"
    name.contains("முருங்கை") -> "🥬"
    name.contains("முள்ளங்கி") -> "🥕"
    name.contains("பாகற்காய்") -> "🥒"
    name.contains("புடலங்காய்") || name.contains("சுரைக்காய்") || name.contains("பீர்க்க") -> "🥒"
    name.contains("பச்சை மிளகாய்") || name.contains("மிளகாய்") -> "🌶️"
    name.contains("வெங்க") || name.contains("சின்ன வெங்க") -> "🧅"
    name.contains("உருளை") -> "🥔"
    name.contains("கேரட்") -> "🥕"
    name.contains("பீட்ரூட்") -> "🫜"
    name.contains("பூசணி") || name.contains("பரங்க") -> "🎃"
    name.contains("வாழை") -> "🍌"
    name.contains("கீரை") -> "🥬"
    name.contains("எலுமிச்சை") -> "🍋"
    name.contains("முட்டைகோஸ்") -> "🥬"
    name.contains("காலிஃபிளவர்") -> "🥦"
    name.contains("மாம்பழம்") -> "🥭"
    name.contains("பப்பாளி") -> "🍈"
    name.contains("திராட்சை") -> "🍇"
    name.contains("ஆப்பிள்") -> "🍎"
    name.contains("பேரிக்காய்") -> "🍐"
    name.contains("வெள்ளரி") -> "🥒"
    name.contains("கொத்தமல்லி") || name.contains("புதினா") -> "🌿"
    else -> "🥬"
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
    // Combine all brinjal varieties into one daily low-high range for history.
    val brinjalRows = rows.filter { it.tamil.startsWith("கத்தரி") }
    val mergedBrinjal = brinjalRows.firstOrNull()?.let { base ->
        val combinedPoints = brinjalRows.flatMap { it.history }
            .groupBy { it.date }
            .mapNotNull { (_, dayRows) ->
                val lows = dayRows.mapNotNull { it.low }
                val highs = dayRows.mapNotNull { it.high }
                if (lows.isEmpty() && highs.isEmpty()) null
                else dayRows.first().copy(low = lows.minOrNull(), high = highs.maxOrNull())
            }
            .sortedBy { it.date }
        base.copy(tamil = "கத்தரி", english = "", history = combinedPoints)
    }
    val historyRows = rows.filterNot { it.tamil.startsWith("கத்தரி") } + listOfNotNull(mergedBrinjal)
    val allHistory = historyRows.flatMap { it.history }
    val latestAvailableDate = allHistory.maxOfOrNull { it.date }
    val availableMonths = allHistory.map { java.time.YearMonth.from(it.date) }.distinct().sortedDescending()
    var selectedMonth by remember(availableMonths) { mutableStateOf(availableMonths.firstOrNull()) }
    var monthMenuExpanded by remember { mutableStateOf(false) }

    val cutoff = latestAvailableDate?.let { if (weekly) it.minusDays(6) else null }
    val selectedMonthLabel = selectedMonth?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ta", "IN")))
        ?: "மாதம் தேர்வு"
    val title = if (weekly) "கிடைத்த கடைசி 7 நாட்கள்" else "மாத விலை வரலாறு"

    val itemsWithHistory = historyRows.mapNotNull { row ->
        val points = row.history.filter { point ->
            if (weekly) {
                cutoff != null && latestAvailableDate != null &&
                    !point.date.isBefore(cutoff) && !point.date.isAfter(latestAvailableDate)
            } else {
                selectedMonth != null && java.time.YearMonth.from(point.date) == selectedMonth
            }
        }.sortedBy { it.date }
        if (points.isEmpty()) null else row to points
    }

    Column(Modifier.fillMaxSize()) {
        if (weekly) {
            Text(title, Modifier.padding(vertical = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Box {
                    OutlinedButton(onClick = { monthMenuExpanded = true }, enabled = availableMonths.isNotEmpty()) {
                        Text(selectedMonthLabel)
                        Text("  ▾")
                    }
                    DropdownMenu(
                        expanded = monthMenuExpanded,
                        onDismissRequest = { monthMenuExpanded = false }
                    ) {
                        availableMonths.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("ta", "IN")))) },
                                onClick = {
                                    selectedMonth = month
                                    monthMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (itemsWithHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (weekly) "கிடைத்த விலைத் தேதிகளுக்கான வரலாறு இல்லை."
                    else "இந்த மாதத்திற்கான விலை வரலாறு இல்லை.",
                    textAlign = TextAlign.Center
                )
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
