package com.mominul.infinitytunnel

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal SOCKS5 server (CONNECT, TCP only) that forwards every connection
 * through an SSH direct-tcpip channel. UDP ASSOCIATE is refused in v1 —
 * UDP comes in phase 2 via the udpgw client.
 */
class SocksServer(private val session: Session, private val port: Int) {

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null

    fun start() {
        running = true
        val ss = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        Thread {
            while (running) {
                try {
                    val client = ss.accept()
                    Thread { handle(client) }.start()
                } catch (_: Exception) {
                    if (running) LogBus.add("socks: accept failed")
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 60_000
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // greeting: VER, NMETHODS, METHODS[]
            val ver = input.read()
            if (ver != 5) { client.close(); return }
            val nMethods = input.read()
            if (nMethods < 0) { client.close(); return }
            val methods = ByteArray(nMethods)
            readFully(input, methods)
            output.write(byteArrayOf(5, 0)) // no-auth
            output.flush()

            // request: VER, CMD, RSV, ATYP, DST, PORT
            val reqVer = input.read()
            val cmd = input.read()
            input.read() // reserved
            val atyp = input.read()
            if (reqVer != 5) { client.close(); return }
            if (cmd != 1) { // CONNECT only
                reply(output, 7) // command not supported
                client.close(); return
            }
            val host: String = when (atyp) {
                1 -> {
                    val b = ByteArray(4); readFully(input, b)
                    b.joinToString(".") { (it.toInt() and 0xFF).toString() }
                }
                3 -> {
                    val len = input.read()
                    val b = ByteArray(len); readFully(input, b)
                    String(b, Charsets.UTF_8)
                }
                else -> {
                    reply(output, 8) // address type not supported (IPv6 in v1)
                    client.close(); return
                }
            }
            val portHi = input.read(); val portLo = input.read()
            val targetPort = (portHi shl 8) or portLo

            val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(host)
            channel.setPort(targetPort)
            channel.connect(15_000)

            reply(output, 0)
            relay(client, input, output, channel)
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun reply(output: OutputStream, status: Int) {
        output.write(byteArrayOf(5, status.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun relay(client: Socket, clientIn: InputStream, clientOut: OutputStream, channel: ChannelDirectTCPIP) {
        val chanIn = channel.inputStream
        val chanOut = channel.outputStream
        val t = Thread {
            try {
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = chanIn.read(buf)
                    if (n < 0) break
                    clientOut.write(buf, 0, n)
                    clientOut.flush()
                }
            } catch (_: Exception) {
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }
        t.start()
        try {
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = clientIn.read(buf)
                if (n < 0) break
                chanOut.write(buf, 0, n)
                chanOut.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { channel.disconnect() } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw java.io.EOFException("socks: unexpected EOF")
            off += n
        }
    }
}
