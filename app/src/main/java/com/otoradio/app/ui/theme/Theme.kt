package com.otoradio.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.otoradio.app.data.local.ThemeMode

// ---- Token warna hasil sampling screenshot Oto Music (lihat docs/02-spek-ui-oto.md) ----
private val OtoPink = Color(0xFFF8B0C2)
private val OtoPinkDeep = Color(0xFF855C8E)
private val OtoPinkContainer = Color(0xFFF5D9E2)
private val OtoDarkSurface = Color(0xFF1F1A1E)
private val OtoDarkContainer = Color(0xFF201A1A)
private val OtoDarkContainerHigh = Color(0xFF322527)
private val OtoOnSurface = Color(0xFFE6E1E5)
private val OtoOnSurfaceVariant = Color(0xFFCAC4C9)
private val OtoLightSurface = Color(0xFFFBF8F8)
private val OtoLightContainer = Color(0xFFF3EAF3)

val AmoledBlack = Color(0xFF000000)

private val DarkScheme = darkColorScheme(
    primary = OtoPink,
    onPrimary = Color(0xFF3A1D24),
    primaryContainer = OtoPinkContainer,
    onPrimaryContainer = Color(0xFF3A1D24),
    secondary = Color(0xFFD6C3CC),
    onSecondary = Color(0xFF2A2023),
    background = OtoDarkSurface,
    onBackground = OtoOnSurface,
    surface = OtoDarkSurface,
    onSurface = OtoOnSurface,
    surfaceVariant = OtoDarkContainer,
    onSurfaceVariant = OtoOnSurfaceVariant,
    surfaceContainer = OtoDarkContainer,
    surfaceContainerHigh = OtoDarkContainerHigh,
    surfaceContainerHighest = Color(0xFF3B2E31),
    outline = Color(0xFF5A4A50),
    error = Color(0xFFFFB4AB)
)

private val LightScheme = lightColorScheme(
    primary = OtoPinkDeep,
    onPrimary = Color.White,
    primaryContainer = OtoPinkContainer,
    onPrimaryContainer = Color(0xFF3A1D24),
    secondary = Color(0xFF74565F),
    background = OtoLightSurface,
    onBackground = Color(0xFF1F1A1E),
    surface = OtoLightSurface,
    onSurface = Color(0xFF1F1A1E),
    surfaceVariant = OtoLightContainer,
    onSurfaceVariant = Color(0xFF7A757A),
    surfaceContainer = OtoLightContainer,
    surfaceContainerHigh = Color(0xFFEDE4ED),
    surfaceContainerHighest = Color(0xFFE7DEE7),
    outline = Color(0xFF857378)
)

private val AmoledScheme = DarkScheme.copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C)
)

// ---- Tipografi: proporsi mengikuti screenshot Oto (docs 2.2) ----
private val OtoTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

private val OtoShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

@Composable
fun OtoRadioTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor
    val scheme = when {
        themeMode == ThemeMode.AMOLED -> AmoledScheme
        supportsDynamic && dark -> dynamicDarkColorScheme(context)
        supportsDynamic -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = OtoTypography,
        shapes = OtoShapes,
        content = content
    )
}
