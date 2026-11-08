package com.pesouza.messager

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

/** Envio de mensagens para o servidor ntfy (https://ntfy.sh por padrão). */
object Net {

    fun send(context: Context, kind: String, text: String) {
        val prefs = Prefs(context)
        val m = Message(
            cid = UUID.randomUUID().toString(),
            kind = kind,
            from = prefs.name,
            deviceId = prefs.deviceId,
            text = text,
            ts = System.currentTimeMillis(),
            mine = true,
            state = Message.STATE_PENDING,
        )
        MessageStore.add(m)
        publish(context, m)
    }

    fun retry(context: Context, m: Message) {
        MessageStore.setState(m.cid, Message.STATE_PENDING)
        publish(context, m)
    }

    private fun publish(context: Context, m: Message) {
        val prefs = Prefs(context)
        val server = prefs.server
        val code = prefs.code
        thread(name = "send") {
            val ok = try {
                val payload = JSONObject()
                    .put("v", 1).put("cid", m.cid).put("k", m.kind).put("from", m.from)
                    .put("dev", m.deviceId).put("t", m.text).put("ts", m.ts)
                    .toString()
                val body = Crypto.encrypt(code, payload).toByteArray()
                val conn = URL("$server/${Crypto.topicFor(code)}").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain")
                conn.outputStream.use { it.write(body) }
                val code2 = conn.responseCode
                conn.disconnect()
                code2 in 200..299
            } catch (e: Exception) {
                false
            }
            MessageStore.setState(m.cid, if (ok) Message.STATE_SENT else Message.STATE_FAILED)
        }
    }
}
