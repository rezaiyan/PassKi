package com.alirezaiyan.passki.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import passki.shared.generated.resources.Res
import passki.shared.generated.resources.bricolage_bold
import passki.shared.generated.resources.bricolage_extrabold

/** Check-status colours, shared with passki-web so a PASS looks the same on the site and on the device. */
@Immutable
data class StatusColors(
    val pass: Color,
    val passBg: Color,
    val warn: Color,
    val warnBg: Color,
    val fail: Color,
    val failBg: Color,
    val skip: Color,
    val skipBg: Color,
)

/** The always-dark brand surface behind the sign-in header and the setup summary. */
object Night {
    val bg = Color(0xFF070B1D)
    val panel = Color(0xFF0D1331)
    val line = Color(0xFF1D2650)
    val fg = Color(0xFFEEF0FB)
    val muted = Color(0xFF9AA2C6)
    val accent = Color(0xFF8F7BFF)
}

private val LightStatus = StatusColors(
    pass = Color(0xFF0D8A4F), passBg = Color(0xFFE3F6EB),
    warn = Color(0xFFA66300), warnBg = Color(0xFFFFF2D8),
    fail = Color(0xFFC62F2C), failBg = Color(0xFFFDE8E7),
    skip = Color(0xFF6A7189), skipBg = Color(0xFFEEF0F6),
)

private val DarkStatus = StatusColors(
    pass = Color(0xFF45D68E), passBg = Color(0xFF0E2D21),
    warn = Color(0xFFF3B74A), warnBg = Color(0xFF32260E),
    fail = Color(0xFFFF7B76), failBg = Color(0xFF391619),
    skip = Color(0xFF98A0BD), skipBg = Color(0xFF1A2140),
)

private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF6E56FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFECFF),
    onPrimaryContainer = Color(0xFF3B23C9),
    secondary = Color(0xFF565D78),
    onSecondary = Color.White,
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF141A33),
    surface = Color.White,
    onSurface = Color(0xFF141A33),
    surfaceVariant = Color(0xFFF1F2F8),
    onSurfaceVariant = Color(0xFF565D78),
    surfaceContainer = Color(0xFFF9FAFC),
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF1F2F8),
    outline = Color(0xFFCDD2E1),
    outlineVariant = Color(0xFFE2E5EF),
    error = Color(0xFFC62F2C),
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E7),
    onErrorContainer = Color(0xFF7A1513),
)

private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFF8F7BFF),
    onPrimary = Color(0xFF0A0E22),
    primaryContainer = Color(0xFF211C4A),
    onPrimaryContainer = Color(0xFFD3CBFF),
    secondary = Color(0xFFA1A8C5),
    onSecondary = Color(0xFF0A0E22),
    background = Color(0xFF0A0E22),
    onBackground = Color(0xFFE7EAF7),
    surface = Color(0xFF10162F),
    onSurface = Color(0xFFE7EAF7),
    surfaceVariant = Color(0xFF0C1128),
    onSurfaceVariant = Color(0xFFA1A8C5),
    surfaceContainer = Color(0xFF0D1229),
    surfaceContainerLow = Color(0xFF10162F),
    surfaceContainerHigh = Color(0xFF161D3B),
    outline = Color(0xFF2D375D),
    outlineVariant = Color(0xFF20294B),
    error = Color(0xFFFF7B76),
    onError = Color(0xFF0A0E22),
    errorContainer = Color(0xFF391619),
    onErrorContainer = Color(0xFFFFD4D2),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Display face for headlines (Bricolage Grotesque, OFL, bundled as a Compose resource). */
@Composable
fun displayFamily(): FontFamily = FontFamily(
    Font(Res.font.bricolage_bold, FontWeight.Bold),
    Font(Res.font.bricolage_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun typography(): Typography {
    val display = displayFamily()
    val base = Typography()
    fun TextStyle.display(weight: FontWeight, size: Int, line: Int, tracking: Double) =
        copy(fontFamily = display, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)
    return base.copy(
        displaySmall = base.displaySmall.display(FontWeight.ExtraBold, 34, 38, -1.2),
        headlineLarge = base.headlineLarge.display(FontWeight.ExtraBold, 30, 34, -1.0),
        headlineMedium = base.headlineMedium.display(FontWeight.Bold, 26, 30, -0.8),
        headlineSmall = base.headlineSmall.display(FontWeight.Bold, 22, 27, -0.5),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

private val PassKiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PassKiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = typography(),
            shapes = PassKiShapes,
            content = content,
        )
    }
}

/** Monospace for IDs, credential IDs and logs. */
val Mono = FontFamily.Monospace
