package com.mominul.infinitytunnel

/**
 * Bridge to the tun2socks core (gomobile .aar, bundled in the VPN phase).
 * Until the core is bundled, [available] is false and the VPN service
 * reports that VPN mode is not ready yet instead of failing silently.
 */
object Tun2SocksBridge {

    val available: Boolean
        get() = try {
            Class.forName("tun2socks.Tun2socks")
            true
        } catch (_: Throwable) {
            false
        }

    fun start(tunFd: Int, socksHost: String, socksPort: Int, mtu: Int) {
        // Filled in with the gomobile binding when the core is bundled:
        // Tun2socks.start(tunFd.toLong(), "$socksHost:$socksPort", mtu.toLong())
        LogBus.add("tun2socks: start fd=$tunFd -> $socksHost:$socksPort mtu=$mtu")
    }

    fun stop() {
        // Tun2socks.stop()
        LogBus.add("tun2socks: stop")
    }
}
