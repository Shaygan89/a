package com.example.studenthelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.studenthelper.ui.StudentHelperApp
import com.example.studenthelper.ui.theme.StudentHelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            StudentHelperTheme {
                StudentHelperApp()
            }
        }
    }
}
