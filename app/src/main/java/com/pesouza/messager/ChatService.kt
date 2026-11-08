package com.pesouza.messager

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Serviço em primeiro plano que mantém uma conexão aberta com o servidor
 * e recebe as mensagens na hora, mesmo com o app fechado ou a tela apagada.
 */
class ChatService : Service() {

    @Volatile private var running = false
    @Volatile private var conn: HttpURLConnection? = null
    private var worker: Thread? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MessageStore.init(this)
        val n = Notifier.serviceNotification(this, "Conectando…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_SERVICE, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART) {
            // Configuração mudou: derruba a conexão atual para reconectar com os dados novos.
            conn?.disconnect()
        }
        if (!running) {
            running = true
            worker = Thread(::loop, "receiver").also { it.start() }
            registerNetwork()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        conn?.disconnect()
        worker?.interrupt()
        netCallback?.let {
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        setStatus("Desconectado", false)
        super.onDestroy()
    }

    private fun registerNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Wi-Fi voltou: reconecta já, sem esperar o tempo de espera.
                if (!connected) conn?.disconnect()
                wake.offer(Unit)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    private val wake = LinkedBlockingQueue<Unit>()

    private fun sleepBackoff(ms: Long) {
        wake.clear()
        try { wake.poll(ms, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) {}
    }

    private fun loop() {
        var backoff = 2000L
        while (running) {
            val prefs = Prefs(this)
            if (!prefs.isConfigured) {
                setStatus("Não configurado", false)
                sleepBackoff(10000)
                continue
            }
            try {
                val code = prefs.code
                val since = prefs.lastId.ifEmpty { "all" }
                val url = URL(
                    "${prefs.server}/${Crypto.topicFor(code)}/json?since=" +
                        URLEncoder.encode(since, "UTF-8")
                )
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 100000 // o servidor manda "keepalive" a cada ~45s
                conn = c
                val reader = c.inputStream.bufferedReader()
                while (running) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val ev = JSONObject(line)
                    when (ev.optString("event")) {
                        "open" -> {
                            backoff = 2000L
                            setStatus("Conectado", true)
                        }
                        "message" -> {
                            handle(prefs, code, ev)
                            prefs.lastId = ev.optString("id")
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
                conn = null
            }
            if (!running) break
            setStatus("Sem conexão — tentando de novo…", false)
            sleepBackoff(backoff)
            backoff = (backoff * 2).coerceAtMost(60000L)
        }
    }

    private fun handle(prefs: Prefs, code: String, ev: JSONObject) {
        val plain = Crypto.decrypt(code, ev.optString("message")) ?: return
        val p = try { JSONObject(plain) } catch (_: Exception) { return }
        val dev = p.optString("dev")
        val mine = dev == prefs.deviceId
        val cid = p.optString("cid").ifEmpty { ev.optString("id") }
        if (mine) {
            MessageStore.setState(cid, Message.STATE_SENT)
            return
        }
        val m = Message(
            cid = cid,
            kind = p.optString("k", "msg"),
            from = p.optString("from", "?"),
            deviceId = dev,
            text = p.optString("t"),
            ts = p.optLong("ts", ev.optLong("time") * 1000),
            mine = false,
        )
        val isNew = MessageStore.add(m)
        if (isNew && !ChatActivity.isVisible) Notifier.showIncoming(this, m)
    }

    private fun setStatus(text: String, ok: Boolean) {
        status = text
        connected = ok
        MessageStore.notifyChanged()
        if (running) {
            getSystemService(NotificationManager::class.java)
                .notify(Notifier.ID_SERVICE, Notifier.serviceNotification(this, text))
        }
    }

    companion object {
        const val ACTION_RESTART = "restart"

        @Volatile var status: String = "Iniciando…"
        @Volatile var connected: Boolean = false

        fun start(context: Context, restart: Boolean = false) {
            val i = Intent(context, ChatService::class.java)
            if (restart) i.action = ACTION_RESTART
            try {
                context.startForegroundService(i)
            } catch (_: Exception) {
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatService::class.java))
        }
    }
}
