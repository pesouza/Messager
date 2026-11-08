package com.pesouza.messager

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : Activity() {

    private lateinit var list: ListView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var warning: TextView
    private val adapter = MessagesAdapter()
    private val onChange: () -> Unit = { refresh() }
    private var lastBuzz = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MessageStore.init(this)
        if (!Prefs(this).isConfigured) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_chat)

        list = findViewById(R.id.list)
        input = findViewById(R.id.input)
        status = findViewById(R.id.status)
        warning = findViewById(R.id.warning)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.send).setOnClickListener { sendText() }
        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }
        findViewById<Button>(R.id.buzz).setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastBuzz < 5000) return@setOnClickListener
            lastBuzz = now
            Net.send(this, "buzz", "🔔 chamou atenção")
            Toast.makeText(this, "Chamando…", Toast.LENGTH_SHORT).show()
        }
        list.setOnItemClickListener { _, _, pos, _ ->
            val m = adapter.items[pos]
            if (m.mine && m.state == Message.STATE_FAILED) Net.retry(this, m)
        }
        warning.setOnClickListener { fixWarning() }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        ChatService.start(this)
    }

    override fun onResume() {
        super.onResume()
        if (!::list.isInitialized) return
        isVisible = true
        Notifier.clear(this)
        MessageStore.addListener(onChange)
        updateTitle()
        refresh()
        ChatService.start(this)
    }

    override fun onPause() {
        isVisible = false
        if (::list.isInitialized) MessageStore.removeListener(onChange)
        super.onPause()
    }

    private fun updateTitle() {
        findViewById<TextView>(R.id.title).text = "Messager · ${Prefs(this).name}"
    }

    private fun sendText() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        if (text.length > 2000) {
            Toast.makeText(this, "Mensagem muito longa", Toast.LENGTH_SHORT).show()
            return
        }
        input.setText("")
        Net.send(this, "msg", text)
    }

    private fun refresh() {
        val atBottom = list.lastVisiblePosition >= adapter.count - 2
        adapter.items = MessageStore.all()
        adapter.notifyDataSetChanged()
        if (atBottom) list.setSelection(adapter.count - 1)
        status.text = ChatService.status
        updateWarning()
    }

    private fun updateWarning() {
        val nm = getSystemService(NotificationManager::class.java)
        val pm = getSystemService(PowerManager::class.java)
        warning.tag = null
        when {
            !nm.areNotificationsEnabled() -> {
                warning.text = "⚠ As notificações estão desligadas. Toque aqui para ativar, senão você não será avisado."
                warning.tag = "notif"
            }
            !pm.isIgnoringBatteryOptimizations(packageName) -> {
                warning.text = "⚠ Toque aqui e escolha \"Permitir\" para o app não ser pausado pela economia de bateria (senão os avisos podem atrasar)."
                warning.tag = "battery"
            }
        }
        warning.visibility = if (warning.tag == null) View.GONE else View.VISIBLE
    }

    private fun fixWarning() {
        when (warning.tag) {
            "notif" -> startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            )
            "battery" -> SetupActivity.requestBatteryExemption(this)
        }
    }

    inner class MessagesAdapter : BaseAdapter() {
        var items: List<Message> = emptyList()
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dayFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_message, parent, false)
            val m = items[position]
            val row = v.findViewById<LinearLayout>(R.id.row)
            val bubble = v.findViewById<LinearLayout>(R.id.bubble)
            val sender = v.findViewById<TextView>(R.id.sender)
            val text = v.findViewById<TextView>(R.id.text)
            val time = v.findViewById<TextView>(R.id.time)

            row.gravity = if (m.mine) Gravity.END else Gravity.START
            bubble.setBackgroundResource(if (m.mine) R.drawable.bubble_mine else R.drawable.bubble_other)
            sender.visibility = if (m.mine) View.GONE else View.VISIBLE
            sender.text = m.from
            text.text = m.text
            text.setTypeface(null, if (m.kind == "buzz") android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)

            val today = System.currentTimeMillis() - m.ts < 20 * 3600 * 1000L
            val t = (if (today) timeFmt else dayFmt).format(Date(m.ts))
            time.text = if (!m.mine) t else when (m.state) {
                Message.STATE_PENDING -> "$t  🕓"
                Message.STATE_FAILED -> "$t  ❗ não enviada — toque para tentar de novo"
                else -> "$t  ✓"
            }
            return v
        }
    }

    companion object {
        @Volatile var isVisible = false
    }
}
