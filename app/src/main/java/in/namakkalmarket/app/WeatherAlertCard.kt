package com.namakkalmarket.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private data class NamakkalWeather(
    val temperature: Double,
    val apparent: Double,
    val wind: Double,
    val code: Int
)

@Composable
fun WeatherAlertCard() {
    var weather by remember { mutableStateOf<NamakkalWeather?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            weather = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://api.open-meteo.com/v1/forecast?latitude=11.2189&longitude=78.1677&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=Asia%2FKolkata")
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Weather service unavailable")
                    val current = JSONObject(response.body?.string() ?: error("Empty weather response"))
                        .getJSONObject("current")
                    NamakkalWeather(
                        temperature = current.getDouble("temperature_2m"),
                        apparent = current.getDouble("apparent_temperature"),
                        wind = current.getDouble("wind_speed_10m"),
                        code = current.getInt("weather_code")
                    )
                }
            }
        } catch (_: Exception) {
            failed = true
        }
    }

    val w = weather
    val warning = when {
        w == null -> null
        w.code >= 95 -> "இடி மின்னல் எச்சரிக்கை — வெளியே செல்லும்போது கவனம்."
        w.code in listOf(65, 82) || w.code == 63 -> "கனமழை வாய்ப்பு — பாதுகாப்பாக இருங்கள்."
        w.code in listOf(61, 80, 81) -> "மழை பெய்யக்கூடும் — பயணத்திற்கு முன் கவனிக்கவும்."
        w.temperature >= 38 -> "அதிக வெப்பம் — தண்ணீர் குடித்து வெயிலைத் தவிர்க்கவும்."
        else -> null
    }
    val condition = when {
        w == null -> if (failed) "வானிலைத் தகவலைப் பெற முடியவில்லை" else "வானிலைத் தகவல் ஏற்றப்படுகிறது…"
        w.code == 0 -> "வானம் தெளிவு"
        w.code in 1..3 -> "மேகமூட்டம்"
        w.code in 45..48 -> "மூடுபனி"
        w.code in 51..67 -> "தூறல் / மழை"
        w.code in 71..77 -> "பனிப்பொழிவு"
        w.code in 80..82 -> "மழை"
        w.code >= 95 -> "இடியுடன் கூடிய மழை"
        else -> "வானிலை நிலவரம்"
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("நாமக்கல் வானிலை", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (w == null) {
                Text(condition, style = MaterialTheme.typography.labelMedium)
            } else {
                Text("${w.temperature.toInt()}°C  •  ${condition}  •  உணரும் ${w.apparent.toInt()}°C",
                    style = MaterialTheme.typography.labelMedium)
                Text(
                    if (warning != null) "⚠ $warning" else "காற்று ${w.wind.toInt()} கி.மீ/மணி  •  முக்கிய எச்சரிக்கை இல்லை",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (warning != null) Color(0xFFB3261E) else Color(0xFF16834A),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
