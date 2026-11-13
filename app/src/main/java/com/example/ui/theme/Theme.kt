package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

private val NetraDarkColorScheme = darkColorScheme(
  primary = NetraDarkPrimary,
  onPrimary = NetraDarkOnPrimary,
  primaryContainer = NetraDarkPrimaryContainer,
  onPrimaryContainer = NetraDarkOnPrimaryContainer,
  secondary = NetraDarkSecondary,
  tertiary = NetraDarkTertiary,
  error = NetraDarkError,
  background = NetraDarkBackground,
  surface = NetraDarkSurface,
  surfaceVariant = NetraDarkSurfaceVariant,
  onBackground = NetraDarkOnSurface,
  onSurface = NetraDarkOnSurface,
  onSurfaceVariant = NetraDarkOnSurfaceVariant,
  outline = NetraDarkOutline,
)

private val NetraLightColorScheme = lightColorScheme(
  primary = NetraLightPrimary,
  onPrimary = NetraLightOnPrimary,
  primaryContainer = NetraLightPrimaryContainer,
  onPrimaryContainer = NetraLightOnPrimaryContainer,
  secondary = NetraLightSecondary,
  tertiary = NetraLightTertiary,
  error = NetraLightError,
  background = NetraLightBackground,
  surface = NetraLightSurface,
  surfaceVariant = NetraLightSurfaceVariant,
  onBackground = NetraLightOnSurface,
  onSurface = NetraLightOnSurface,
  onSurfaceVariant = NetraLightOnSurfaceVariant,
  outline = NetraLightOutline,
)

val NetraShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NetraBatteryTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val darkTheme =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val colorScheme = if (darkTheme) NetraDarkColorScheme else NetraLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = NetraTypography,
    shapes = NetraShapes,
    content = content,
  )
}
