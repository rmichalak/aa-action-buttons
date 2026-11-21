package dev.robertmichalak.aaactionbuttons

import android.content.Context
import android.net.Uri
import android.util.Log

data class TaskerTask(
    val name: String,
    val projectName: String,
)

sealed class TaskerQueryResult {
    data class Ok(val tasks: List<TaskerTask>) : TaskerQueryResult()
    data object ExternalAccessDisabled : TaskerQueryResult()
    data object Unavailable : TaskerQueryResult()
}

object TaskerTasks {
    private const val TAG = "AaActionButtons"
    private val TASKS_URI: Uri = Uri.parse("content://net.dinglisch.android.tasker/tasks")
    private val PREFS_URI: Uri = Uri.parse("content://net.dinglisch.android.tasker/prefs")

    fun query(context: Context): TaskerQueryResult {
        if (!isExternalAccessEnabled(context)) {
            return TaskerQueryResult.ExternalAccessDisabled
        }
        return runCatching {
            val resolver = context.contentResolver
            resolver.query(TASKS_URI, null, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                val projectCol = cursor.getColumnIndex("project_name")
                if (nameCol < 0) return@use TaskerQueryResult.Unavailable
                val tasks = buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val project = if (projectCol >= 0) {
                            cursor.getString(projectCol).orEmpty()
                        } else {
                            ""
                        }
                        add(TaskerTask(name = name, projectName = project))
                    }
                }.distinctBy { it.name }.sortedBy { it.name.lowercase() }
                TaskerQueryResult.Ok(tasks)
            } ?: TaskerQueryResult.Unavailable
        }.getOrElse { error ->
            Log.e(TAG, "Tasker task query failed", error)
            TaskerQueryResult.Unavailable
        }
    }

    private fun isExternalAccessEnabled(context: Context): Boolean {
        return runCatching {
            context.contentResolver.query(PREFS_URI, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex("ext_access")
                if (col < 0 || !cursor.moveToFirst()) return@use true
                when (val raw = cursor.getString(col)?.lowercase()) {
                    "false", "0" -> false
                    else -> raw != null
                }
            } ?: true
        }.getOrDefault(true)
    }
}
