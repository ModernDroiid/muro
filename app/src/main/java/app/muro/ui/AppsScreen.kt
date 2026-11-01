package app.muro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.muro.R
import app.muro.data.UserSettings

@Composable
fun AppsScreen(vm: MuroViewModel, settings: UserSettings) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val blockedByApp by vm.blockedByApp.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    // Las marcadas van primero, pero el orden se fija al entrar: si cambiara con cada
    // toque, la fila saltaría bajo el dedo.
    val selectedOnEntry = remember(apps.isEmpty()) { settings.selectedApps }
    val visible = remember(apps, query, selectedOnEntry) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedByDescending { it.packageName in selectedOnEntry }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                "Elige qué apps filtrar",
                "Solo el tráfico de las apps marcadas pasa por Muro. Las demás funcionan normal.",
            )
            Spacer(Modifier.height(16.dp))
            SearchField(query) { query = it }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val count = settings.selectedApps.size
                Text(
                    if (count == 1) "1 app filtrada" else "$count apps filtradas",
                    style = MuroText.Body,
                    color = MuroColors.Muted,
                )
                Text("Apps instaladas", style = MuroText.Small, color = MuroColors.Muted)
            }
        }
        // Leer los nombres de todas las apps tarda unos segundos al abrir por primera vez.
        if (apps.isEmpty()) {
            item { Text("Cargando apps…", style = MuroText.Body, color = MuroColors.Dim) }
        } else if (visible.isEmpty()) {
            item { Text("Ninguna app coincide con la búsqueda.", style = MuroText.Body, color = MuroColors.Dim) }
        }
        items(visible, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                selected = app.packageName in settings.selectedApps,
                blocked = blockedByApp[app.packageName] ?: 0,
                onChange = { vm.setAppSelected(app.packageName, it) },
            )
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, selected: Boolean, blocked: Int, onChange: (Boolean) -> Unit) {
    RowCard {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, app.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MuroText.RowTitle, color = MuroColors.Text, maxLines = 1)
                if (selected) {
                    val detail = if (blocked > 0) " · ${formatCount(blocked)} bloqueados hoy" else ""
                    Text("Filtrando$detail", style = MuroText.Small, color = MuroColors.Accent)
                } else {
                    Text("Sin filtrar", style = MuroText.Small, color = MuroColors.Dim)
                }
            }
            Spacer(Modifier.width(8.dp))
            MuroSwitch(checked = selected, onChange = onChange)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MuroColors.Surface, RowShape)
            .border(BorderStroke(1.dp, MuroColors.Border), RowShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MuroColors.Muted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Buscar app", style = MuroText.Body.copy(fontSize = 15.sp), color = MuroColors.Placeholder)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MuroText.Body.copy(fontSize = 15.sp, color = MuroColors.Text),
                cursorBrush = SolidColor(MuroColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
