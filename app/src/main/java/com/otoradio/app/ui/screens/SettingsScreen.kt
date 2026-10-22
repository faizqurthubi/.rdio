package com.otoradio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.otoradio.app.data.local.AppSettings
import com.otoradio.app.data.local.EqScope
import com.otoradio.app.data.local.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onTheme: (ThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
    onIcy: (Boolean) -> Unit,
    onReconnect: (Boolean) -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    onSleepDefault: (Int) -> Unit,
    onEqScope: (EqScope) -> Unit,
    onOpenEq: () -> Unit,
    stationCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
        )

        GroupTitle("TEMA & WARNA")
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onTheme(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Terang"
                                ThemeMode.DARK -> "Gelap"
                                ThemeMode.AMOLED -> "AMOLED"
                            }
                        )
                    }
                )
            }
        }
        SettingSwitch(
            title = "Dynamic color",
            checked = settings.dynamicColor,
            onChange = onDynamic
        )

        GroupTitle("PEMUTAR")
        SettingSwitch(
            title = "Judul lagu (ICY)",
            checked = settings.useIcyTitle,
            onChange = onIcy
        )
        SettingSwitch(
            title = "Sambung ulang otomatis",
            checked = settings.autoReconnect,
            onChange = onReconnect
        )
        SettingSwitch(
            title = "Putar stasiun terakhir",
            checked = settings.autoPlayLastStation,
            onChange = onAutoPlay
        )

        GroupTitle("AUDIO")
        SettingRow(
            title = "Equalizer",
            onClick = onOpenEq
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Sleep timer default: ${settings.sleepTimerDefaultMin} menit",
                style = MaterialTheme.typography.titleMedium)
            var v by remember(settings.sleepTimerDefaultMin) {
                mutableFloatStateOf(settings.sleepTimerDefaultMin.toFloat())
            }
            Slider(
                value = v,
                onValueChange = { v = it },
                onValueChangeFinished = { onSleepDefault(v.toInt()) },
                valueRange = 5f..120f,
                steps = 22
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Cakupan EQ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.eqScope == EqScope.GLOBAL,
                    onClick = { onEqScope(EqScope.GLOBAL) },
                    label = { Text("Global") }
                )
                FilterChip(
                    selected = settings.eqScope == EqScope.PER_STATION,
                    onClick = { onEqScope(EqScope.PER_STATION) },
                    label = { Text("Per stasiun") }
                )
            }
        }

        GroupTitle("TENTANG")
        SettingRow(
            title = "Oto Radio 1.0",
            subtitle = "$stationCount stasiun",
            onClick = null
        )
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)?) {
    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}
