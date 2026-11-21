package dev.robertmichalak.aaactionbuttons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.PatternMatcher
import android.os.Process
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * Invokes a named Tasker task using the same intent shape as Tasker's TaskerIntent helper.
 * @see <a href="https://tasker.joaoapps.com/invoketasks.html">Invoking Tasks Programatically</a>
 */
object TaskerInvoker {
    private const val TASKER_PACKAGE = "net.dinglisch.android.tasker"
    private const val TASKER_PACKAGE_MARKET = "net.dinglisch.android.taskerm"
    private const val ACTION_TASK = "net.dinglisch.android.tasker.ACTION_TASK"
    private const val ACTION_TASK_COMPLETE = "net.dinglisch.android.tasker.ACTION_TASK_COMPLETE"
    private const val PERMISSION_RUN_TASKS = "net.dinglisch.android.tasker.PERMISSION_RUN_TASKS"
    private const val PREFS_URI = "content://net.dinglisch.android.tasker/prefs"
    private const val TASKS_URI = "content://net.dinglisch.android.tasker/tasks"
    private const val EXTRA_VERSION = "version_number"
    private const val EXTRA_TASK_NAME = "task_name"
    private const val EXTRA_SUCCESS = "success"
    private const val EXTRA_OUTPUT = "output"
    private const val VERSION_NUMBER = "1.1"
    /** Long-running Tasker tasks may take minutes before ACTION_TASK_COMPLETE. */
    private const val COMPLETION_TIMEOUT_MS = 600_000L

    enum class Status {
        NotInstalled,
        NoPermission,
        NotEnabled,
        AccessBlocked,
        NoReceiver,
        TaskMissing,
        SentNoCompletion,
        Failed,
        OK,
    }

    data class Result(
        val status: Status,
        val taskerPackage: String?,
        val receiverCount: Int,
        val detail: String? = null,
    )

    suspend fun invoke(context: Context, taskName: String): Result {
        DebugLog.init(context)
        val appContext = context.applicationContext
        DebugLog.i("Tasker invoke requested task='$taskName'")

        val taskerPackage = installedTaskerPackage(appContext)
        if (taskerPackage == null) {
            DebugLog.w("Tasker not installed")
            return Result(Status.NotInstalled, null, 0)
        }
        DebugLog.i("Tasker package=$taskerPackage")

        if (!havePermission(appContext)) {
            DebugLog.w(
                "Missing $PERMISSION_RUN_TASKS. Fix: adb shell pm grant ${appContext.packageName} $PERMISSION_RUN_TASKS",
            )
            return Result(Status.NoPermission, taskerPackage, 0)
        }
        DebugLog.i("PERMISSION_RUN_TASKS granted")

        val enabled = prefTrue(appContext, "enabled")
        val extAccess = prefTrue(appContext, "ext_access")
        DebugLog.i("Tasker prefs enabled=$enabled ext_access=$extAccess")
        if (!enabled) {
            DebugLog.w("Tasker is disabled")
            return Result(Status.NotEnabled, taskerPackage, 0)
        }
        if (!extAccess) {
            DebugLog.w("Tasker external access blocked")
            return Result(Status.AccessBlocked, taskerPackage, 0)
        }

        if (!taskExists(appContext, taskName)) {
            DebugLog.w("No Tasker task with exact name '$taskName' (check spelling/case)")
            return Result(Status.TaskMissing, taskerPackage, 0, "task name not found in Tasker")
        }
        DebugLog.i("Task name exists in Tasker task list")

        val intent = buildIntent(taskName)
        // Resolve receivers without a package first, then pin to ONE component.
        // Package-wide sendBroadcast can hit multiple Tasker receivers and run the task twice.
        val probe = Intent(intent).apply { `package` = null }
        var receivers = queryReceivers(appContext, probe)
        if (receivers.isEmpty()) {
            probe.setPackage(taskerPackage)
            receivers = queryReceivers(appContext, probe)
        }
        DebugLog.i("Matching ACTION_TASK receivers: ${receivers.size}")
        receivers.forEach { info ->
            DebugLog.i("Receiver ${info.activityInfo.packageName}/${info.activityInfo.name}")
        }
        val target = receivers.firstOrNull()
            ?: run {
                DebugLog.w("No Tasker broadcast receiver for ACTION_TASK")
                return Result(Status.NoReceiver, taskerPackage, 0)
            }
        intent.setPackage(null)
        intent.setClassName(target.activityInfo.packageName, target.activityInfo.name)

        val completion = withTimeoutOrNull(COMPLETION_TIMEOUT_MS) {
            awaitCompletion(appContext, taskName) {
                appContext.sendBroadcast(intent)
                DebugLog.i(
                    "Broadcast sent once to ${target.activityInfo.packageName}/${target.activityInfo.name} data=${intent.data}",
                )
            }
        }

        DebugLog.persist(appContext)
        return when {
            completion == null -> {
                DebugLog.w(
                    "No ACTION_TASK_COMPLETE within ${COMPLETION_TIMEOUT_MS / 1000}s. " +
                        "Unlocking buttons. Tasker may still be running, or never answered.",
                )
                Result(Status.SentNoCompletion, taskerPackage, receivers.size.coerceAtLeast(1))
            }
            completion.success -> {
                DebugLog.i("Tasker completion success=true output=${completion.output}")
                Result(Status.OK, taskerPackage, receivers.size.coerceAtLeast(1), completion.output)
            }
            else -> {
                DebugLog.e("Tasker completion success=false output=${completion.output}")
                Result(Status.Failed, taskerPackage, receivers.size.coerceAtLeast(1), completion.output)
            }
        }
    }

    private data class Completion(val success: Boolean, val output: String?)

    private suspend fun awaitCompletion(
        context: Context,
        taskName: String,
        send: () -> Unit,
    ): Completion = suspendCancellableCoroutine { cont ->
        val filter = IntentFilter(ACTION_TASK_COMPLETE).apply {
            addDataScheme("task")
            addDataPath(taskName, PatternMatcher.PATTERN_LITERAL)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
                val output = intent.getStringExtra(EXTRA_OUTPUT)
                DebugLog.i("Got ACTION_TASK_COMPLETE success=$success")
                runCatching { context.unregisterReceiver(this) }
                if (cont.isActive) {
                    cont.resume(Completion(success, output))
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        cont.invokeOnCancellation {
            runCatching { context.unregisterReceiver(receiver) }
        }

        try {
            send()
        } catch (error: Exception) {
            runCatching { context.unregisterReceiver(receiver) }
            cont.resume(Completion(false, error.message))
        }
    }

    private fun buildIntent(taskName: String): Intent {
        return Intent(ACTION_TASK).apply {
            // Tasker's receiver filter matches an id: URI (see TaskerIntent.setRandomData).
            data = Uri.parse("id:${Random.nextLong()}")
            putExtra(EXTRA_VERSION, VERSION_NUMBER)
            putExtra(EXTRA_TASK_NAME, taskName)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
    }

    private fun queryReceivers(context: Context, intent: Intent) =
        context.packageManager.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)

    private fun taskExists(context: Context, taskName: String): Boolean {
        return runCatching {
            context.contentResolver.query(Uri.parse(TASKS_URI), null, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                if (nameCol < 0) return@use false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol) == taskName) return@use true
                }
                false
            } ?: false
        }.getOrDefault(false)
    }

    private fun installedTaskerPackage(context: Context): String? {
        val pm = context.packageManager
        return when {
            isInstalled(pm, TASKER_PACKAGE_MARKET) -> TASKER_PACKAGE_MARKET
            isInstalled(pm, TASKER_PACKAGE) -> TASKER_PACKAGE
            else -> null
        }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun havePermission(context: Context): Boolean {
        return context.checkPermission(
            PERMISSION_RUN_TASKS,
            Process.myPid(),
            Process.myUid(),
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun prefTrue(context: Context, column: String): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                Uri.parse(PREFS_URI),
                arrayOf(column),
                null,
                null,
                null,
            )
            if (cursor == null || !cursor.moveToFirst()) {
                DebugLog.w("No Tasker prefs cursor for $column")
                false
            } else {
                val value = cursor.getString(0)
                DebugLog.i("Tasker pref $column='$value'")
                java.lang.Boolean.TRUE.toString().equals(value, ignoreCase = true)
            }
        } catch (error: Exception) {
            DebugLog.e("Tasker prefs query failed for $column", error)
            false
        } finally {
            cursor?.close()
        }
    }
}
