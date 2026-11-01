package com.namakkalmarket.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
fun WhatsAppBridgeCard(rows: List<PriceRow>, dateLabel: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("whatsapp_inbox", android.content.Context.MODE_PRIVATE) }
    var latest by remember { mutableStateOf(readLatestWhatsApp(prefs)) }
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("WhatsApp இணைப்பு", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "மூடு" else "திற") }
            }
            if (expanded) {
                Text("WhatsApp மற்றும் WhatsApp Business அறிவிப்புகளைப் பெற அனுமதி வழங்கவும்.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    ) { Text("அனுமதி அமைப்பு") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val lines = buildString {
                                append("நாமக்கல் உழவர் சந்தை – ")
                                append(dateLabel ?: "விலைத் தகவல்")
                                append("\\n")
                                rows.filter { it.low != null || it.high != null }.forEach {
                                    append(it.tamil).append(": ₹").append(it.low ?: "—")
                                        .append(" - ").append(it.high ?: "—").append(" ")
                                        .append(it.unit).append("\\n")
                                }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, lines)
                            }
                            context.startActivity(Intent.createChooser(send, "WhatsApp மூலம் விலைகளைப் பகிரவும்"))
                        }
                    ) { Text("விலைகளைப் பகிர்") }
                }
                latest?.let { message ->
                    HorizontalDivider(Modifier.padding(vertical = 5.dp))
                    Text("கடைசியாகப் பெற்ற செய்தி • ${message.first}", style = MaterialTheme.typography.labelSmall)
                    Text(message.second, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                } ?: Text("இன்னும் WhatsApp செய்தி பெறப்படவில்லை.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { latest = readLatestWhatsApp(prefs) }) { Text("செய்தியைப் புதுப்பி") }
            }
        }
    }
}

private fun readLatestWhatsApp(prefs: android.content.SharedPreferences): Pair<String, String>? = runCatching {
    val array = JSONArray(prefs.getString("messages", "[]"))
    if (array.length() == 0) null else {
        val item = array.getJSONObject(0)
        item.optString("source") to (item.optString("title") + ": " + item.optString("text"))
    }
}.getOrNull()
