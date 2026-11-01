package app.muro.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.muro.R
import app.muro.data.UserSettings
import app.muro.vpn.MuroVpnService
import app.muro.vpn.VpnStatus

@Composable
fun HomeScreen(
    vm: MuroViewModel,
    settings: UserSettings,
    status: VpnStatus,
    onToggle: (Boolean) -> Unit,
    onEditApps: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val blockedToday by vm.blockedToday.collectAsStateWithLifecycle()
    val blockedByApp by vm.blockedByApp.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val privateDns by vm.strictPrivateDns.collectAsStateWithLifecycle()
    val protectedApps = apps.filter { it.packageName in settings.selectedApps }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(),
    ) {
        item {
            Header(onOpenSettings)
            Spacer(Modifier.height(20.dp))
            HeroCard(status, settings, blockedToday, onToggle)
        }
        privateDns?.let { server ->
            item {
                Spacer(Modifier.height(12.dp))
                WarningCard(
                    "El DNS privado de Android está fijado en $server y las consultas se saltan a " +
                        "Muro. Ponlo en automático o apágalo en los ajustes de red del celular.",
                )
            }
        }
        item {
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Apps protegidas", style = MuroText.Section, color = MuroColors.Text)
                Text(
                    "Editar",
                    style = MuroText.Action.copy(fontSize = MuroText.Section.fontSize),
                    color = MuroColors.Accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onEditApps).padding(4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (protectedApps.isEmpty()) {
            item {
                Text(
                    "Todavía no eliges apps. Toca Editar para empezar.",
                    style = MuroText.Body,
                    color = MuroColors.Dim,
                )
            }
        }
        items(protectedApps, key = { it.packageName }) { app ->
            ProtectedAppRow(app, blockedByApp[app.packageName] ?: 0)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = MuroColors.Accent,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text("Muro", style = MuroText.Brand, color = MuroColors.Text, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MuroColors.Surface)
                .clickable(role = Role.Button, onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_settings),
                contentDescription = "Ajustes",
                tint = MuroColors.Text,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(status: VpnStatus, settings: UserSettings, blockedToday: Int, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val on = status !is VpnStatus.Off
    val active = status is VpnStatus.Active

    val (title, subtitle) = when (status) {
        is VpnStatus.Active -> "Protección activa" to MuroVpnService.filteringText(status.apps)
        VpnStatus.Starting -> "Iniciando" to "Preparando el filtro"
        VpnStatus.NoApps -> "Sin apps" to "Elige al menos una app"
        VpnStatus.Off -> "Protección apagada" to when (val count = settings.selectedApps.size) {
            0 -> "Elige apps para empezar"
            1 -> "1 app lista"
            else -> "$count apps listas"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MuroColors.Surface, shape)
            .border(BorderStroke(1.dp, MuroColors.Border), shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(if (active) MuroColors.IconTile else MuroColors.ToggleOff, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = if (active) MuroColors.Accent else MuroColors.Muted,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MuroText.HeroTitle, color = MuroColors.Text)
                Text(subtitle, style = MuroText.Body, color = MuroColors.Muted)
            }
            Spacer(Modifier.width(8.dp))
            MuroSwitch(checked = on, onChange = onToggle, large = true)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(formatCount(blockedToday), "anuncios bloqueados hoy", MuroColors.Accent, Modifier.weight(1f))
            StatTile(formatSaved(blockedToday), "datos ahorrados", MuroColors.Text, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Text("Datos estimados: 30 KB por anuncio", style = MuroText.Caption, color = MuroColors.Dim)
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MuroColors.Background, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(value, style = MuroText.Stat, color = color, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MuroText.Small, color = MuroColors.Muted)
    }
}

@Composable
private fun ProtectedAppRow(app: InstalledApp, blocked: Int) {
    RowCard {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, app.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MuroText.RowTitle, color = MuroColors.Text, maxLines = 1)
                Text("${formatCount(blocked)} bloqueados hoy", style = MuroText.Small, color = MuroColors.Muted)
            }
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MuroColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Margen de pantalla más el espacio de la barra de estado. */
@Composable
fun screenPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return PaddingValues(start = ScreenPadding, end = ScreenPadding, top = top + 16.dp, bottom = 16.dp)
}
