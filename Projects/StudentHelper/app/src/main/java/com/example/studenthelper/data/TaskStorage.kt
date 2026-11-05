package com.example.studenthelper.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.studentDataStore by preferencesDataStore(name = "student_helper_tasks")
private val TASKS_JSON_KEY = stringPreferencesKey("tasks_json")

enum class TaskType {
    HOMEWORK,
    EXAM
}

data class StudyTask(
    val id: String,
    val title: String,
    val type: TaskType,
    val dueDateEpochDay: Long,
    val isCompleted: Boolean = false
)

/**
 * Stores the complete task list in app-private DataStore storage.
 * DataStore writes are transactional and run away from the UI thread.
 */
class TaskStorage(context: Context) {
    private val dataStore = context.applicationContext.studentDataStore

    val tasks: Flow<List<StudyTask>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> decodeTasks(preferences[TASKS_JSON_KEY]) }
        // JSON parsing can become noticeable as the task list grows. Keep it off
        // the UI thread so the first screen frame and scrolling stay responsive.
        .flowOn(Dispatchers.Default)

    suspend fun add(task: StudyTask) {
        changeTasks { current ->
            current.filterNot { it.id == task.id } + task
        }
    }

    suspend fun setCompleted(taskId: String, completed: Boolean) {
        changeTasks { current ->
            current.map { task ->
                if (task.id == taskId) task.copy(isCompleted = completed) else task
            }
        }
    }

    suspend fun rename(taskId: String, newTitle: String) {
        changeTasks { current ->
            current.map { task ->
                if (task.id == taskId) task.copy(title = newTitle) else task
            }
        }
    }

    suspend fun delete(taskId: String) {
        changeTasks { current -> current.filterNot { it.id == taskId } }
    }

    private suspend fun changeTasks(transform: (List<StudyTask>) -> List<StudyTask>) {
        dataStore.edit { preferences ->
            val current = decodeTasks(preferences[TASKS_JSON_KEY])
            preferences[TASKS_JSON_KEY] = encodeTasks(transform(current))
        }
    }
}

private fun encodeTasks(tasks: List<StudyTask>): String {
    val array = JSONArray()

    tasks.forEach { task ->
        val item = JSONObject()
            .put("id", task.id)
            .put("title", task.title)
            .put("type", task.type.name)
            .put("dueDateEpochDay", task.dueDateEpochDay)
            .put("completed", task.isCompleted)

        array.put(item)
    }

    return array.toString()
}

private fun decodeTasks(json: String?): List<StudyTask> {
    if (json.isNullOrBlank()) return emptyList()

    return try {
        val array = JSONArray(json)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                val type = runCatching {
                    TaskType.valueOf(item.optString("type"))
                }.getOrNull() ?: continue
                val dueDate = item.optLong("dueDateEpochDay", Long.MIN_VALUE)

                if (id.isBlank() || title.isBlank() || dueDate == Long.MIN_VALUE) continue

                add(
                    StudyTask(
                        id = id,
                        title = title,
                        type = type,
                        dueDateEpochDay = dueDate,
                        isCompleted = item.optBoolean("completed", false)
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
