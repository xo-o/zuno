package com.zuno.ai.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** An attachment already uploaded and read by the server, kept in a saved draft. */
data class SavedDraftAttachment(
    val serverId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: AttachmentKind,
    val url: String?,
)

data class SavedDraft(val text: String, val attachments: List<SavedDraftAttachment>) {
    val isEmpty: Boolean get() = text.isBlank() && attachments.isEmpty()
}

/**
 * Persists each conversation's unsent composer text and ready attachments, so a chat reopened
 * later (e.g. from a "Remind me to continue" notification) looks exactly as the learner left it.
 */
class DraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("zuno_drafts", Context.MODE_PRIVATE)

    fun read(key: String): SavedDraft? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val list = json.optJSONArray("attachments") ?: JSONArray()
            SavedDraft(
                text = json.optString("text"),
                attachments = List(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    SavedDraftAttachment(
                        serverId = item.getString("serverId"),
                        name = item.optString("name"),
                        mimeType = item.optString("mimeType"),
                        sizeBytes = item.optLong("sizeBytes"),
                        kind = runCatching { AttachmentKind.valueOf(item.optString("kind")) }.getOrDefault(AttachmentKind.Document),
                        url = item.optString("url").takeIf { it.isNotBlank() },
                    )
                },
            )
        }.getOrNull()
    }

    fun text(key: String): String = read(key)?.text.orEmpty()

    fun saveText(key: String, text: String) = update(key) { it.copy(text = text) }

    fun saveAttachments(key: String, attachments: List<SavedDraftAttachment>) = update(key) { it.copy(attachments = attachments) }

    fun remove(key: String) = prefs.edit { remove(key) }

    fun clear() = prefs.edit { clear() }

    private fun update(key: String, transform: (SavedDraft) -> SavedDraft) {
        val next = transform(read(key) ?: SavedDraft("", emptyList()))
        if (next.isEmpty) {
            remove(key)
            return
        }
        val attachments = JSONArray()
        next.attachments.forEach {
            attachments.put(
                JSONObject()
                    .put("serverId", it.serverId)
                    .put("name", it.name)
                    .put("mimeType", it.mimeType)
                    .put("sizeBytes", it.sizeBytes)
                    .put("kind", it.kind.name)
                    .put("url", it.url ?: ""),
            )
        }
        prefs.edit { putString(key, JSONObject().put("text", next.text).put("attachments", attachments).toString()) }
    }
}
