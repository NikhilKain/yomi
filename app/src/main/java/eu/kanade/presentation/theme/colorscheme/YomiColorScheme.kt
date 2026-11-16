package eu.kanade.presentation.theme.colorscheme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightSakura = lightColorScheme(
    primary = Color(0xFF8E4958), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DF), onPrimaryContainer = Color(0xFF3A0716),
    secondary = Color(0xFF75565B), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9DE), onSecondaryContainer = Color(0xFF2C1519),
    tertiary = Color(0xFF7B5733), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBC), onTertiaryContainer = Color(0xFF2C1700),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF22191A),
    surface = Color(0xFFFFF8F7), onSurface = Color(0xFF22191A),
    surfaceVariant = Color(0xFFF3DDDF), onSurfaceVariant = Color(0xFF524345),
    outline = Color(0xFF847375), outlineVariant = Color(0xFFD6C2C3),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFEF1F2),
    surfaceContainer = Color(0xFFF8EBEC), surfaceContainerHigh = Color(0xFFF2E5E6),
    surfaceContainerHighest = Color(0xFFECDFE0), surfaceDim = Color(0xFFE7D6D7), surfaceBright = Color(0xFFFFF8F7),
    inverseSurface = Color(0xFF382E2F), inverseOnSurface = Color(0xFFFEEDEE), inversePrimary = Color(0xFFFFB1C0),
)

private val DarkSakura = darkColorScheme(
    primary = Color(0xFFFFB1C0), onPrimary = Color(0xFF561D2B),
    primaryContainer = Color(0xFF723341), onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFE4BDC2), onSecondary = Color(0xFF43292D),
    secondaryContainer = Color(0xFF5C3F43), onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Color(0xFFEEBD92), onTertiary = Color(0xFF472A0A),
    tertiaryContainer = Color(0xFF61401E), onTertiaryContainer = Color(0xFFFFDCBC),
    background = Color(0xFF1A1112), onBackground = Color(0xFFF0DEDF),
    surface = Color(0xFF1A1112), onSurface = Color(0xFFF0DEDF),
    surfaceVariant = Color(0xFF524345), onSurfaceVariant = Color(0xFFD6C2C3),
    outline = Color(0xFF9F8C8E), outlineVariant = Color(0xFF524345),
    surfaceContainerLowest = Color(0xFF140C0D), surfaceContainerLow = Color(0xFF22191A),
    surfaceContainer = Color(0xFF261D1E), surfaceContainerHigh = Color(0xFF312828),
    surfaceContainerHighest = Color(0xFF3C3233), surfaceDim = Color(0xFF1A1112), surfaceBright = Color(0xFF413738),
    inverseSurface = Color(0xFFF0DEDF), inverseOnSurface = Color(0xFF382E2F), inversePrimary = Color(0xFF8E4958),
)

internal val YomiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

internal object YomiColorScheme : BaseColorScheme() {
    override val darkScheme = DarkSakura
    override val lightScheme = LightSakura
}
