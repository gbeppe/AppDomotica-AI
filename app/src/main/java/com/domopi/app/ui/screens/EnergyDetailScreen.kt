package com.domopi.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domopi.app.data.EnergyHistory
import com.domopi.app.data.EnergyRepository
import com.domopi.app.data.HistoryPoint
import com.domopi.app.ui.theme.ConsumptionYellow
import com.domopi.app.ui.theme.GridBlue
import com.domopi.app.ui.theme.SolarGreen

val BatteryMagenta = Color(0xFFE040FB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyDetailScreen(emoncmsIp: String, onBack: () -> Unit) {
    var history by remember { mutableStateOf<EnergyHistory?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedHours by remember { mutableIntStateOf(24) }

    LaunchedEffect(selectedHours, emoncmsIp) {
        if (emoncmsIp.isNotEmpty()) {
            val repository = EnergyRepository(emoncmsIp)
            isLoading = true
            history = repository.fetchHistory(selectedHours)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storico Energia") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                SegmentedButton(
                    selected = selectedHours == 6,
                    onClick = { selectedHours = 6 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Ultime 6 ore") }
                SegmentedButton(
                    selected = selectedHours == 24,
                    onClick = { selectedHours = 24 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Ultime 24 ore") }
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (history == null || history!!.solar.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Dati non disponibili.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        Text("Andamento Potenze (W)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        EnergyHistoryChart(history!!)
                    }
                    
                    item {
                        Text("Andamento Carica (%)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        SocHistoryChart(history!!.soc)
                    }
                    
                    item {
                        Legend()
                    }
                    
                    item {
                        SummaryStats(history!!)
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EnergyHistoryChart(history: EnergyHistory) {
    val density = LocalDensity.current
    val labelSize = 10.sp
    val labelPx = with(density) { labelSize.toPx() }

    Card(
        modifier = Modifier.fillMaxWidth().height(350.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp, horizontal = 4.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxVal = 7000f 
                val chartLeftPadding = 50.dp.toPx()
                val chartWidth = size.width - chartLeftPadding
                val midY = size.height / 2
                
                // Horizontal grid lines and Labels
                val gridLines = listOf(-4000, -2000, 0, 2000, 4000, 6000)
                gridLines.forEach { valW ->
                    val y = midY - (valW.toFloat() / maxVal) * midY
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(chartLeftPadding, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Y Axis Label
                    drawContext.canvas.nativeCanvas.drawText(
                        "${valW}W",
                        5.dp.toPx(),
                        y + labelPx / 3,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = labelPx
                            textAlign = android.graphics.Paint.Align.LEFT
                        }
                    )
                }

                // Baseline 0
                drawLine(Color.Gray.copy(alpha = 0.5f), Offset(chartLeftPadding, midY), Offset(size.width, midY), 1.dp.toPx())

                // Adjust draw scope for the chart area
                val chartAreaWidth = chartWidth
                
                drawHistoryLine(history.solar, SolarGreen, maxVal, offset = chartLeftPadding, width = chartAreaWidth, filled = true)
                drawHistoryLine(history.consumption, ConsumptionYellow, maxVal, offset = chartLeftPadding, width = chartAreaWidth, filled = true)
                
                drawHistoryLine(history.grid, GridBlue, maxVal, offset = chartLeftPadding, width = chartAreaWidth)
                drawHistoryLine(history.battery, BatteryMagenta, maxVal, offset = chartLeftPadding, width = chartAreaWidth)
            }
        }
    }
}

@Composable
fun SocHistoryChart(socData: List<HistoryPoint>) {
    val density = LocalDensity.current
    val labelPx = with(density) { 10.sp.toPx() }
    
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 4.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val chartLeftPadding = 50.dp.toPx()
                val chartAreaWidth = size.width - chartLeftPadding
                
                listOf(0, 50, 100).forEach { soc ->
                    val y = size.height - (soc / 100f) * size.height
                    drawLine(Color.White.copy(alpha = 0.1f), Offset(chartLeftPadding, y), Offset(size.width, y), 1.dp.toPx())
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        "$soc%",
                        5.dp.toPx(),
                        y + labelPx / 3,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = labelPx
                        }
                    )
                }
                drawHistoryLine(socData, SolarGreen, 100f, isSoc = true, offset = chartLeftPadding, width = chartAreaWidth, filled = true, thickness = 3f)
            }
        }
    }
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHistoryLine(
    data: List<HistoryPoint>,
    color: Color,
    maxVal: Float,
    isSoc: Boolean = false,
    filled: Boolean = false,
    thickness: Float = 2f,
    offset: Float = 0f,
    width: Float = size.width
) {
    if (data.isEmpty()) return
    
    val height = size.height
    val midY = if (isSoc) height else height / 2
    
    val startTime = data.first().timestamp
    val endTime = data.last().timestamp
    val timeRange = (endTime - startTime).coerceAtLeast(1)

    val path = Path()
    data.forEachIndexed { index, point ->
        val x = offset + ((point.timestamp - startTime).toFloat() / timeRange) * width
        val y = if (isSoc) {
            height - (point.value.coerceIn(0f, 100f) / 100f) * height
        } else {
            midY - (point.value.coerceIn(-maxVal, maxVal) / maxVal) * midY
        }
        
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    
    if (filled) {
        val fillPath = Path().apply {
            addPath(path)
            val baseline = if (isSoc) height else midY
            lineTo(offset + width, baseline)
            lineTo(offset, baseline)
            close()
        }
        drawPath(fillPath, color.copy(alpha = 0.2f), style = Fill)
    }
    
    drawPath(path, color, style = Stroke(width = thickness.dp.toPx()))
}

@Composable
fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem("Produzione", SolarGreen)
            LegendItem("Consumo", ConsumptionYellow)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem("Rete (Imp/Exp)", GridBlue)
            LegendItem("Powerwall (Ch/Dis)", BatteryMagenta)
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SummaryStats(history: EnergyHistory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("INDICATORI CHIAVE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            val lastSoc = history.soc.lastOrNull { it.value > 0 }?.value ?: history.soc.lastOrNull()?.value ?: 0f
            DetailStat("Livello Batteria", "${lastSoc.toInt()}%")
            
            val peakSolar = history.solar.map { it.value }.maxOrNull() ?: 0f
            DetailStat("Picco Solare", "${peakSolar.toInt()} W")
            
            val avgCons = history.consumption.map { it.value }.average().takeIf { !it.isNaN() } ?: 0.0
            DetailStat("Consumo Medio", "${avgCons.toInt()} W")
        }
    }
}

@Composable
fun DetailStat(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
