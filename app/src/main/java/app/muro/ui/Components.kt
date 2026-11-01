package app.muro.ui

import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.muro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

val RowShape = RoundedCornerShape(16.dp)
val ScreenPadding = 20.dp

/** Interruptor del mockup: más ancho y plano que el de Material. */
@Composable
fun MuroSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val width = if (large) 64.dp else 48.dp
    val height = if (large) 36.dp else 28.dp
    val knob = if (large) 28.dp else 22.dp
    val gap = (height - knob) / 2

    val knobOffset by animateDpAsState(if (checked) width - knob - gap else gap, label = "knob")
    val track by animateColorAsState(if (checked) MuroColors.Toggle else MuroColors.ToggleOff, label = "track")

    Box(
        modifier
            .size(width, height)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = knobOffset).size(knob).background(Color.White, CircleShape))
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Text(title, style = MuroText.ScreenTitle, color = MuroColors.Text)
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MuroText.Body, color = MuroColors.Muted)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MuroText.Label, color = MuroColors.Muted, modifier = modifier)
}

/** Fila con el fondo y las esquinas comunes a todas las listas. */
@Composable
fun RowCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RowShape)
            .background(MuroColors.Surface),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
fun WarningCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MuroColors.WarningBackground, RowShape)
            .border(BorderStroke(1.dp, MuroColors.WarningBorder), RowShape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = MuroColors.Amber,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MuroText.Small.copy(fontSize = 13.5.sp, lineHeight = 18.sp), color = MuroColors.WarningText)
    }
}

private val iconCache = LruCache<String, ImageBitmap>(128)

/** Icono real de la app; las apps sin icono usan el cuadro con letra del mockup. */
@Composable
fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.packageManager.getApplicationIcon(packageName).toBitmap(120, 120).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(packageName, it) }
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val loaded = icon
    if (loaded != null) {
        Image(loaded, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        val tile = MuroColors.Tiles[packageName.hashCode().absoluteValue % MuroColors.Tiles.size]
        Box(modifier.size(size).background(tile, shape), contentAlignment = Alignment.Center) {
            Text(
                label.take(1).uppercase(),
                color = MuroColors.Background,
                fontFamily = InterFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    HOME("Inicio", R.drawable.ic_shield),
    APPS("Apps", R.drawable.ic_apps),
    LOG("Registro", R.drawable.ic_log),
    LISTS("Listas", R.drawable.ic_lists),
}

@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MuroColors.NavBackground).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MuroColors.NavBorder))
        Row(Modifier.fillMaxWidth().height(79.dp)) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val tint = if (selected) MuroColors.Accent else MuroColors.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .toggleable(value = selected, role = Role.Tab, onValueChange = { onSelect(tab) })
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tab.label,
                        color = tint,
                        fontFamily = InterFont,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
