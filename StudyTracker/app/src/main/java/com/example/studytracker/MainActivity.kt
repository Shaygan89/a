package com.example.studytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.studytracker.ui.theme.StudyTrackerTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyTrackerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                    Greeting(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    var homeworks by remember {             //'remember' means Keep this value between recompositions.
        mutableStateOf(                     //This value can change, and Compose should know when it changes.
        listOf(
            Homework(1, "Math", false),
            Homework(2, "Science", true),
            Homework(3, "Physics", false)
        )
        )
    }
    val completed = homeworks.count{it.isCompleted}
    val total = homeworks.size
    val progress = if (total == 0) {
        0f
    } else {
        completed.toFloat() / total
    }

    Column(modifier = Modifier.statusBarsPadding()) {

        ProgressBarSystem(progress)

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.padding(all = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text( modifier = Modifier.padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.onSecondary,
                text = "You have $total homeworks and have done $completed of them",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        homeworks.forEach { homework ->
            HomeworkItems(
                homework = homework,
                onCheckedChange = { newValue ->
                    homeworks = homeworks.map { item ->
                        if (item.id == homework.id) {
                            item.copy(isCompleted = newValue)
                        } else {
                            item
                        }
                    }
                }
            )
        }

    }

}

@Composable
fun HomeworkItems(homework: Homework, onCheckedChange: (Boolean) -> Unit) {
    Row {
        Text(text = homework.title)

        Checkbox(
            checked = homework.isCompleted,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ProgressBarSystem(Value: Float) {
    Card(
        modifier = Modifier.statusBarsPadding()
            .padding(horizontal = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(all = 10.dp)) {
            Text(
                text = "Today's progress",
                color = MaterialTheme.colorScheme.onPrimary
            )

            LinearProgressIndicator(
                progress = { Value },
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun TasksUI() {
}

data class Homework(
    var id: Int,
    var title: String,
    var isCompleted: Boolean
)

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StudyTrackerTheme {
        Greeting()
    }
}