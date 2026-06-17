package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SafeMint,
    secondary = CalmTeal,
    tertiary = SoftGreen,
    background = Slate900,
    surface = Slate800,
    onPrimary = Slate900,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Slate50,
    onSurface = Slate100,
    error = RedAccent
)

private val LightColorScheme = lightColorScheme(
    primary = CalmTeal,
    secondary = SafeMint,
    tertiary = SoftGreen,
    background = Slate50,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Slate900,
    onTertiary = Color.White,
    onBackground = Slate900,
    onSurface = Slate800,
    error = RedAccent
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic coloring by default to strictly preserve our calming branding palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
