package com.japanese.ohanashi.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.japanese.ohanashi.R

val noto_sans = FontFamily(
    Font(R.font.notosans_jp_black,   weight = FontWeight.Black),
    Font(R.font.notosans_jp_bold,    weight = FontWeight.Bold),
    Font(R.font.notosans_jp_light,   weight = FontWeight.Light),
    Font(R.font.notosans_jp_medium,  weight = FontWeight.Medium),
    Font(R.font.notosans_jp_regular, weight = FontWeight.Normal),
    Font(R.font.notosans_jp_thin,    weight = FontWeight.Thin),
)
val noto_serif = FontFamily(
    Font(R.font.notoserif_jp_black,      weight = FontWeight.Black),
    Font(R.font.notoserif_jp_bold,       weight = FontWeight.Bold),
    Font(R.font.notoserif_jp_extralight, weight = FontWeight.ExtraLight),
    Font(R.font.notoserif_jp_light,      weight = FontWeight.Light),
    Font(R.font.notoserif_jp_medium,     weight = FontWeight.Medium),
    Font(R.font.notoserif_jp_regular,    weight = FontWeight.Normal),
    Font(R.font.notoserif_jp_semibold,   weight = FontWeight.SemiBold),
)

// Set of Material typography styles to start with
val Typography = Typography(
    body1 = TextStyle(
//        fontFamily = FontFamily.Default,
        fontFamily = noto_sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
    /* Other default text styles to override
    button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
    */
)

// Set of Material typography styles to start with
val TypographyBlackAndBrownDark = Typography(
    body1 = TextStyle(
        color = SoftWhite,
        fontFamily = noto_sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )
)