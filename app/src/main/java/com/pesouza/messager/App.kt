package com.pesouza.messager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)

        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val messages = NotificationChannel(
            CH_MESSAGES, "Mensagens", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Novas mensagens recebidas"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audio)
            enableLights(true)
            setShowBadge(true)
        }

        val buzz = NotificationChannel(
            CH_BUZZ, "Chamadas de atenção", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Quando o outro celular aperta \"Chamar\""
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800, 300, 800)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), audio)
            enableLights(true)
            setShowBadge(true)
        }

        val service = NotificationChannel(
            CH_SERVICE, "Conexão em segundo plano", NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Mantém o app conectado para receber mensagens"
            setShowBadge(false)
        }

        nm.createNotificationChannels(listOf(messages, buzz, service))
    }

    companion object {
        const val CH_MESSAGES = "messages"
        const val CH_BUZZ = "buzz"
        const val CH_SERVICE = "service"
    }
}
