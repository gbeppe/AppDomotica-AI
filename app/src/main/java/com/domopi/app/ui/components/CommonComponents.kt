package com.domopi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NumericStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
            }
            
            Text(
                text = value.toString(),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun formatLocalizedDate(isoDateStr: String, locale: Locale, pattern: String = "d MMMM yyyy"): String {
    val clean = isoDateStr.trim()
    if (clean.isEmpty() || clean.equals("N/D", ignoreCase = true) || clean.equals("Domani", ignoreCase = true)) {
        return clean.ifEmpty { "N/D" }
    }
    return try {
        val datePart = clean.take(10)
        val parsed = LocalDate.parse(datePart)
        val formatter = DateTimeFormatter.ofPattern(pattern, locale)
        parsed.format(formatter)
    } catch (_: Exception) {
        clean
    }
}
