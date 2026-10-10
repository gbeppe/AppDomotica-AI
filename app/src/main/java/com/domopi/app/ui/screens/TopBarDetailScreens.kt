package com.domopi.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.domopi.app.BuildConfig
import com.domopi.app.data.AppAlignmentStatus
import com.domopi.app.data.BuildAlignment
import com.domopi.app.data.PublishedBuild
import com.domopi.app.network.AccessPathState
import com.domopi.app.network.AccessPathStatus
import com.domopi.app.network.HomeNetworkSettings
import com.domopi.app.ui.components.accessPathColor
import com.domopi.app.ui.components.indicatorToneColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun AccessPathDetailScreen(
    state: AccessPathState,
    homeNetwork: HomeNetworkSettings,
    onEditHomeNetwork: () -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "Percorso di accesso", onBack = onBack) {
        if (state.status == AccessPathStatus.UNKNOWN) {
            // Same three lines as WallBox L-1d, adapted to DomoPi services.
            Text("Percorso di rete non riconosciuto.", style = MaterialTheme.typography.titleMedium)
            Text("L'app potrebbe non raggiungere i servizi di casa.", style = MaterialTheme.typography.bodyMedium)
            Text("Verifica Wi-Fi o VPN.", style = MaterialTheme.typography.bodyMedium, color = accessPathColor(state.status))
        } else {
            BuildInfoRow("Stato", state.label, accessPathColor(state.status))
            val description = when (state.status) {
                AccessPathStatus.NON_IN_RETE -> "Lo smartphone non ha connettività IP utile. Connettersi a Wi-Fi o dati mobili."
                AccessPathStatus.LOCALE -> "Lo smartphone è sulla LAN di casa: Wi-Fi senza VPN con dominio ${homeNetwork.domain}."
                AccessPathStatus.REMOTO_CONNECTED -> "Fuori dalla LAN di casa, con una VPN che instrada ${homeNetwork.subnet} (subnet router Tailscale .20)."
                AccessPathStatus.REMOTO_DISCONNECTED -> "Fuori dalla LAN di casa, senza VPN verso ${homeNetwork.subnet}: i servizi di casa non sono raggiungibili."
                AccessPathStatus.PENDING -> "Classificazione della rete in corso."
                AccessPathStatus.UNKNOWN -> ""
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        BuildInfoRow("Dominio di casa in uso", homeNetwork.domain)
        BuildInfoRow("Subnet di casa in uso", homeNetwork.subnet)
        Text(
            "Indicatore informativo: l'app usa sempre gli indirizzi LAN; il percorso lo sceglie Android/Tailscale.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onEditHomeNetwork) { Text("Modifica rete di casa") }
    }
}

@Composable
fun BuildAlignmentScreen(
    status: AppAlignmentStatus,
    published: PublishedBuild,
    onOpenDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    DetailScaffold(title = "Build App", onBack = onBack) {
        Text("Allineamento", style = MaterialTheme.typography.titleMedium)
        Text(
            BuildAlignment.describe(status),
            style = MaterialTheme.typography.bodyLarge,
            color = indicatorToneColor(BuildAlignment.toneOf(status))
        )
        if (status == AppAlignmentStatus.BROKER_UNREACHABLE) {
            TextButton(onClick = onOpenDiagnostics) { Text("Apri diagnostica") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("APK installato", style = MaterialTheme.typography.titleMedium)
        BuildIdentityRows(
            version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            gitSha = BuildConfig.GIT_SHA,
            branch = BuildConfig.GIT_BRANCH,
            dirty = BuildConfig.BUILD_DIRTY,
            buildId = BuildConfig.BUILD_ID,
            buildTimestamp = BuildConfig.BUILD_TIMESTAMP,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Ultima release pubblicata", style = MaterialTheme.typography.titleMedium)
        when (published) {
            is PublishedBuild.Received -> {
                BuildInfoRow("Git SHA", published.identity.gitSha.take(12))
                BuildInfoRow("Working tree", published.identity.dirty)
                BuildInfoRow("Build ID", published.identity.buildId)
                BuildInfoRow("Data build", formatBuildTimestamp(published.identity.buildTimestamp))
            }
            PublishedBuild.Waiting -> Text("In attesa del broker…", style = MaterialTheme.typography.bodyMedium)
            PublishedBuild.Absent -> Text("Nessuna release pubblicata su ${BuildAlignment.TOPIC}.", style = MaterialTheme.typography.bodyMedium)
            PublishedBuild.Invalid -> Text("Identità pubblicata non valida.", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Verde = l'app installata è l'ultima release approvata (tools/release_android.py). " +
                "Le build di sviluppo risultano sempre non allineate.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BuildIdentityRows(version: String, gitSha: String, branch: String, dirty: String, buildId: String, buildTimestamp: String) {
    BuildInfoRow("Versione", version)
    BuildInfoRow("Git SHA", gitSha.take(12))
    BuildInfoRow("Branch", branch)
    BuildInfoRow("Working tree", dirty)
    BuildInfoRow("Build ID", buildId)
    BuildInfoRow("Data build", formatBuildTimestamp(buildTimestamp))
}

/** Release identities carry ISO-8601 UTC, development builds epoch milliseconds. */
fun formatBuildTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "N/D"
    return try {
        val instant = raw.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.parse(raw)
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(instant)
    } catch (_: Exception) {
        raw
    }
}
