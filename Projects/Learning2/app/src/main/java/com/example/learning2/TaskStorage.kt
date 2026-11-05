
package com.example.learning2

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// "object" creates one shared storage helper.
// We can call TaskStorage.saveTasks(...) without creating an instance.
object TaskStorage {

    // The name of the SharedPreferences file where our data is stored.
    private const val PREFS_NAME = "task_storage"

    // The key used to find our saved task list inside that file.
    private const val TASKS_KEY = "tasks"

    // Saves the current task list to the phone.
    // Context gives this function access to Android's storage system.
    // List<Task> means we accept a list of our existing Task objects.
    fun saveTasks(context: Context, tasks: List<Task>) {

        // Create an empty JSON array to hold all tasks.
        val jsonArray = JSONArray()

        // Convert each Task object into a JSON object.
        for (task in tasks) {

            // Create an object representing one task.
            val jsonObject = JSONObject()

            // Store the task's name and completion status as JSON fields.
            jsonObject.put("name", task.name)
            jsonObject.put("isCompleted", task.isCompleted)

            // Add this task object to the overall task array.
            jsonArray.put(jsonObject)
        }

        // Open our app's private SharedPreferences storage.
        // If the file doesn't exist yet, Android creates it.
        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        // Start a storage edit operation.
        preferences.edit()

            // Convert the JSON array into text and store it under TASKS_KEY.
            // JSON text can represent the entire list in one saved value.
            .putString(TASKS_KEY, jsonArray.toString())

            // Apply the change without blocking the UI while waiting
            // for the disk write to finish.
            .apply()
    }


    // Reads the saved task list and converts it back into Task objects.
    // Returns an empty list if nothing has been saved yet.
    fun loadTasks(context: Context): List<Task> {

        // Open the same preferences file used by saveTasks().
        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        // Read the saved JSON string.
        // If the key doesn't exist, use an empty JSON array instead.
        val savedJson = preferences.getString(TASKS_KEY, "[]") ?: "[]"

        // Convert the JSON text back into a JSON array.
        val jsonArray = JSONArray(savedJson)

        // This list will hold the reconstructed Task objects.
        val tasks = mutableListOf<Task>()

        // Read each JSON object in the array.
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)

            // Extract the fields and rebuild the original Task.
            val name = jsonObject.getString("name")
            val isCompleted = jsonObject.getBoolean("isCompleted")

            tasks.add(
                Task(
                    name = name,
                    isCompleted = isCompleted
                )
            )
        }

        // Return the recovered tasks to whoever called this function.
        return tasks
    }

}

