package dev.robertmichalak.aaactionbuttons

import android.content.Context
import android.content.Intent
import dev.robertmichalak.aaactionbuttons.model.Action
import dev.robertmichalak.aaactionbuttons.model.ButtonConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ActionRunner(private val context: Context) {
    suspend fun run(button: ButtonConfig) {
        DebugLog.init(context)
        DebugLog.i("Running button id=${button.id} label='${button.label}' type=${button.action::class.simpleName}")
        when (val action = button.action) {
            is Action.Tasker -> runTasker(action)
            is Action.Http -> runHttp(action)
            is Action.IntentAction -> runIntent(action)
        }
        DebugLog.persist(context)
    }

    private suspend fun runTasker(action: Action.Tasker) {
        val result = TaskerInvoker.invoke(context, action.taskName)
        when (result.status) {
            TaskerInvoker.Status.OK -> {
                DebugLog.i(
                    "Tasker invoke OK package=${result.taskerPackage} " +
                        "receivers=${result.receiverCount} detail=${result.detail}",
                )
            }
            TaskerInvoker.Status.SentNoCompletion -> {
                DebugLog.w(
                    "Tasker broadcast sent but no completion callback " +
                        "(package=${result.taskerPackage}). Task may still be blocked inside Tasker.",
                )
                throw IllegalStateException("Tasker sent but no completion: ${result.status}")
            }
            else -> {
                DebugLog.e("Tasker invoke failed status=${result.status} detail=${result.detail}")
                throw IllegalStateException(
                    "Tasker invoke failed: ${result.status}" +
                        (result.detail?.let { " ($it)" } ?: ""),
                )
            }
        }
    }

    private suspend fun runHttp(action: Action.Http) = withContext(Dispatchers.IO) {
        val connection = (URL(action.url).openConnection() as HttpURLConnection).apply {
            requestMethod = action.method.uppercase()
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doInput = true
            action.headers.forEach { (key, value) -> setRequestProperty(key, value) }
            val body = action.body
            if (body != null && requestMethod != "GET" && requestMethod != "HEAD") {
                doOutput = true
                outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
        }
        try {
            val code = connection.responseCode
            DebugLog.i("HTTP ${action.method.uppercase()} finished status=$code")
        } catch (error: Exception) {
            DebugLog.e("HTTP request failed", error)
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun runIntent(action: Action.IntentAction) {
        val intent = Intent(action.action)
        action.packageName?.let { intent.setPackage(it) }
        action.extras.forEach { (key, value) -> intent.putExtra(key, value) }
        if (action.startActivity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            DebugLog.i("Started activity intent action=${action.action}")
        } else {
            context.sendBroadcast(intent)
            DebugLog.i("Sent broadcast intent action=${action.action}")
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8_000
    }
}
