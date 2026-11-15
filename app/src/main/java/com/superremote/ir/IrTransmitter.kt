package com.superremote.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Sends IR codes through the phone's built-in IR blaster, off the UI thread. */
class IrTransmitter(context: Context) {
    private val ir: ConsumerIrManager? =
        context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    val hasEmitter: Boolean = try { ir?.hasIrEmitter() == true } catch (e: Exception) { false }

    fun frequencyInfo(): String = try {
        ir?.carrierFrequencies?.joinToString { "${it.minFrequency}-${it.maxFrequency} Hz" } ?: "unknown"
    } catch (e: Exception) { "unknown" }

    /**
     * Transmit a code. If a previous transmission is still running the request is dropped
     * (prevents a queue of stale presses while holding a button).
     * [onError] is called on the main thread with a readable message.
     */
    fun send(code: IrCode, onError: (String) -> Unit = {}) {
        val mgr = ir
        if (mgr == null || !hasEmitter) {
            onError("This phone has no IR blaster")
            return
        }
        val signal = try { IrEncoder.encode(code) } catch (e: Exception) {
            onError("Bad code: ${e.message}")
            return
        }
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                mgr.transmit(signal.frequency, signal.pattern)
            } catch (e: Exception) {
                main.post { onError("IR send failed: ${e.message}") }
            } finally {
                busy.set(false)
            }
        }
    }

    companion object {
        @Volatile private var instance: IrTransmitter? = null
        fun get(context: Context): IrTransmitter =
            instance ?: synchronized(this) { instance ?: IrTransmitter(context).also { instance = it } }
    }
}
