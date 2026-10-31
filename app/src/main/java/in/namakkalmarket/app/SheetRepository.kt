package in.namakkalmarket.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
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

class SheetRepository(context: Context) {
    private val prefs = context.getSharedPreferences("market_cache", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    // Stable Dropbox shared links. Files are overwritten in place during sync, so these links remain usable.
    private val monthFiles = mapOf(
        YearMonth.of(2026, 10) to "https://www.dropbox.com/scl/fi/yhm8ikx53spv95k4nhv2w/October-2026.csv?rlkey=9253upg6n5r5qyyriusup742x&dl=1",
        YearMonth.of(2026, 11) to "https://www.dropbox.com/scl/fi/1d2h8zu1ijdhjo77ec3ix/November-2026.csv?rlkey=kxt8guvzkcaqhmzoj1i336mn6&dl=1",
        YearMonth.of(2026, 12) to "https://www.dropbox.com/scl/fi/zhdzd4sstf5rdf0fdzhkn/December-2026.csv?rlkey=iwirsu0r90cjpjtfjnqbuwpwf&dl=1"
    )

    suspend fun load(): SheetData = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        val chosen = monthFiles.entries.firstOrNull { it.key == currentMonth }
            ?: monthFiles.entries.filter { !it.key.isAfter(currentMonth) }.maxByOrNull { it.key }
            ?: monthFiles.entries.minByOrNull { it.key }
            ?: error("No Dropbox month file configured")
        val csv = getText(chosen.value)
        val grid = parseCsv(csv)
        if (grid.isEmpty()) error("Dropbox CSV is empty")

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
        val selectedDate = dateColumns.keys.filter { !it.isAfter(today) }.sortedDescending()
            .firstOrNull { date ->
                val pair = dateColumns[date] ?: return@firstOrNull false
                grid.drop(1).any { row ->
                    val low = pair.first.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toPrice()
                    val high = pair.second.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toPrice()
                    low != null || high != null
                }
            } ?: error("No dated price entries found in Dropbox CSV")

        val parsedRows = grid.drop(1).mapNotNull { row ->
            if (row.size < 3 || row[0].isBlank()) return@mapNotNull null
            val history = dateColumns.entries.sortedBy { it.key }.mapNotNull { (date, pair) ->
                val low = pair.first.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toPrice()
                val high = pair.second.takeIf { it >= 0 }?.let { row.getOrNull(it) }?.toPrice()
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

    private fun String.toPrice(): Double? =
        replace("₹", "").replace("Rs.", "", ignoreCase = true)
            .replace(",", "").trim().takeIf { it.isNotEmpty() && it != "-" }?.toDoubleOrNull()

    private fun parseCsv(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { row.add(cell.toString()); cell.setLength(0) }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
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

    private fun getText(url: String): String {
        client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { response ->
            if (!response.isSuccessful) error("Dropbox CSV download HTTP ${response.code}")
            response.body?.string() ?: error("Dropbox returned an empty CSV")
        }
    }
}
