package com.pesouza.messager

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        MessageStore.init(this)
        val prefs = Prefs(this)

        val name = findViewById<EditText>(R.id.name)
        val code = findViewById<EditText>(R.id.code)
        val server = findViewById<EditText>(R.id.server)
        name.setText(prefs.name)
        code.setText(prefs.code)
        server.setText(prefs.server)

        findViewById<Button>(R.id.generate).setOnClickListener {
            code.setText(Crypto.generateCode())
        }
        findViewById<Button>(R.id.battery).setOnClickListener { requestBatteryExemption(this) }

        findViewById<Button>(R.id.save).setOnClickListener {
            val n = name.text.toString().trim()
            val c = code.text.toString().trim().uppercase()
            val s = server.text.toString().trim().ifEmpty { Prefs.DEFAULT_SERVER }
            if (n.isEmpty()) {
                name.error = "Digite um nome"; return@setOnClickListener
            }
            if (Crypto.normalize(c).length < 8) {
                code.error = "Código muito curto (use \"Gerar código novo\")"; return@setOnClickListener
            }
            if (!s.startsWith("https://") && !s.startsWith("http://")) {
                server.error = "Endereço inválido"; return@setOnClickListener
            }
            val codeChanged = Crypto.normalize(c) != Crypto.normalize(prefs.code) ||
                s.trimEnd('/') != prefs.server
            if (codeChanged && prefs.code.isNotEmpty() && MessageStore.all().isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Trocar o código?")
                    .setMessage("O histórico atual será apagado deste celular e você passará a conversar com quem usa o novo código.")
                    .setPositiveButton("Trocar") { _, _ -> save(prefs, n, c, s, true) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } else {
                save(prefs, n, c, s, codeChanged)
            }
        }
    }

    private fun save(prefs: Prefs, n: String, c: String, s: String, codeChanged: Boolean) {
        prefs.name = n
        prefs.code = c
        prefs.server = s
        if (codeChanged) {
            prefs.lastId = ""
            MessageStore.clear()
        }
        ChatService.start(this, restart = true)
        Toast.makeText(this, "Pronto! Use o código $c no outro celular.", Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    companion object {
        fun requestBatteryExemption(context: Context) {
            val pm = context.getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                Toast.makeText(context, "Já está liberado ✓", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}
