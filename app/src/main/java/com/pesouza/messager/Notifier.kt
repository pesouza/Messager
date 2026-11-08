package com.pesouza.messager

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notifier {
    private const val ID_MESSAGES = 10
    private const val ID_BUZZ = 11
    const val ID_SERVICE = 1

    private val unread = ArrayList<Message>()

    private fun openChat(context: Context): PendingIntent {
        val i = Intent(context, ChatActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Synchronized
    fun showIncoming(context: Context, m: Message) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (m.kind == "buzz") {
            val n = Notification.Builder(context, App.CH_BUZZ)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("🔔 ${m.from} está te chamando!")
                .setContentText("Toque para abrir as mensagens")
                .setCategory(Notification.CATEGORY_CALL)
                .setContentIntent(openChat(context))
                .setAutoCancel(true)
                .build()
            nm.notify(ID_BUZZ, n)
            return
        }

        unread.add(m)
        val style = Notification.InboxStyle()
        unread.takeLast(6).forEach { style.addLine("${it.from}: ${it.text}") }
        if (unread.size > 6) style.setSummaryText("+${unread.size - 6} mensagens")

        val n = Notification.Builder(context, App.CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(if (unread.size == 1) m.from else "${unread.size} mensagens novas")
            .setContentText(if (unread.size == 1) m.text else "${m.from}: ${m.text}")
            .setStyle(if (unread.size == 1) Notification.BigTextStyle().bigText(m.text) else style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setNumber(unread.size)
            .setContentIntent(openChat(context))
            .setAutoCancel(true)
            .build()
        nm.notify(ID_MESSAGES, n)
    }

    @Synchronized
    fun clear(context: Context) {
        unread.clear()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ID_MESSAGES)
        nm.cancel(ID_BUZZ)
    }

    fun serviceNotification(context: Context, status: String): Notification =
        Notification.Builder(context, App.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Messager pronto para receber")
            .setContentText(status)
            .setContentIntent(openChat(context))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
}
