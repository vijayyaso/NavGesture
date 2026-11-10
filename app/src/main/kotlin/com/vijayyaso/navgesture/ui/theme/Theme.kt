// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.


package com.vijayyaso.navgesture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette taken from the launcher icon: a brighter take on its blue swipe arc, on a cool grey
// page with white cards (grouped-list look). Every role is set explicitly: overriding only a
// few leaves the rest on Material 3's purple baseline, which made the tonal button lavender
// and the card surfaces pink-grey. The values are listed in docs/designs/ui-redesign.md.

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6BD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FD),
    onPrimaryContainer = Color(0xFF0A2A5E),
    inversePrimary = Color(0xFF9DBBFF),
    secondary = Color(0xFF4D5D73),
    onSecondary = Color.White,
    // FilledTonalButton draws with the secondary container, so it is the light brand blue.
    secondaryContainer = Color(0xFFDCE7FD),
    onSecondaryContainer = Color(0xFF0A2A5E),
    tertiary = Color(0xFF2F6BD8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE7FD),
    onTertiaryContainer = Color(0xFF0A2A5E),
    background = Color(0xFFF2F4F8),
    onBackground = Color(0xFF11141A),
    surface = Color.White,
    onSurface = Color(0xFF11141A),
    surfaceVariant = Color(0xFFE9ECF1),
    onSurfaceVariant = Color(0xFF5F6672),
    surfaceTint = Color(0xFF2F6BD8),
    inverseSurface = Color(0xFF2A2E35),
    inverseOnSurface = Color(0xFFF1F3F7),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E5),
    onErrorContainer = Color(0xFF5C0F0A),
    outline = Color(0xFF8A919C),
    outlineVariant = Color(0xFFE3E6EC),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE1E4EA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = Color(0xFFF1F3F7),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE9ECF1),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FAAFF),
    onPrimary = Color(0xFF0A2A5E),
    primaryContainer = Color(0xFF203C6B),
    onPrimaryContainer = Color(0xFFDCE7FD),
    inversePrimary = Color(0xFF1F5AC0),
    secondary = Color(0xFFB6C3D6),
    onSecondary = Color(0xFF1F2B3B),
    secondaryContainer = Color(0xFF203C6B),
    onSecondaryContainer = Color(0xFFDCE7FD),
    tertiary = Color(0xFF7FAAFF),
    onTertiary = Color(0xFF0A2A5E),
    tertiaryContainer = Color(0xFF203C6B),
    onTertiaryContainer = Color(0xFFDCE7FD),
    background = Color(0xFF0B0D11),
    onBackground = Color(0xFFE8EBF0),
    surface = Color(0xFF1A1D23),
    onSurface = Color(0xFFE8EBF0),
    surfaceVariant = Color(0xFF262A31),
    onSurfaceVariant = Color(0xFFA3AAB6),
    surfaceTint = Color(0xFF7FAAFF),
    inverseSurface = Color(0xFFE8EBF0),
    inverseOnSurface = Color(0xFF1A1D23),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C0F0A),
    errorContainer = Color(0xFF7A1F18),
    onErrorContainer = Color(0xFFFFDAD5),
    outline = Color(0xFF7C838F),
    outlineVariant = Color(0xFF2A2E36),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A2E36),
    surfaceDim = Color(0xFF0B0D11),
    surfaceContainerLowest = Color(0xFF0F1115),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF1A1D23),
    surfaceContainerHigh = Color(0xFF22262D),
    surfaceContainerHighest = Color(0xFF2A2E36),
)

val successContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1C4430) else Color(0xFFD5F2E0)

val onSuccessContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFB7EBCB) else Color(0xFF0B3D21)

/** Strong enough for a status dot on the plain surface, in either theme. */
val success: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FD39A) else Color(0xFF1E8E4E)

val warning: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFBA52) else Color(0xFFB26A00)

val warningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF4E3500) else Color(0xFFFFE8C2)

val onWarningContainer: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFDFA8) else Color(0xFF3D2800)

/**
 * Backgrounds of the small icon squares on tiles and rows, the same in both themes. Each is
 * dark enough (3:1 or more) for the white glyph drawn on it.
 */
object IconTint {
    val BLUE = Color(0xFF2F6BD8)
    val GREEN = Color(0xFF248A3D)
    val ORANGE = Color(0xFFC9650A)
    val PURPLE = Color(0xFF7E57C2)
    val RED = Color(0xFFD93025)
    val GREY = Color(0xFF6B7380)
    val TEAL = Color(0xFF0B7E8A)
}

// Wallpaper-derived dynamic colour is deliberately not used: it makes the palette
// unpredictable - on a neutral wallpaper everything renders grey and the badges turn muddy.
@Composable
fun NavGestureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
