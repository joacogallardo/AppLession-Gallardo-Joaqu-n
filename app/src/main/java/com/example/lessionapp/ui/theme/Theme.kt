package com.example.lessionapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ProaBluePrimaryDark,
    onPrimary = ProaOnPrimaryDark,
    primaryContainer = ProaBlueSecondaryDark,
    secondary = ProaCyanAccentDark,
    tertiary = ProaCyanAccentDark,
    background = ProaBackgroundDark,
    surface = ProaSurfaceDark,
    onBackground = ProaOnSurfaceDark,
    onSurface = ProaOnSurfaceDark,
    outline = ProaBlueSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = ProaBluePrimary,
    onPrimary = ProaOnPrimary,
    primaryContainer = ProaBlueSecondary,
    secondary = ProaCyanAccent,
    tertiary = ProaCyanAccent,
    background = ProaBackgroundLight,
    surface = ProaSurfaceLight,
    onBackground = ProaOnSurface,
    onSurface = ProaOnSurface,
    outline = ProaBlueSecondary
)

@Composable
fun LessionappTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
