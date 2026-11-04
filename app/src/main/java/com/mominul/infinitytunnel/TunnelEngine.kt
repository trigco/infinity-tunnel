package com.mominul.infinitytunnel

import android.content.Context
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File

/**
 * Runs the real tunnel: dnstt-client process (bundled native binary) ->
 * SSH session over the tunnel's local listener -> local SOCKS5 proxy.
 */
object TunnelEngine {

    enum class State { Stopped, Starting, Running, Failed }

    @Volatile
    var state: State = State.Stopped
        private set

    @Volatile
    var onStateChanged: ((State) -> Unit)? = null

    private var dnsttProcess: Process? = null
    private var sshSession: Session? = null
    private var socksServer: SocksServer? = null

    private const val DNSTT_LOCAL_PORT = 11080

    private fun setState(s: State) {
        state = s
        onStateChanged?.invoke(s)
    }

    /**
     * The dnstt engine is an executable shipped as a jniLib. Prefer the copy
     * Android extracted into nativeLibraryDir; if packaging left it inside
     * the APK (no on-disk file), pull it out of the APK into filesDir and
     * mark it executable ourselves.
     */
    private fun resolveEngineBinary(context: Context): File? {
        val native = File(context.applicationInfo.nativeLibraryDir, "libdnstt.so")
        if (native.exists()) return native
        return try {
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return null
            val outDir = File(context.filesDir, "engine").apply { mkdirs() }
            val out = File(outDir, "libdnstt.so")
            java.util.zip.ZipFile(context.applicationInfo.sourceDir).use { zip ->
                val entry = zip.getEntry("lib/$abi/libdnstt.so") ?: return null
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            out.setExecutable(true)
            LogBus.add("Engine binary self-extracted from APK ($abi)")
            out
        } catch (e: Exception) {
            LogBus.add("ERROR: engine extract failed: ${e.message}")
            null
        }
    }

    @Synchronized
    fun start(context: Context, config: TunnelConfig) {
        if (state == State.Starting || state == State.Running) return
        setState(State.Starting)
        Thread { startInternal(context, config) }.start()
    }

    private fun startInternal(context: Context, config: TunnelConfig) {
        try {
            // 1) dnstt-client process
            val binary = resolveEngineBinary(context)
            if (binary == null) {
                LogBus.add("ERROR: dnstt engine binary missing (not extracted, not in APK)")
                setState(State.Failed)
                return
            }
            val cmd = listOf(
                binary.absolutePath,
                "-udp", config.resolver,
                "-pubkey", config.publicKey,
                config.serverName,
                "127.0.0.1:$DNSTT_LOCAL_PORT"
            )
            LogBus.add("Starting DNSTT: ${config.serverName} via ${config.resolver}")
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            dnsttProcess = process
            Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        if (line.isNotBlank()) LogBus.add("dnstt: $line")
                    }
                } catch (_: Exception) {
                }
            }.start()

            // 2) wait for the local listener, then SSH through it
            if (!waitForPort(DNSTT_LOCAL_PORT, 20_000)) {
                LogBus.add("ERROR: DNSTT local listener did not come up")
                stopInternal()
                setState(State.Failed)
                return
            }
            LogBus.add("DNSTT session up — logging in via SSH…")

            val jsch = JSch()
            val session = jsch.getSession(config.sshUser, "127.0.0.1", DNSTT_LOCAL_PORT)
            session.setPassword(config.sshPassword)
            session.setConfig("StrictHostKeyChecking", "no")
            session.timeout = 30_000
            session.connect(30_000)
            sshSession = session
            LogBus.add("SSH connected: ${config.sshUser}@${config.sshHost}:${config.sshPort}")

            // 3) local SOCKS5 proxy backed by SSH direct-tcpip channels
            val socks = SocksServer(session, config.socksPort)
            socks.start()
            socksServer = socks
            LogBus.add("SOCKS5 proxy ready on 127.0.0.1:${config.socksPort}")
            LogBus.add("Connected")
            setState(State.Running)
        } catch (e: Exception) {
            LogBus.add("ERROR: ${e.message ?: e.toString()}")
            stopInternal()
            setState(State.Failed)
        }
    }

    private fun waitForPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket("127.0.0.1", port).close()
                return true
            } catch (_: Exception) {
                Thread.sleep(300)
            }
        }
        return false
    }

    @Synchronized
    fun stop() {
        if (state == State.Stopped) return
        stopInternal()
        LogBus.add("Disconnected")
        setState(State.Stopped)
    }

    private fun stopInternal() {
        try { socksServer?.stop() } catch (_: Exception) {}
        socksServer = null
        try { sshSession?.disconnect() } catch (_: Exception) {}
        sshSession = null
        try { dnsttProcess?.destroy() } catch (_: Exception) {}
        dnsttProcess = null
    }
}
