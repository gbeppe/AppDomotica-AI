package com.domopi.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.NodeStatus
import com.domopi.app.data.StackHealthState
import com.domopi.app.data.StackNode
import com.domopi.app.data.StackOverallStatus
import com.domopi.app.data.TrafficLogEntry

val StatusGreen = Color(0xFF4CAF50)
val StatusOrange = Color(0xFFFF9800)
val StatusRed = Color(0xFFF44336)
val StatusGray = Color(0xFF9E9E9E)

fun statusColor(status: NodeStatus): Color = when (status) {
    NodeStatus.ONLINE -> StatusGreen
    NodeStatus.DEGRADED -> StatusOrange
    NodeStatus.OFFLINE -> StatusRed
    NodeStatus.CHECKING -> StatusGray
    NodeStatus.UNKNOWN -> StatusGray
}

fun overallColor(status: StackOverallStatus): Color = when (status) {
    StackOverallStatus.ONLINE -> StatusGreen
    StackOverallStatus.DEGRADED -> StatusOrange
    StackOverallStatus.OFFLINE -> StatusRed
    StackOverallStatus.CHECKING -> StatusGray
    StackOverallStatus.UNKNOWN -> StatusGray
}

@Composable
fun StackStatusIcon(
    overallStatus: StackOverallStatus,
    isChecking: Boolean = false,
    onClick: () -> Unit
) {
    val color = overallColor(overallStatus)
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag("stack-status-icon")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (overallStatus == StackOverallStatus.OFFLINE) Icons.Default.LinkOff else Icons.Default.Link,
                contentDescription = "Stato Stack HW/SW",
                tint = MaterialTheme.colorScheme.onSurface
            )
            // Status dot indicator overlay in top right
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackStatusBottomSheet(
    healthState: StackHealthState,
    trafficLogs: List<TrafficLogEntry>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("stack-status-sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Stato Stack & Traffico",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Diagnostica connettività e messaggi per AI Smart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        val onlineCount = healthState.nodeStates.values.count { it.status == NodeStatus.ONLINE }
                        val totalCount = StackNode.entries.size
                        Text("Stato Stack ($onlineCount/$totalCount)")
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Log Traffico (${trafficLogs.size})") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> StackNodesTabContent(healthState, onRefresh)
                1 -> TrafficLogTabContent(trafficLogs, onClearLogs, context)
            }
        }
    }
}

@Composable
private fun StackNodesTabContent(
    healthState: StackHealthState,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Summary Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = overallColor(healthState.overallStatus).copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(overallColor(healthState.overallStatus))
                    )
                    Column {
                        Text(
                            text = when (healthState.overallStatus) {
                                StackOverallStatus.ONLINE -> "Stack Completamente Operativo"
                                StackOverallStatus.DEGRADED -> "Degrado Parziale dello Stack"
                                StackOverallStatus.OFFLINE -> "Stack Non Raggiungibile"
                                StackOverallStatus.CHECKING -> "Verifica in corso..."
                                StackOverallStatus.UNKNOWN -> "Stato non ancora verificato"
                            },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (healthState.lastOverallCheckTime.isNotBlank()) {
                            Text(
                                text = "Ultimo controllo: ${healthState.lastOverallCheckTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onRefresh, enabled = !healthState.isChecking) {
                    if (healthState.isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna ora")
                    }
                }
            }
        }

        // List of Stack Nodes
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 360.dp)
        ) {
            items(StackNode.entries.toTypedArray()) { node ->
                val nodeState = healthState.nodeStates[node]
                val status = nodeState?.status ?: NodeStatus.CHECKING

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("node-card-${node.name}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor(status))
                            )
                            Column {
                                Text(
                                    text = node.displayName,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                text = nodeState?.detail ?: "Non ancora verificato",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = when (status) {
                                    NodeStatus.ONLINE -> "ONLINE"
                                    NodeStatus.DEGRADED -> "DEGRADATO"
                                    NodeStatus.OFFLINE -> "OFFLINE"
                                    NodeStatus.CHECKING -> "CHECK..."
                                    NodeStatus.UNKNOWN -> "SCONOSCIUTO"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = statusColor(status)
                            )
                            nodeState?.latencyMs?.let { latency ->
                                Text(
                                    text = "${latency} ms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficLogTabContent(
    logs: List<TrafficLogEntry>,
    onClearLogs: () -> Unit,
    context: Context
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    if (logs.isEmpty()) {
                        Toast.makeText(context, "Nessun log da copiare", Toast.LENGTH_SHORT).show()
                    } else {
                        val text = logs.joinToString("\n") { log ->
                            "[${log.timestamp}] [${log.tag}] ${log.endpoint} -> ${log.statusCode ?: "-"} (${log.latencyMs ?: "-"}ms): ${log.details}"
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DomoPi Traffic Log", text))
                        Toast.makeText(context, "Log copiato negli appunti", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.testTag("copy-log-btn")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copia Log")
            }

            TextButton(
                onClick = onClearLogs,
                modifier = Modifier.testTag("clear-log-btn")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pulisci")
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nessun evento registrato per questa sessione.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 340.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    var expanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        colors = CardDefaults.cardColors(
                            containerColor = if (entry.isError)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = if (entry.isError) StatusRed else StatusGreen,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = entry.tag,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = entry.timestamp,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = buildString {
                                        entry.statusCode?.let { append("HTTP $it ") }
                                        entry.latencyMs?.let { append("(${it}ms)") }
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (entry.isError) StatusRed else StatusGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = entry.endpoint,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )

                            if (entry.details.isNotBlank()) {
                                Text(
                                    text = entry.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (expanded) 10 else 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
