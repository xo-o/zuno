package com.talkie.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.talkie.ai.R

val LexendFontFamily = FontFamily(
    Font(R.font.lexend, FontWeight.Normal),
    Font(R.font.lexend, FontWeight.Medium),
    Font(R.font.lexend, FontWeight.SemiBold),
    Font(R.font.lexend, FontWeight.Bold)
)

val AppFontFamily = LexendFontFamily
val InterFontFamily = LexendFontFamily

val CalSansFontFamily = FontFamily(
    Font(R.font.cal_sans_regular, FontWeight.Normal),
    Font(R.font.cal_sans_regular, FontWeight.Medium),
    Font(R.font.cal_sans_regular, FontWeight.SemiBold),
    Font(R.font.cal_sans_regular, FontWeight.Bold)
)

private val BaseTypography = Typography()

val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = LexendFontFamily),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = LexendFontFamily),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = LexendFontFamily),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = LexendFontFamily),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = LexendFontFamily),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = LexendFontFamily),
    titleLarge = TextStyle(
        fontFamily = LexendFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = LexendFontFamily),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = LexendFontFamily),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = LexendFontFamily),
    bodyMedium = TextStyle(
        fontFamily = LexendFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = LexendFontFamily),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = LexendFontFamily),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = LexendFontFamily),
    labelSmall = TextStyle(
        fontFamily = LexendFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = TextMuted
    )
)
