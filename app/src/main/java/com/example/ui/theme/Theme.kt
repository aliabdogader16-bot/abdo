package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = OceanBluePrimary,
    onPrimary = Color.White,
    primaryContainer = OceanBlueContainer,
    onPrimaryContainer = OnOceanBlueContainer,
    secondary = EmeraldPrimary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = TealBridgeTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TealBridgeContainer,
    onTertiaryContainer = OnTealBridgeContainer,
    background = CoolPearlBackground,
    onBackground = OnCoolBackground,
    surface = CoolSurface,
    onSurface = OnCoolBackground,
    surfaceVariant = CoolSurfaceVariant,
    onSurfaceVariant = OnCoolMuted,
    outline = CoolOutline,
    error = PayableCrimson,
    onError = Color.White,
    errorContainer = PayableCrimsonContainer,
    onErrorContainer = Color(0xFF7F1D1D),
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = OceanBluePrimaryDark,
    onPrimary = Color(0xFF002A66),
    primaryContainer = OceanBlueContainerDark,
    onPrimaryContainer = OnOceanBlueContainerDark,
    secondary = EmeraldPrimaryDark,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = OnEmeraldContainerDark,
    tertiary = TealBridgeTertiaryDark,
    onTertiary = Color(0xFF082F49),
    background = DeepSapphireBackground,
    onBackground = OnDeepSapphireBackground,
    surface = DeepSapphireSurface,
    onSurface = OnDeepSapphireBackground,
    surfaceVariant = DeepSapphireSurfaceVariant,
    onSurfaceVariant = OnDeepSapphireMuted,
    outline = DeepSapphireOutline,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF5C1919),
    onErrorContainer = Color(0xFFFEE2E2),
  )

val DuyunShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun DuyunTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = DuyunTypography,
    shapes = DuyunShapes,
    content = content,
  )
}
