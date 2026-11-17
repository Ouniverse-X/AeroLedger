package cn.edu.buaa.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CampusBlue = Color(0xFF2257B8)
val IncomeWarm = Color(0xFFB45309)
val ExpenseBlue = Color(0xFF2257B8)
private val Light = lightColorScheme(primary = CampusBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEFC), onPrimaryContainer = Color(0xFF194080),
    secondary = IncomeWarm, secondaryContainer = Color(0xFFFFEDD5), onSecondaryContainer = Color(0xFF7C2D12),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF172033),
    surface = Color.White, onSurface = Color(0xFF172033), surfaceVariant = Color(0xFFEEF2F8),
    onSurfaceVariant = Color(0xFF526176), outline = Color(0xFF758197), outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB42318), errorContainer = Color(0xFFFDECE9))
private val Dark = darkColorScheme(primary = Color(0xFF96B8FF), onPrimary = Color(0xFF102D61),
    primaryContainer = Color(0xFF203C69), onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFFFBD89), secondaryContainer = Color(0xFF52311D), onSecondaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF101827), onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1A2538), onSurface = Color(0xFFF1F5F9), surfaceVariant = Color(0xFF253248),
    onSurfaceVariant = Color(0xFFB6C2D2), outline = Color(0xFF8A98B0), outlineVariant = Color(0xFF314058),
    error = Color(0xFFFFB4A9), errorContainer = Color(0xFF52241F))

val LedgerTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
)

enum class ThemeColor(val key: String, val label: String, val start: Color, val end: Color,
    val darkAccent: Color, val container: Color, val darkContainer: Color) {
    RED("red", "红", Color(0xFFB83248), Color(0xFF7C2038), Color(0xFFFFB1BD), Color(0xFFFCE8EC), Color(0xFF632B3A)),
    ORANGE("orange", "橙", Color(0xFFA84D13), Color(0xFF76330B), Color(0xFFFFBA8F), Color(0xFFFFEADB), Color(0xFF61371F)),
    GREEN("green", "绿", Color(0xFF21734C), Color(0xFF164D35), Color(0xFF86D8AF), Color(0xFFDFF3E8), Color(0xFF234C39)),
    TEAL("teal", "青", Color(0xFF087780), Color(0xFF07505B), Color(0xFF80D5DF), Color(0xFFDDF3F5), Color(0xFF194B52)),
    BLUE("blue", "深蓝", CampusBlue, Color(0xFF153F86), Color(0xFF96B8FF), Color(0xFFE7EEFC), Color(0xFF203C69)),
    PURPLE("purple", "紫", Color(0xFF7443AF), Color(0xFF4C2A7B), Color(0xFFD3B5FF), Color(0xFFF0E7FC), Color(0xFF493166));

    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: BLUE }
}

val LocalThemeColor = compositionLocalOf { ThemeColor.BLUE }

@Composable fun LedgerTheme(mode: String = "system", colorKey: String = "blue", content: @Composable () -> Unit) {
    val dark = mode == "dark" || mode == "system" && isSystemInDarkTheme()
    val accent = ThemeColor.fromKey(colorKey)
    val colors = if (accent == ThemeColor.BLUE) { if (dark) Dark else Light }
        else if (dark) Dark.copy(primary=accent.darkAccent, onPrimary=accent.end,
            primaryContainer=accent.darkContainer, onPrimaryContainer=accent.container)
        else Light.copy(primary=accent.start, onPrimary=Color.White,
            primaryContainer=accent.container, onPrimaryContainer=accent.end)
    CompositionLocalProvider(LocalThemeColor provides accent) {
        MaterialTheme(colorScheme=colors, typography=LedgerTypography, content=content)
    }
}
