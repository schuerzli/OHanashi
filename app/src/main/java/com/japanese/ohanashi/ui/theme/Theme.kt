package com.japanese.ohanashi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPaletteDefault = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200
)

private val LightColorPaletteDefault = lightColors(
    primary = Purple500,
    primaryVariant = Purple700,
    secondary = Teal200

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)
private val DarkColorPalette = darkColors(
    primary = Green200,
    primaryVariant = Green300,
    secondary = Teal200
)

private val LightColorPalette = lightColors(
    primary = Green300,
    primaryVariant = Green400,
    secondary = Teal200
)

private val BlackAndBrownColorsDark = lightColors(
    primary = GreyD,
    primaryVariant = GreyL,
    secondary = BrownD,
    background = GreyM,
//    onBackground = SoftWhite
)

private val BlackAndBrownColorsLight = darkColors(
    primary = GreyM,
    primaryVariant = GreyL,
    secondary = BrownM,
    background = SoftWhite,
//    onPrimary = Color(0xffffffff),
//    background = Color(0xff000000),
)

@Composable
fun OHanashiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable() () -> Unit) {
    val colors = if (darkTheme) {
        BlackAndBrownColorsDark
    } else {
//        LightColorPalette
        BlackAndBrownColorsLight
    }

    MaterialTheme(
        colors = colors,
        typography = if(darkTheme) TypographyBlackAndBrownDark else Typography,
        shapes = Shapes,
        content = content
    )
}