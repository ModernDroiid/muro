package app.muro.vpn

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Hilo que lee paquetes de la interfaz virtual.
 *
 * Cerrar el descriptor no despierta un read() bloqueado en Android, así que el hilo
 * espera con poll() sobre la interfaz y una tubería: cerrar la tubería lo detiene al
 * instante. Sin esto, cada reinicio de la VPN dejaría un hilo colgado.
 */
class TunPump(
    private val tun: ParcelFileDescriptor,
    private val onPacket: (ByteArray, Int) -> Unit,
) {
    private val wakeRead: java.io.FileDescriptor
    private val wakeWrite: java.io.FileDescriptor
    private val output = FileOutputStream(tun.fileDescriptor)
    private val reader: Thread

    init {
        val pipe = Os.pipe()
        wakeRead = pipe[0]
        wakeWrite = pipe[1]
        reader = thread(name = "muro-tun", isDaemon = true) { readLoop() }
    }

    /** Cada write() es un paquete; se serializan porque las respuestas llegan en paralelo. */
    fun write(packet: ByteArray) {
        try {
            synchronized(output) { output.write(packet) }
        } catch (e: IOException) {
            Log.d(TAG, "Respuesta descartada, túnel cerrado: ${e.message}")
        }
    }

    fun close() {
        Os.close(wakeWrite)
        reader.join(1_000)
        Os.close(wakeRead)
        tun.close()
    }

    private fun readLoop() {
        val input = FileInputStream(tun.fileDescriptor)
        val buffer = ByteArray(Tunnel.MTU)
        val tunPoll = StructPollfd().apply {
            fd = tun.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val wakePoll = StructPollfd().apply {
            fd = wakeRead
            events = OsConstants.POLLIN.toShort()
        }
        val polls = arrayOf(tunPoll, wakePoll)

        try {
            while (true) {
                try {
                    Os.poll(polls, -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    throw e
                }
                if (wakePoll.revents.toInt() != 0) return
                if (tunPoll.revents.toInt() and OsConstants.POLLIN == 0) return

                val length = input.read(buffer)
                if (length < 0) return
                if (length > 0) onPacket(buffer, length)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Lectura del túnel terminada: ${e.message}")
        } catch (e: ErrnoException) {
            Log.w(TAG, "Lectura del túnel terminada: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "MuroTun"
    }
}
