package com.pesouza.messager

import android.content.Context
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("messager", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var code: String
        get() = sp.getString("code", "") ?: ""
        set(v) = sp.edit().putString("code", v).apply()

    var server: String
        get() = sp.getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = sp.edit().putString("server", v.trim().trimEnd('/')).apply()

    /** Id (do servidor) da última mensagem processada, para retomar sem perder nada. */
    var lastId: String
        get() = sp.getString("lastId", "") ?: ""
        set(v) = sp.edit().putString("lastId", v).apply()

    val deviceId: String
        get() {
            var id = sp.getString("deviceId", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                sp.edit().putString("deviceId", id).apply()
            }
            return id
        }

    val isConfigured: Boolean get() = name.isNotBlank() && Crypto.normalize(code).length >= 8

    companion object {
        const val DEFAULT_SERVER = "https://ntfy.sh"
    }
}
