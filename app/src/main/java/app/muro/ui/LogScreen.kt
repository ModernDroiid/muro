package app.muro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.muro.data.LogEntry

@Composable
fun LogScreen(vm: MuroViewModel) {
    val entries by vm.logEntries.collectAsStateWithLifecycle()
    val filter by vm.logFilter.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<LogEntry?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                "Registro en vivo",
                "Si una app deja de funcionar, toca el dominio bloqueado y permítelo.",
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { option ->
                    FilterChip(option.label, selected = option == filter) { vm.logFilter.value = option }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "Todavía no hay consultas. Enciende la protección y abre una app filtrada.",
                    style = MuroText.Body,
                    color = MuroColors.Dim,
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            LogRow(entry) { pending = entry }
        }
    }

    pending?.let { entry ->
        DomainActionDialog(
            entry = entry,
            onConfirm = {
                if (entry.blocked) vm.allow(entry.domain) else vm.block(entry.domain)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) MuroColors.Text else MuroColors.Surface)
            .border(BorderStroke(1.dp, if (selected) MuroColors.Text else MuroColors.ChipBorder), shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MuroText.Action, color = if (selected) MuroColors.Background else MuroColors.Text)
    }
}

@Composable
private fun LogRow(entry: LogEntry, onClick: () -> Unit) {
    val color = if (entry.blocked) MuroColors.Danger else MuroColors.Accent
    RowCard(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.domain,
                    style = MuroText.Domain,
                    color = MuroColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entry.appName ?: "App filtrada"} · ${formatTime(entry.timestamp)}",
                    style = MuroText.Small,
                    color = MuroColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(if (entry.blocked) "Bloqueado" else "Permitido", style = MuroText.Action.copy(fontWeight = MuroText.RowTitle.fontWeight), color = color)
        }
    }
}

@Composable
private fun DomainActionDialog(entry: LogEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MuroColors.Surface,
        title = {
            Text(
                if (entry.blocked) "¿Permitir este dominio?" else "¿Bloquear este dominio?",
                style = MuroText.HeroTitle,
            )
        },
        text = {
            Column {
                Text(entry.domain, style = MuroText.Domain, color = MuroColors.Text)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (entry.blocked) {
                        "Dejará de bloquearse en todas las apps filtradas, también en el filtro de AdGuard."
                    } else {
                        "Se agregará a Mi lista y se bloqueará en todas las apps filtradas."
                    },
                    style = MuroText.Body,
                    color = MuroColors.Muted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (entry.blocked) "Permitir" else "Bloquear", style = MuroText.Action, color = MuroColors.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", style = MuroText.Action, color = MuroColors.Muted)
            }
        },
    )
}
