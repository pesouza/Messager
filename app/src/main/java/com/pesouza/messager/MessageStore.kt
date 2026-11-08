package com.pesouza.messager

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

data class Message(
    val cid: String,          // id criado por quem enviou (evita duplicatas)
    val kind: String,         // "msg" ou "buzz"
    val from: String,
    val deviceId: String,
    val text: String,
    val ts: Long,
    val mine: Boolean,
    var state: Int = STATE_SENT,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("cid", cid).put("kind", kind).put("from", from).put("dev", deviceId)
        .put("text", text).put("ts", ts).put("mine", mine).put("state", state)

    companion object {
        const val STATE_PENDING = 0
        const val STATE_SENT = 1
        const val STATE_FAILED = 2

        fun fromJson(o: JSONObject) = Message(
            cid = o.getString("cid"),
            kind = o.optString("kind", "msg"),
            from = o.optString("from"),
            deviceId = o.optString("dev"),
            text = o.optString("text"),
            ts = o.optLong("ts"),
            mine = o.optBoolean("mine"),
            state = o.optInt("state", STATE_SENT),
        )
    }
}

/** Histórico guardado só no próprio celular. */
object MessageStore {
    private const val MAX = 3000
    private val lock = Any()
    private var loaded = false
    private val items = ArrayList<Message>()
    private lateinit var file: File

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun init(context: Context) = synchronized(lock) {
        if (loaded) return
        file = File(context.filesDir, "messages.json")
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) items.add(Message.fromJson(arr.getJSONObject(i)))
                // Mensagens que ficaram "enviando" quando o app fechou viram "falhou".
                items.forEach { if (it.state == Message.STATE_PENDING) it.state = Message.STATE_FAILED }
            } catch (_: Exception) {
            }
        }
        loaded = true
    }

    fun all(): List<Message> = synchronized(lock) { ArrayList(items) }

    /** Adiciona; retorna false se já existia. */
    fun add(m: Message): Boolean {
        synchronized(lock) {
            if (items.any { it.cid == m.cid }) return false
            items.add(m)
            items.sortBy { it.ts }
            while (items.size > MAX) items.removeAt(0)
            save()
        }
        notifyChanged()
        return true
    }

    fun setState(cid: String, state: Int) {
        synchronized(lock) {
            val m = items.firstOrNull { it.cid == cid } ?: return
            if (m.state == state) return
            m.state = state
            save()
        }
        notifyChanged()
    }

    fun clear() {
        synchronized(lock) { items.clear(); save() }
        notifyChanged()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, "messages.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)
    fun notifyChanged() = main.post { listeners.forEach { it() } }
}
