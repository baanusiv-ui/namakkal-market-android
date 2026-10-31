package in.namakkalmarket.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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

class SheetRepository(context: Context) {
    private val prefs = context.getSharedPreferences("market_cache", Context.MODE_PRIVATE)
    private val client = OkHttpClient()
    private val spreadsheetId = "1yOBoBrT2swBvPnnI5kzJysr71OgCY1vbSiG9qAmWw-8"
    private val apiKey = BuildConfig.GOOGLE_SHEETS_API_KEY

    suspend fun load(): SheetData = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Missing Google Sheets API key" }
        val metadata = getJson("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties&key=$apiKey")
        val tabs = metadata.optJSONArray("sheets") ?: error("No worksheet metadata")
        val monthTabs = (0 until tabs.length()).mapNotNull { i ->
            val title = tabs.getJSONObject(i).optJSONObject("properties")?.optString("title") ?: return@mapNotNull null
            runCatching { YearMonth.parse(title, DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) }
                .getOrNull()?.let { it to title }
        }.sortedBy { it.first }

        val today = LocalDate.now()
        val monthName = "${today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${today.year}"
        val chosenTab = monthTabs.firstOrNull { it.second == monthName } ?: monthTabs.lastOrNull()
            ?: error("No monthly worksheet found")
        val range = URLEncoder.encode("' ${chosenTab.second}'!A:ZZ".replace("' ", "'"), "UTF-8")
        val values = getJson("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/$range?valueRenderOption=UNFORMATTED_VALUE&key=$apiKey")
            .optJSONArray("values") ?: error("Worksheet has no values")
        val grid = (0 until values.length()).map { r ->
            val row = values.optJSONArray(r)
            if (row == null) emptyList() else (0 until row.length()).map { c -> row.opt(c)?.toString()?.trim().orEmpty() }
        }
        if (grid.isEmpty()) error("Worksheet is empty")

        val dateColumns = mutableMapOf<LocalDate, Pair<Int, Int>>()
        grid.first().forEachIndexed { column, header ->
            val match = Regex("""(\d{4}-\d{2}-\d{2}).*\((Low|High)\)""", RegexOption.IGNORE_CASE).find(header)
            if (match != null) {
                val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull()
                if (date != null) {
                    val old = dateColumns[date] ?: (-1 to -1)
                    dateColumns[date] = if (match.groupValues[2].equals("Low", true))
                        column to old.second else old.first to column
                }
            }
        }
        // Select the latest date that actually has at least one numeric quote.
        // A date header alone is not proof that prices were entered for that day.
        val selectedDate = dateColumns.keys
            .filter { !it.isAfter(today) }
            .sortedDescending()
            .firstOrNull { date ->
                val pair = dateColumns[date] ?: return@firstOrNull false
                grid.drop(1).any { row ->
                    val low = pair.first.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toDoubleOrNull()
                    val high = pair.second.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toDoubleOrNull()
                    low != null || high != null
                }
            } ?: error("No dated price entries found")

        val parsedRows = grid.drop(1).mapNotNull { row ->
            if (row.size < 3 || row[0].isBlank()) return@mapNotNull null
            val history = dateColumns.entries.sortedBy { it.key }.mapNotNull { (date, pair) ->
                val low = pair.first.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toDoubleOrNull()
                val high = pair.second.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toDoubleOrNull()
                if (low == null && high == null) null else DayPrice(date, low, high)
            }
            val current = history.firstOrNull { it.date == selectedDate }
            val previous = history.filter { it.date.isBefore(selectedDate) }.maxByOrNull { it.date }
            PriceRow(
                tamil = row[0],
                english = row.getOrNull(1).orEmpty(),
                unit = row.getOrNull(2).orEmpty(),
                low = current?.low?.let(::format),
                high = current?.high?.let(::format),
                previousLow = previous?.low?.let(::format),
                previousHigh = previous?.high?.let(::format),
                history = history
            )
        }
        save(parsedRows, selectedDate)
        SheetData(parsedRows, selectedDate, selectedDate != today)
    }

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

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (isNull(name)) null else optDouble(name).takeIf { it.isFinite() }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)

    private fun getJson(url: String): JSONObject {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Google Sheets API HTTP ${response.code}: ${body.take(180)}")
            JSONObject(body)
        }
    }
}
