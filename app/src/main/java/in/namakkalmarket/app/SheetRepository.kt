package com.namakkalmarket.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DayPrice(val date: LocalDate, val low: Double?, val high: Double?)
data class PriceRow(
    val tamil: String,
    val english: String,
    val unit: String,
    val low: String?,
    val high: String?,
    val previousLow: String?,
    val previousHigh: String?,
    val history: List<DayPrice>
)
data class SheetData(val rows: List<PriceRow>, val date: LocalDate, val fallback: Boolean)

private val tamilItemOrder = listOf(
    "கத்தரி சிம்ரன்",
    "கத்தரி ஊதா",
    "கத்தரி பச்சை",
    "தக்காளி",
    "வெண்டை",
    "அவரை",
    "கொத்தவரை",
    "முருங்கை",
    "முள்ளங்கி",
    "புடலை",
    "பாகல்",
    "பீர்க்கன்",
    "வாழைக்காய்",
    "வாழைப்பூ",
    "வாழைத்தண்டு",
    "பரங்கிக்காய்",
    "பூசணி",
    "சுரைக்காய்",
    "கோவக்காய்",
    "பீன்ஸ்",
    "கேரட்",
    "பீட்ரூட்",
    "உருளை",
    "சௌசௌ",
    "முட்டைகோஸ்",
    "காலிஃபிளவர்",
    "குடைமிளகாய்",
    "நூல்கோல்",
    "பச்சை பட்டாணி",
    "வெள்ளரிக்காய்",
    "சேனை",
    "கருணை",
    "மரவள்ளி",
    "சின்ன வெங்காயம்",
    "பெரிய வெங்காயம்",
    "இஞ்சி",
    "பூண்டு",
    "பச்சை மிளகாய்",
    "எலுமிச்சை",
    "கொலுமிச்சை",
    "கீரை",
    "கருவேப்பிலை",
    "மல்லித்தழை",
    "புதினா",
    "வாழை இலை",
    "தேங்காய்",
    "கொய்யா",
    "மலைவாழை",
    "பச்சை வாழை",
    "கற்பூரவள்ளி",
    "ரஸ்தாளி",
    "செவ்வாழை",
    "பூவன்",
    "இளநீர்",
    "பலாப்பழம்",
    "பப்பாளி",
    "சீத்தாப்பழம்",
    "நிலக்கடலை",
    "தர்பூசணி"
)
private val tamilItemOrderIndex = tamilItemOrder.withIndex().associate { it.value to it.index }

class SheetRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("market_cache", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    suspend fun load(): SheetData = withContext(Dispatchers.IO) {
        val token = GoogleTokenStore(context).getValidToken()
        if (!token.isNullOrBlank()) {
            runCatching { loadFromGoogleSheets(token) }.getOrElse { loadWhatsAppFallback() }
        } else {
            loadWhatsAppFallback()
        }
    }

    private suspend fun loadFromGoogleSheets(token: String): SheetData = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val monthTab = today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        val range = "'$monthTab'!A1:BK100"
        val url = "https://sheets.googleapis.com/v4/spreadsheets/16-u9MpIaCPsT7wfby8CoM-80zThyOUXvNvOxV4WGyNk/values/" +
            java.net.URLEncoder.encode(range, "UTF-8") + "?valueRenderOption=FORMATTED_VALUE"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Google Sheets API HTTP ${response.code}")
            val body = response.body?.string() ?: error("Google Sheets returned an empty response")
            val values = JSONObject(body).optJSONArray("values") ?: error("Google Sheet has no values")
            if (values.length() < 2) error("Google Sheet has no market rows")
            val header = values.getJSONArray(0)
            val dateColumns = linkedMapOf<LocalDate, Pair<Int, Int>>()
            val headerRegex = Regex("^(\\d{4}-\\d{2}-\\d{2}) \\(Low\\)$")
            for (i in 3 until header.length()) {
                val label = header.optString(i)
                val match = headerRegex.matchEntire(label) ?: continue
                val date = LocalDate.parse(match.groupValues[1])
                val highIndex = (i + 1).takeIf { it < header.length() && header.optString(it).contains("(High)") } ?: continue
                dateColumns[date] = i to highIndex
            }
            if (dateColumns.isEmpty()) error("Date columns not found in $monthTab")
            val rows = mutableListOf<PriceRow>()
            for (i in 1 until values.length()) {
                val cells = values.optJSONArray(i) ?: continue
                val tamil = cells.optString(0).trim()
                val english = cells.optString(1).trim()
                if (tamil.isBlank() || english.isBlank()) continue
                val unit = cells.optString(2).ifBlank { "Per Kg" }
                val history = dateColumns.mapNotNull { (date, columns) ->
                    val low = parsePrice(cells.optString(columns.first))
                    val high = parsePrice(cells.optString(columns.second))
                    if (low == null && high == null) null else DayPrice(date, low, high)
                }.sortedBy { it.date }
                if (history.isEmpty()) continue
                val currentDate = history.filter { !it.date.isAfter(today) }.maxByOrNull { it.date }?.date ?: continue
                val current = history.firstOrNull { it.date == currentDate }
                val previous = history.filter { it.date.isBefore(currentDate) }.maxByOrNull { it.date }
                rows += PriceRow(tamil, english, unit,
                    current?.low?.let(::format), current?.high?.let(::format),
                    previous?.low?.let(::format), previous?.high?.let(::format), history)
            }
            if (rows.isEmpty()) error("No market prices found in $monthTab")
            val selectedDate = rows.flatMap { it.history }.maxOf { it.date }
            val selectedRows = rows.map { row ->
                val current = row.history.firstOrNull { it.date == selectedDate }
                val previous = row.history.filter { it.date.isBefore(selectedDate) }.maxByOrNull { it.date }
                row.copy(
                    low = current?.low?.let(::format), high = current?.high?.let(::format),
                    previousLow = previous?.low?.let(::format), previousHigh = previous?.high?.let(::format)
                )
            }.sortedWith(compareBy<PriceRow> { tamilItemOrderIndex[it.tamil] ?: Int.MAX_VALUE }.thenBy { it.tamil })
            save(selectedRows, selectedDate)
            SheetData(selectedRows, selectedDate, selectedDate != today)
        }
    }

    private fun parsePrice(value: String): Double? =
        value.replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()?.takeIf { it > 0.0 }

    private suspend fun loadWhatsAppFallback(): SheetData = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val inbox = context.getSharedPreferences("whatsapp_inbox", Context.MODE_PRIVATE)
        val messages = runCatching { JSONArray(inbox.getString("messages", "[]")) }.getOrDefault(JSONArray())
        val grouped = linkedMapOf<Pair<String, LocalDate>, DayPrice>()
        val metadata = linkedMapOf<String, Triple<String, String, String>>()

        // WhatsApp group notifications are the live source. Newest messages are stored first.
        for (i in messages.length() - 1 downTo 0) {
            val message = messages.optJSONObject(i) ?: continue
            val groupName = message.optString("group", "")
            if (!groupName.contains("நாமக்கல்", true) && !groupName.contains("namakkal", true)) continue
            val timestamp = message.optLong("time", 0L)
            if (timestamp <= 0L) continue
            val date = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            if (date.isAfter(today)) continue
            val candidates = message.optJSONArray("candidates") ?: continue
            for (j in 0 until candidates.length()) {
                val candidate = candidates.optJSONObject(j) ?: continue
                val name = candidate.optString("name").trim()
                val low = candidate.optDouble("low", Double.NaN).takeIf { it.isFinite() }
                val high = candidate.optDouble("high", Double.NaN).takeIf { it.isFinite() }
                if (name.isBlank() || low == null || high == null || low <= 0.0 || high < low) continue
                val key = name to date
                // Iteration is oldest-to-newest, so later messages replace earlier prices for the same item/day.
                grouped[key] = DayPrice(date, low, high)
                metadata[name] = Triple(name, "", "கிலோ")
            }
        }

        if (grouped.isEmpty()) error("நாமக்கல் WhatsApp குழுவிலிருந்து விலைச் செய்தி இன்னும் வரவில்லை")
        val selectedDate = grouped.values.maxOf { it.date }
        val names = grouped.keys.map { it.first }.distinct()
        val parsedRows = names.map { name ->
            val history = grouped.filterKeys { it.first == name }.values.sortedBy { it.date }
            val current = history.lastOrNull { it.date == selectedDate }
            val previous = history.filter { it.date.isBefore(selectedDate) }.maxByOrNull { it.date }
            val meta = metadata[name] ?: Triple(name, "", "கிலோ")
            PriceRow(
                tamil = meta.first, english = meta.second, unit = meta.third,
                low = current?.low?.let(::format), high = current?.high?.let(::format),
                previousLow = previous?.low?.let(::format), previousHigh = previous?.high?.let(::format),
                history = history
            )
        }.sortedWith(compareBy<PriceRow> { tamilItemOrderIndex[it.tamil] ?: Int.MAX_VALUE }.thenBy { it.tamil })

        save(parsedRows, selectedDate)
        SheetData(parsedRows, selectedDate, selectedDate != today)
    }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else optDouble(name).takeIf { it.isFinite() }

    fun cachedDate(): String? = prefs.getString("date", null)

    fun cachedRows(): List<PriceRow> = runCatching {
        val array = JSONObject(prefs.getString("rows", "{}") ?: "{}").optJSONArray("items") ?: return emptyList()
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val hist = o.optJSONArray("hist")
            val history = if (hist == null) emptyList() else (0 until hist.length()).map { j ->
                val h = hist.getJSONObject(j)
                DayPrice(LocalDate.parse(h.getString("d")), h.optNullableDouble("l"), h.optNullableDouble("h"))
            }
            PriceRow(o.optString("ta"), o.optString("en"), o.optString("unit"),
                o.optNullable("lo"), o.optNullable("hi"), o.optNullable("pl"), o.optNullable("ph"), history)
        }
    }.getOrDefault(emptyList())

    private fun save(rows: List<PriceRow>, date: LocalDate) {
        val array = org.json.JSONArray()
        rows.forEach { row ->
            val history = org.json.JSONArray()
            row.history.forEach { point ->
                history.put(JSONObject().put("d", point.date.toString()).put("l", point.low).put("h", point.high))
            }
            array.put(JSONObject().put("ta", row.tamil).put("en", row.english).put("unit", row.unit)
                .put("lo", row.low).put("hi", row.high).put("pl", row.previousLow).put("ph", row.previousHigh)
                .put("hist", history))
        }
        prefs.edit().putString("rows", JSONObject().put("items", array).toString())
            .putString("date", date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))).apply()
    }

    private fun JSONObject.optNullable(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)
}
