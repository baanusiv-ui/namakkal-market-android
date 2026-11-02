package com.namakkalmarket.app

import org.json.JSONArray
import org.json.JSONObject

data class WhatsAppPriceCandidate(val name: String, val low: Double, val high: Double, val sourceLine: String)

object WhatsAppPriceParser {
    private val aliases = linkedMapOf(
        "கத்தரி" to listOf("கத்தரி", "கத்திரி", "brinjal", "eggplant"),
        "தக்காளி" to listOf("தக்காளி", "tomato"),
        "வெண்டை" to listOf("வெண்டை", "வெண்டைக்காய்", "ladies finger", "okra"),
        "அவரை" to listOf("அவரை", "broad bean"),
        "கொத்தவரை" to listOf("கொத்தவரை", "cluster bean"),
        "முருங்கை" to listOf("முருங்கை", "முருங்கைக்காய்", "drumstick"),
        "முள்ளங்கி" to listOf("முள்ளங்கி", "radish"),
        "புடலை" to listOf("புடலை", "snake gourd"),
        "பாகல்" to listOf("பாகல்", "பாகற்காய்", "bitter gourd"),
        "பீர்க்கன்" to listOf("பீர்க்கன்", "பீர்க்கங்காய்", "ridge gourd"),
        "வாழைக்காய்" to listOf("வாழைக்காய்", "raw banana"),
        "வாழைப்பூ" to listOf("வாழைப்பூ", "plantain flower"),
        "வாழைத்தண்டு" to listOf("வாழைத்தண்டு", "banana stem"),
        "சின்ன வெங்காயம்" to listOf("சின்ன வெங்காயம்", "small onion"),
        "பெரிய வெங்காயம்" to listOf("பெரிய வெங்காயம்", "onion"),
        "பச்சை மிளகாய்" to listOf("பச்சை மிளகாய்", "green chilli"),
        "தேங்காய்" to listOf("தேங்காய்", "coconut")
    )

    fun parse(message: String): List<WhatsAppPriceCandidate> {
        val output = mutableListOf<WhatsAppPriceCandidate>()
        message.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val lower = line.lowercase()
            val item = aliases.entries.firstOrNull { (_, words) -> words.any { lower.contains(it.lowercase()) } } ?: return@forEach
            // Require a recognizable price range/currency marker to avoid treating
            // dates and unrelated quantities on the same line as prices.
            val number = """(\d{1,5}(?:\.\d{1,2})?)"""
            val rangeMatch = Regex(
                """$number\s*[-–—]\s*$number(?=\s*(?:/|₹|ரூ|ரூபாய்|$))""",
                RegexOption.IGNORE_CASE
            ).find(line)
            val singleMatch = if (rangeMatch == null) {
                Regex("""(?:₹|ரூ\.?\s*)$number(?=\s*(?:/|ரூபாய்|$))""", RegexOption.IGNORE_CASE)
                    .find(line)
            } else null
            val low: Double
            val high: Double
            if (rangeMatch != null) {
                low = rangeMatch.groupValues[1].toDoubleOrNull() ?: return@forEach
                high = rangeMatch.groupValues[2].toDoubleOrNull() ?: return@forEach
            } else if (singleMatch != null) {
                low = singleMatch.groupValues[1].toDoubleOrNull() ?: return@forEach
                high = low
            } else return@forEach
            if (low <= 0.0 || high < low || high > 100000.0) return@forEach
            output.add(WhatsAppPriceCandidate(item.key, low, high, line))
        }
        return output.distinctBy { it.name }
    }

    fun toJson(candidates: List<WhatsAppPriceCandidate>): JSONArray = JSONArray().also { array ->
        candidates.forEach { c ->
            array.put(JSONObject().put("name", c.name).put("low", c.low).put("high", c.high).put("line", c.sourceLine))
        }
    }
}
