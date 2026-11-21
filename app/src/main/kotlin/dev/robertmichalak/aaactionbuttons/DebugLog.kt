package dev.robertmichalak.aaactionbuttons

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-app debug ring buffer so phone-side events can be copied without logcat.
 */
object DebugLog {
    private const val TAG = "AaActionButtons"
    private const val PREFS = "aa_action_buttons_debug"
    private const val KEY = "lines"
    private const val MAX_LINES = 80

    private val lines = CopyOnWriteArrayList<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)
        if (!stored.isNullOrBlank()) {
            lines.addAll(stored.split('\n').filter { it.isNotBlank() })
        }
        loaded = true
    }

    fun i(message: String) {
        append("I", message)
        Log.i(TAG, message)
    }

    fun w(message: String) {
        append("W", message)
        Log.w(TAG, message)
    }

    fun e(message: String, error: Throwable? = null) {
        val detail = if (error == null) {
            message
        } else {
            "$message: ${error.javaClass.simpleName}: ${error.message}"
        }
        append("E", detail)
        if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
    }

    fun snapshot(): String = lines.joinToString("\n")

    @Synchronized
    fun clear(context: Context) {
        lines.clear()
        persist(context)
        Log.i(TAG, "Debug log cleared")
    }

    private fun append(level: String, message: String) {
        val line = "${timeFormat.format(Date())} $level $message"
        lines.add(line)
        while (lines.size > MAX_LINES) {
            lines.removeAt(0)
        }
    }

    @Synchronized
    fun persist(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, snapshot())
            .apply()
    }
}
