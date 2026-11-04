package cn.edu.buaa.ledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CampusBlue = Color(0xFF2257B8)
val IncomeGreen = Color(0xFF0F766E)
val ExpenseBlue = Color(0xFF2257B8)
private val Light = lightColorScheme(primary = CampusBlue, onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EEFC), onPrimaryContainer = Color(0xFF194080),
    secondary = IncomeGreen, secondaryContainer = Color(0xFFE2F2ED), onSecondaryContainer = Color(0xFF07564F),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF172033),
    surface = Color.White, onSurface = Color(0xFF172033), surfaceVariant = Color(0xFFEEF2F8),
    onSurfaceVariant = Color(0xFF526176), outline = Color(0xFF758197), outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB42318), errorContainer = Color(0xFFFDECE9))
private val Dark = darkColorScheme(primary = Color(0xFF96B8FF), onPrimary = Color(0xFF102D61),
    primaryContainer = Color(0xFF203C69), onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF6AD4BD), secondaryContainer = Color(0xFF173D3B), onSecondaryContainer = Color(0xFFA9F0DF),
    background = Color(0xFF101827), onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1A2538), onSurface = Color(0xFFF1F5F9), surfaceVariant = Color(0xFF253248),
    onSurfaceVariant = Color(0xFFB6C2D2), outline = Color(0xFF8A98B0), outlineVariant = Color(0xFF314058),
    error = Color(0xFFFFB4A9), errorContainer = Color(0xFF52241F))

val LedgerTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
)

@Composable fun LedgerTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = mode == "dark" || mode == "system" && isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = LedgerTypography, content = content)
}
