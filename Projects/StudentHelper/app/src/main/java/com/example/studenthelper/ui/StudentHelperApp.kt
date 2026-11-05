package com.example.studenthelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.studenthelper.data.StudyTask
import com.example.studenthelper.data.TaskStorage
import com.example.studenthelper.data.TaskType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

@Composable
fun StudentHelperApp() {
    val context = LocalContext.current
    val storage = remember(context) { TaskStorage(context.applicationContext) }

    // Stop collecting storage updates while the Activity is stopped. This avoids
    // keeping UI observers active while the app is in the background or closing.
    val tasks by storage.tasks.collectAsStateWithLifecycle(
        initialValue = emptyList(),
        minActiveState = Lifecycle.State.STARTED
    )

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var taskToRename by remember { mutableStateOf<StudyTask?>(null) }
    var taskToDelete by remember { mutableStateOf<StudyTask?>(null) }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }

    // Calculate today's date once per screen composition and share it with every row.
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val tomorrowEpochDay = remember(todayEpochDay) { todayEpochDay + 1L }

    // These values only need recalculation when stored tasks or the date changes,
    // not on every animation frame or unrelated UI recomposition.
    val tomorrowStats = remember(tasks, tomorrowEpochDay) {
        var total = 0
        var completed = 0
        tasks.forEach { task ->
            if (task.dueDateEpochDay == tomorrowEpochDay) {
                total++
                if (task.isCompleted) completed++
            }
        }
        total to completed
    }

    val visibleTasks = remember(tasks, selectedFilter) {
        val filtered = tasks.filter { task ->
            when (selectedFilter) {
                "HOMEWORK" -> task.type == TaskType.HOMEWORK
                "EXAM" -> task.type == TaskType.EXAM
                else -> true
            }
        }

        // Compare titles ignoring case without allocating a lowercase String
        // every time the sorting comparator is called.
        filtered.sortedWith { first, second ->
            val dateOrder = first.dueDateEpochDay.compareTo(second.dueDateEpochDay)
            if (dateOrder != 0) {
                dateOrder
            } else {
                val completionOrder = first.isCompleted.compareTo(second.isCompleted)
                if (completionOrder != 0) {
                    completionOrder
                } else {
                    first.title.compareTo(second.title, ignoreCase = true)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null
                    )
                },
                text = { Text("Add task") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 14.dp,
                end = 20.dp,
                bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "study_header", contentType = "header") {
                StudyHeader()
            }

            item(key = "tomorrow_progress", contentType = "progress") {
                TomorrowProgressCard(
                    total = tomorrowStats.first,
                    completed = tomorrowStats.second
                )
            }

            item(key = "task_filters", contentType = "filters") {
                TaskFilters(
                    selected = selectedFilter,
                    onSelected = { selectedFilter = it }
                )
            }

            item(key = "task_section_title", contentType = "section_title") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your tasks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = visibleTasks.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (visibleTasks.isEmpty()) {
                item(key = "empty_task_state", contentType = "empty_state") {
                    EmptyTaskState(selectedFilter)
                }
            } else {
                items(
                    items = visibleTasks,
                    key = { task -> task.id },
                    contentType = { "task_card" }
                ) { task ->
                    TaskCard(
                        task = task,
                        todayEpochDay = todayEpochDay,
                        modifier = Modifier.animateItem(),
                        onToggleCompleted = {
                            coroutineScope.launch {
                                storage.setCompleted(task.id, !task.isCompleted)
                            }
                        },
                        onRename = { taskToRename = task },
                        onDelete = { taskToDelete = task }
                    )
                }
            }

            item(key = "bottom_spacing", contentType = "spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddSheet) {
        AddTaskBottomSheet(
            onDismiss = { showAddSheet = false },
            onSave = { title, type, dueDateEpochDay ->
                val newTask = StudyTask(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    type = type,
                    dueDateEpochDay = dueDateEpochDay
                )
                coroutineScope.launch {
                    runCatching { storage.add(newTask) }
                        .onFailure {
                            snackbarHostState.showSnackbar("Couldn't save this task. Please try again.")
                        }
                }
                showAddSheet = false
            }
        )
    }

    taskToRename?.let { task ->
        RenameTaskDialog(
            task = task,
            onDismiss = { taskToRename = null },
            onSave = { newTitle ->
                coroutineScope.launch {
                    runCatching { storage.rename(task.id, newTitle) }
                        .onFailure {
                            snackbarHostState.showSnackbar("Couldn't rename this task. Please try again.")
                        }
                }
                taskToRename = null
            }
        )
    }

    taskToDelete?.let { task ->
        DeleteTaskDialog(
            task = task,
            onDismiss = { taskToDelete = null },
            onConfirm = {
                coroutineScope.launch {
                    runCatching { storage.delete(task.id) }
                        .onFailure {
                            snackbarHostState.showSnackbar("Couldn't delete this task. Please try again.")
                        }
                }
                taskToDelete = null
            }
        )
    }
}
