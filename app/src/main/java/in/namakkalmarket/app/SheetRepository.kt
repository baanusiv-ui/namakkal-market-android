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

class SheetRepository(context: Context) {
    private val prefs = context.getSharedPreferences("market_cache", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    suspend fun load(): SheetData = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val records = mutableListOf<JSONObject>()
        var offset = 0
        val pageSize = 1000

        // Read-only public customer prices from Supabase. The publishable key is safe to embed in a client.
        while (true) {
            val request = Request.Builder()
                .url(
                    BuildConfig.SUPABASE_URL.trimEnd('/') +
                        "/rest/v1/market_prices?select=market_date,tamil_name,english_name,unit,low_price,high_price" +
                        "&order=market_date.asc,tamil_name.asc,english_name.asc"
                )
                .header("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                .header("Range-Unit", "items")
                .header("Range", "$offset-${offset + pageSize - 1}")
                .header("Cache-Control", "no-cache")
                .build()

            val page = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Supabase price API HTTP ${response.code}")
                JSONArray(response.body?.string() ?: "[]")
            }
            for (i in 0 until page.length()) records.add(page.getJSONObject(i))
            if (page.length() < pageSize) break
            offset += page.length()
        }

        val grouped = linkedMapOf<Pair<String, String>, MutableList<DayPrice>>()
        val metadata = linkedMapOf<Pair<String, String>, Triple<String, String, String>>()
        records.forEach { item ->
            val date = runCatching { LocalDate.parse(item.getString("market_date")) }.getOrNull()
                ?: return@forEach
            if (date.isAfter(today)) return@forEach
            val tamil = item.optString("tamil_name").trim()
            if (tamil.isEmpty()) return@forEach
            val english = item.optString("english_name").trim()
            val unit = item.optString("unit").trim()
            val low = item.optNullableDouble("low_price")
            val high = item.optNullableDouble("high_price")
            if (low == null && high == null) return@forEach

            val key = tamil to english
            grouped.getOrPut(key) { mutableListOf() }.add(DayPrice(date, low, high))
            metadata[key] = Triple(tamil, english, unit)
        }

        val selectedDate = grouped.values.flatten().map { it.date }.maxOrNull()
            ?: error("Supabase has no dated market prices yet")

        val parsedRows = grouped.entries.map { (key, historyValues) ->
            val history = historyValues.distinctBy { it.date }.sortedBy { it.date }
            val current = history.firstOrNull { it.date == selectedDate }
            val previous = history.filter { it.date.isBefore(selectedDate) }.maxByOrNull { it.date }
            val meta = metadata.getValue(key)
            PriceRow(
                tamil = meta.first,
                english = meta.second,
                unit = meta.third,
                low = current?.low?.let(::format),
                high = current?.high?.let(::format),
                previousLow = previous?.low?.let(::format),
                previousHigh = previous?.high?.let(::format),
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
