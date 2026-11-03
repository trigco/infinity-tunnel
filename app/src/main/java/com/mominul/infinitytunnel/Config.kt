package com.mominul.infinitytunnel

import android.content.Context
import android.content.SharedPreferences

data class TunnelConfig(
    val serverName: String = "",
    val publicKey: String = "",
    val resolver: String = "1.1.1.1:53",
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val sshPassword: String = "",
    val udpgwPort: Int = 7300,
    val socksPort: Int = 3080,
    val mtu: Int = 1400
) {
    fun missingFields(): List<String> {
        val missing = mutableListOf<String>()
        if (serverName.isBlank()) missing.add("server name")
        if (publicKey.isBlank()) missing.add("public key")
        if (sshHost.isBlank()) missing.add("SSH host")
        if (sshUser.isBlank()) missing.add("SSH username")
        if (sshPassword.isBlank()) missing.add("SSH password")
        return missing
    }
}

class ConfigStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("infinity_config", Context.MODE_PRIVATE)

    fun load(): TunnelConfig {
        val d = TunnelConfig()
        return TunnelConfig(
            serverName = prefs.getString("serverName", d.serverName) ?: d.serverName,
            publicKey = prefs.getString("publicKey", d.publicKey) ?: d.publicKey,
            resolver = prefs.getString("resolver", d.resolver) ?: d.resolver,
            sshHost = prefs.getString("sshHost", d.sshHost) ?: d.sshHost,
            sshPort = prefs.getInt("sshPort", d.sshPort),
            sshUser = prefs.getString("sshUser", d.sshUser) ?: d.sshUser,
            sshPassword = prefs.getString("sshPassword", d.sshPassword) ?: d.sshPassword,
            udpgwPort = prefs.getInt("udpgwPort", d.udpgwPort),
            socksPort = prefs.getInt("socksPort", d.socksPort),
            mtu = prefs.getInt("mtu", d.mtu)
        )
    }

    fun save(config: TunnelConfig) {
        prefs.edit()
            .putString("serverName", config.serverName.trim())
            .putString("publicKey", config.publicKey.trim())
            .putString("resolver", config.resolver.trim())
            .putString("sshHost", config.sshHost.trim())
            .putInt("sshPort", config.sshPort)
            .putString("sshUser", config.sshUser.trim())
            .putString("sshPassword", config.sshPassword)
            .putInt("udpgwPort", config.udpgwPort)
            .putInt("socksPort", config.socksPort)
            .putInt("mtu", config.mtu)
            .apply()
    }
}

/** Simple in-app log buffer shared by the engine and the Logs screen. */
object LogBus {
    private const val MAX_LINES = 300
    private val lines = mutableListOf<String>()
    var version: Int = 0
        private set

    @Synchronized
    fun add(line: String) {
        val stamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        lines.add("[$stamp] $line")
        while (lines.size > MAX_LINES) lines.removeAt(0)
        version++
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
        version++
    }
}
