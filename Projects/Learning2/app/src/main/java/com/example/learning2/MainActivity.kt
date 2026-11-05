package com.example.learning2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.learning2.ui.theme.Learning2Theme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Learning2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

data class Task(
    val name: String,
    val isCompleted: Boolean = false
)

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    var count by remember { mutableIntStateOf(0) }

    var taskName by remember { mutableStateOf("") }

    //val tasks = remember { mutableStateListOf<Task>() }

    // Get access to Android's app context so we can read saved data.
    val context = LocalContext.current

    // Create the observable list used by our Compose UI.
    val tasks = remember { mutableStateListOf<Task>() }

    // Load saved tasks once when this screen enters composition.
    LaunchedEffect(Unit) {
        tasks.addAll(TaskStorage.loadTasks(context))
    }

    val completedTasks = tasks.count { it.isCompleted }

    val totalTasks = tasks.size

    val progress = if (totalTasks == 0) {
        0f
    } else {
        completedTasks.toFloat() / totalTasks
    }



    Column(modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("My Tasks",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF283593)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE8EAF6))
                .padding(16.dp)
        ) {
            Column() {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Completed Tasks")
                    Text(" $completedTasks / ${tasks.size}")
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text("Things i want to do it")

        TextField(
            value = taskName,
            onValueChange = { taskName = it },
            label = { Text("Task name") }
        )

        for (task in tasks) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = { checked ->
                        val index = tasks.indexOf(task)
                        if (index != -1) {
                            tasks[index] = task.copy(isCompleted = checked)
                        }
                    }
                )

                Text(text = task.name)

                Button(
                    onClick = {
                        tasks.remove(task)
                    }
                ) {
                    Text("Delete")
                }
            }
        }

        Button(onClick = {
            if (taskName.isNotBlank()) {
                tasks.add(Task(name = taskName.trim()))
            taskName = ""
        }
                         },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF283593)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Task")
        }

        Button(
            onClick = {
                count = 0
            }
        ) {
            Text("Reset")
        }
        }
}