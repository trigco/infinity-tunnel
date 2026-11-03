package com.mominul.infinitytunnel

/**
 * Bridge to the tun2socks core (gomobile .aar built in CI from
 * tun2socks-go/). The generated binding is tun2socks.Tun2socks.
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
        tun2socks.Tun2socks.start(tunFd.toLong(), "socks5://$socksHost:$socksPort", mtu.toLong())
        LogBus.add("tun2socks: forwarding TUN -> socks5://$socksHost:$socksPort")
    }

    fun stop() {
        try {
            tun2socks.Tun2socks.stop()
        } catch (_: Throwable) {
        }
    }
}
