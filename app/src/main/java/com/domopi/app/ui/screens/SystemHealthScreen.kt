package com.domopi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domopi.app.data.HealthLight
import com.domopi.app.data.HostHealth
import com.domopi.app.data.PhoneProbe
import com.domopi.app.data.SystemHealth
import com.domopi.app.data.ZaiConnectivityManager
import com.domopi.app.ui.theme.SolarGreen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun healthColor(light: HealthLight): Color = when (light) {
    HealthLight.GREEN -> SolarGreen
    HealthLight.RED -> Color(0xFFE53935)
    HealthLight.GREY -> Color.Gray
}

/**
 * Phase S3 of docs/AI_MODE/SALUTE_IMPIANTO.md: the two health summaries of .20 and .15 (backend/health)
 * and, on the home Wi-Fi, a few TCP probes from the phone (user's decisions of 23/10/2026: heart icon in
 * the top bar, probes when the page opens plus "Ricontrolla").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthScreen(
    hosts: Map<String, HostHealth>,
    connected: Boolean,
    atHome: Boolean,
    connectivityManager: ZaiConnectivityManager,
    onBack: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    var probes by remember { mutableStateOf<Map<PhoneProbe, Boolean>>(emptyMap()) }
    var probing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun probe() {
        if (!atHome || probing) return
        probing = true
        scope.launch {
            probes = SystemHealth.PROBES.map { p -> async { p to connectivityManager.checkServiceReachable(p.ip, p.port) } }
                .awaitAll().toMap()
            probing = false
        }
    }
    LaunchedEffect(atHome) { if (atHome) probe() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Salute impianto") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") } },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (!connected) item {
                Text(
                    if (hosts.isEmpty()) "Casa non collegata: nessuno stato ricevuto. Controlla la rete o Tailscale."
                    else "Casa non collegata: sono gli ultimi stati ricevuti.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SystemHealth.HOSTS.forEach { host -> item { HostCard(host, hosts[host], now) } }
            item { PhoneCard(atHome, probing, probes, SystemHealth.phoneAdvice(probes, hosts, now), ::probe) }
            item {
                Text(
                    "Controlli ogni 2 minuti su ciascun Raspberry; gli avvisi arrivano su ntfy (domopi-casa). " +
                        "Grigio = nessun aggiornamento da più di 6 minuti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Dot(light: HealthLight, size: Int = 14) =
    Box(Modifier.size(size.dp).background(healthColor(light), CircleShape))

@Composable
private fun HostCard(host: String, health: HostHealth?, now: Long) {
    val light = SystemHealth.light(health, now)
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(light)
                Spacer(Modifier.width(10.dp))
                Text("Raspberry $host", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(health?.let { "controllato " + SystemHealth.age(it.checkedAtMs, now) } ?: "nessun dato",
                    style = MaterialTheme.typography.bodySmall)
            }
            when {
                health == null -> Text("Nessun riepilogo ricevuto dal controllo di $host.")
                light == HealthLight.GREY -> Text("Nessun aggiornamento da più di 6 minuti: $host o il suo controllo non funziona.",
                    color = MaterialTheme.colorScheme.error)
            }
            val faults = health?.checks?.filter { !it.ok }.orEmpty()
            if (health != null && faults.isEmpty() && light != HealthLight.GREY) Text("Tutto a posto (${health.checks.size} controlli).")
            faults.forEach { c ->
                Column {
                    Text(c.title, fontWeight = FontWeight.SemiBold, color = healthColor(HealthLight.RED))
                    Text(c.detail, style = MaterialTheme.typography.bodyMedium)
                    if (c.advice.isNotBlank()) Text("Cosa fare: ${c.advice}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (health != null) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Nascondi i controlli" else "Tutti i controlli") }
                if (expanded) health.checks.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (c.ok) HealthLight.GREEN else HealthLight.RED, 8)
                        Spacer(Modifier.width(8.dp))
                        Text("${c.title}: ${c.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneCard(atHome: Boolean, probing: Boolean, probes: Map<PhoneProbe, Boolean>, advice: String?, onProbe: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dal Wi-Fi del telefono", fontWeight = FontWeight.Bold)
            if (!atHome) {
                Text("Disponibile solo in casa (LOCALE): fuori casa il telefono passa da Tailscale.",
                    style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            SystemHealth.PROBES.forEach { p ->
                val ok = probes[p]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(when (ok) { true -> HealthLight.GREEN; false -> HealthLight.RED; null -> HealthLight.GREY }, 10)
                    Spacer(Modifier.width(8.dp))
                    Text("${p.label} (${p.ip}:${p.port})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(when (ok) { true -> "raggiungibile"; false -> "non risponde"; null -> "—" }, style = MaterialTheme.typography.bodySmall)
                }
            }
            advice?.let { Text(it, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onProbe, enabled = !probing) { Text(if (probing) "Controllo in corso…" else "Ricontrolla") }
        }
    }
}
