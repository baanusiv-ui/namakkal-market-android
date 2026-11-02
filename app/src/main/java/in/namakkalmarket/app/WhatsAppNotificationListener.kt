package com.namakkalmarket.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class WhatsAppNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return
        val extras = sbn.notification.extras
        val conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString()?.trim().orEmpty()
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val isGroup = extras.getBoolean("android.isGroupConversation", false) || conversationTitle.isNotBlank()
        val groupName = conversationTitle.ifBlank { if (isGroup) title else "" }
        // Only the configured Namakkal market group is treated as a price source.
        if (!isGroup || (!groupName.contains("நாமக்கல்", true) && !groupName.contains("namakkal", true))) return

        val text = (extras.getCharSequence("android.bigText")
            ?: extras.getCharSequence("android.text"))?.toString()?.trim().orEmpty()
        if (text.isBlank() || text.contains("new messages", true)) return

        val prefs = getSharedPreferences("whatsapp_inbox", MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString("messages", "[]")) }.getOrDefault(JSONArray())
        val item = JSONObject()
            .put("source", if (pkg == "com.whatsapp") "WhatsApp" else "WhatsApp Business")
            .put("group", groupName)
            .put("title", title)
            .put("text", text)
            .put("time", sbn.postTime)
            .put("candidates", WhatsAppPriceParser.toJson(WhatsAppPriceParser.parse(text)))
        val next = JSONArray().put(item)
        for (i in 0 until minOf(old.length(), 49)) {
            val previous = old.optJSONObject(i) ?: continue
            if (previous.optString("group") != groupName ||
                previous.optString("title") != title || previous.optString("text") != text) next.put(previous)
        }
        prefs.edit().putString("messages", next.toString()).apply()
    }
}
