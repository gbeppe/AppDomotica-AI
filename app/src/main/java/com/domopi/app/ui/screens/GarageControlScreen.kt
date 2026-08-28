package com.domopi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.MqttManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageControlScreen(mqttManager: MqttManager, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Controllo Garage") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                Icons.Default.Garage, 
                null, 
                modifier = Modifier.size(100.dp), 
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            
            Text(
                "Azionamento Cancelli",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Premi il pulsante per inviare l'impulso di apertura/chiusura",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // Pulsante 1: APRI 1ST BUTTON
            GarageTriggerButton(
                label = "APRI 1ST BUTTON",
                color = MaterialTheme.colorScheme.primary,
                onClick = { mqttManager.publish("zara/interface/garage/gate_1/cmd", "true") }
            )

            // Pulsante 2: APRI 2ND BUTTON
            GarageTriggerButton(
                label = "APRI 2ND BUTTON",
                color = Color(0xFF00E5FF),
                onClick = { mqttManager.publish("zara/interface/garage/gate_2/cmd", "true") }
            )
        }
    }
}

@Composable
fun GarageTriggerButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.OpenInFull, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}
