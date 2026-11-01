package app.muro.ui

import android.net.InetAddresses
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.muro.data.Domains
import app.muro.data.ListId
import app.muro.data.UserSettings

@Composable
fun ListsScreen(vm: MuroViewModel, settings: UserSettings) {
    val custom by vm.customDomains.collectAsStateWithLifecycle()
    val allowed by vm.allowedDomains.collectAsStateWithLifecycle()
    var showMyList by rememberSaveable { mutableStateOf(false) }
    var editingDns by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding()) {
        item {
            ScreenHeader("Listas y ajustes")
            Spacer(Modifier.height(14.dp))
            SectionLabel("Listas de bloqueo")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    title = "Publicidad",
                    subtitle = "Redes de anuncios conocidas",
                    checked = settings.adsList,
                    onChange = { vm.setListEnabled(ListId.ADS, it) },
                )
                ToggleRow(
                    title = "Rastreadores",
                    subtitle = "Analítica y seguimiento",
                    checked = settings.trackersList,
                    onChange = { vm.setListEnabled(ListId.TRACKERS, it) },
                )
                ToggleRow(
                    title = "Mi lista",
                    subtitle = myListSummary(custom.size, allowed.size),
                    checked = settings.customList,
                    onChange = { vm.setListEnabled(ListId.CUSTOM, it) },
                    onClick = { showMyList = true },
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Agregar dominio propio")
            Spacer(Modifier.height(8.dp))
            AddDomain(onAdd = vm::block)

            Spacer(Modifier.height(20.dp))
            SectionLabel("General")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    title = "Iniciar al encender el celular",
                    checked = settings.startOnBoot,
                    onChange = vm::setStartOnBoot,
                )
                RowCard(Modifier.clickable(role = Role.Button) { editingDns = true }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("DNS de respaldo", style = MuroText.RowTitle, color = MuroColors.Text)
                        Text(settings.backupDns, style = MuroText.Domain, color = MuroColors.Muted)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            WarningCard(
                "Los anuncios que vienen del mismo servidor que el contenido de la app no se " +
                    "pueden separar y seguirán apareciendo.",
            )
        }
    }

    if (showMyList) {
        MyListSheet(
            custom = custom,
            allowed = allowed,
            onRemoveCustom = vm::removeCustom,
            onRemoveAllowed = vm::removeAllowed,
            onDismiss = { showMyList = false },
        )
    }
    if (editingDns) {
        BackupDnsDialog(
            current = settings.backupDns,
            onSave = {
                vm.setBackupDns(it)
                editingDns = false
            },
            onDismiss = { editingDns = false },
        )
    }
}

private fun myListSummary(custom: Int, allowed: Int): String {
    val added = if (custom == 1) "1 dominio agregado por ti" else "$custom dominios agregados por ti"
    return when (allowed) {
        0 -> added
        1 -> "$added · 1 permitido"
        else -> "$added · $allowed permitidos"
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    RowCard(rowModifier) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MuroText.RowTitle, color = MuroColors.Text)
                if (subtitle != null) {
                    Text(subtitle, style = MuroText.Small, color = MuroColors.Muted)
                }
            }
            Spacer(Modifier.width(8.dp))
            MuroSwitch(checked = checked, onChange = onChange)
        }
    }
}

@Composable
private fun AddDomain(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        val domain = Domains.normalize(text)
        invalid = domain == null
        if (domain != null) {
            onAdd(domain)
            text = ""
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DomainField(
            value = text,
            placeholder = "ej: ads.ejemplo.com",
            onChange = {
                text = it
                invalid = false
            },
            onDone = ::submit,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .width(95.dp)
                .height(48.dp)
                .clip(RowShape)
                .background(MuroColors.Accent)
                .clickable(role = Role.Button, onClick = ::submit),
            contentAlignment = Alignment.Center,
        ) {
            Text("Agregar", style = MuroText.Section, color = MuroColors.Background)
        }
    }
    if (invalid) {
        Spacer(Modifier.height(6.dp))
        Text("Escribe un dominio válido, como ads.ejemplo.com", style = MuroText.Small, color = MuroColors.Danger)
    }
}

@Composable
private fun DomainField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Uri,
) {
    Box(
        modifier
            .height(48.dp)
            .background(MuroColors.Surface, RowShape)
            .border(BorderStroke(1.dp, MuroColors.Border), RowShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = MuroText.Domain, color = MuroColors.Placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MuroText.Domain.copy(color = MuroColors.Text),
            cursorBrush = SolidColor(MuroColors.Accent),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyListSheet(
    custom: List<String>,
    allowed: List<String>,
    onRemoveCustom: (String) -> Unit,
    onRemoveAllowed: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MuroColors.NavBackground) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ScreenHeader("Mi lista")
                Spacer(Modifier.height(8.dp))
                SectionLabel("Bloqueados por ti")
            }
            if (custom.isEmpty()) item { EmptyNote("Agrega dominios desde Listas o desde el registro.") }
            items(custom, key = { "b:$it" }) { DomainRow(it) { onRemoveCustom(it) } }

            item {
                Spacer(Modifier.height(8.dp))
                SectionLabel("Permitidos por ti")
            }
            if (allowed.isEmpty()) item { EmptyNote("Los dominios que permitas desde el registro aparecen aquí.") }
            items(allowed, key = { "a:$it" }) { DomainRow(it) { onRemoveAllowed(it) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MuroText.Body, color = MuroColors.Dim)
}

@Composable
private fun DomainRow(domain: String, onRemove: () -> Unit) {
    RowCard {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                domain,
                style = MuroText.Domain,
                color = MuroColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) {
                Text("Quitar", style = MuroText.Action, color = MuroColors.Danger)
            }
        }
    }
}

@Composable
private fun BackupDnsDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val trimmed = text.trim()
    val valid = InetAddresses.isNumericAddress(trimmed)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MuroColors.Surface,
        title = { Text("DNS de respaldo", style = MuroText.HeroTitle) },
        text = {
            Column {
                Text(
                    "Se usa solo si AdGuard no responde. Va sin cifrar y sin filtro de AdGuard.",
                    style = MuroText.Body,
                    color = MuroColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                DomainField(
                    value = text,
                    placeholder = "1.1.1.1",
                    onChange = { text = it },
                    onDone = { if (valid) onSave(trimmed) },
                    keyboardType = KeyboardType.Decimal,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = valid) {
                Text("Guardar", style = MuroText.Action, color = if (valid) MuroColors.Accent else MuroColors.Dim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", style = MuroText.Action, color = MuroColors.Muted)
            }
        },
    )
}
