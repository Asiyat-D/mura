package kz.mura.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kz.mura.domain.ThemeMode

val Blue = Color(0xFF2858E8)
val Navy = Color(0xFF101E3D)
val Cyan = Color(0xFF8ACDFF)
private val LightColors = lightColorScheme(primary = Blue, onPrimary = Color.White, primaryContainer = Color(0xFFE9EFFF), onPrimaryContainer = Blue, secondary = Color(0xFF5486BD), background = Color(0xFFF5F7FB), surface = Color.White, surfaceVariant = Color(0xFFEDF1F8), onBackground = Navy, onSurface = Navy, onSurfaceVariant = Color(0xFF77839A), outline = Color(0xFFDDE3EE), error = Color(0xFFB64050))
private val DarkColors = darkColorScheme(primary = Color(0xFF9DB8FF), onPrimary = Navy, primaryContainer = Color(0xFF22365C), onPrimaryContainer = Color(0xFFB8CBFF), secondary = Cyan, background = Color(0xFF0B1220), surface = Color(0xFF151F31), surfaceVariant = Color(0xFF1D2B40), onBackground = Color(0xFFE5ECFA), onSurface = Color(0xFFE5ECFA), onSurfaceVariant = Color(0xFF9AA9C3), outline = Color(0xFF2C3B53))
@Composable fun MuraTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if(dark) DarkColors else LightColors, typography = Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 38.sp, lineHeight = 43.sp, letterSpacing = (-1.3).sp),
        headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    ), content = content)
}
