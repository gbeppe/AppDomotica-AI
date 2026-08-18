package com.domopi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.domopi.app.ui.theme.DomoPiTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.domopi.app.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DomoPiTheme {
                var currentScreen by remember { mutableStateOf("dashboard") }
                
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, null) },
                                label = { Text("Dashboard") },
                                selected = currentScreen == "dashboard",
                                onClick = { currentScreen = "dashboard" }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Build, null) },
                                label = { Text("Tecnico") },
                                selected = currentScreen == "technical",
                                onClick = { currentScreen = "technical" }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, null) },
                                label = { Text("Impostazioni") },
                                selected = currentScreen == "settings",
                                onClick = { currentScreen = "settings" }
                            )
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        when (currentScreen) {
                            "dashboard" -> DashboardScreen()
                            "technical" -> TechnicalMenuScreen()
                            "settings" -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Welcome to $name!",
        modifier = modifier
    )
}
