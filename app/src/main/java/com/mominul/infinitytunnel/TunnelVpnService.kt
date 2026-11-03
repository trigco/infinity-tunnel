package com.mominul.infinitytunnel

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * VPN mode: establishes the TUN interface and hands its fd to the
 * tun2socks bridge, which forwards all traffic to the local SOCKS5
 * proxy run by TunnelEngine. Requires the tun2socks core (VPN phase).
 */
class TunnelVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        val socksPort = intent?.getIntExtra(EXTRA_SOCKS_PORT, 3080) ?: 3080
        val mtu = intent?.getIntExtra(EXTRA_MTU, 1400) ?: 1400

        if (!Tun2SocksBridge.available) {
            LogBus.add("VPN mode is not bundled in this build yet — use Proxy mode")
            stopSelf()
            return START_NOT_STICKY
        }

        val pfd = Builder()
            .setSession("Infinity Tunnel")
            .addAddress("10.10.10.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setMtu(mtu)
            .establish()

        if (pfd == null) {
            LogBus.add("ERROR: could not establish the VPN interface")
            stopSelf()
            return START_NOT_STICKY
        }
        tun = pfd
        Tun2SocksBridge.start(pfd.fd, "127.0.0.1", socksPort, mtu)
        LogBus.add("VPN mode on — all apps now use the tunnel")
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun stopVpn() {
        try { Tun2SocksBridge.stop() } catch (_: Exception) {}
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.mominul.infinitytunnel.VPN_START"
        const val ACTION_STOP = "com.mominul.infinitytunnel.VPN_STOP"
        const val EXTRA_SOCKS_PORT = "socksPort"
        const val EXTRA_MTU = "mtu"
    }
}
