package com.openchat.app.presentation.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
val Typography = Typography(
    displayLarge = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.SemiBold, fontSize=34.sp, lineHeight=40.sp, letterSpacing=(-0.3).sp),
    displayMedium = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.SemiBold, fontSize=30.sp, lineHeight=36.sp, letterSpacing=(-0.2).sp),
    headlineLarge = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.SemiBold, fontSize=28.sp, lineHeight=34.sp),
    headlineMedium = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.SemiBold, fontSize=24.sp, lineHeight=30.sp),
    titleLarge = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.SemiBold, fontSize=22.sp, lineHeight=28.sp),
    titleMedium = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Medium, fontSize=17.sp, lineHeight=24.sp, letterSpacing=0.1.sp),
    titleSmall = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Medium, fontSize=15.sp, lineHeight=20.sp, letterSpacing=0.1.sp),
    bodyLarge = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Normal, fontSize=16.sp, lineHeight=24.sp, letterSpacing=0.15.sp),
    bodyMedium = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Normal, fontSize=15.sp, lineHeight=22.sp, letterSpacing=0.15.sp),
    bodySmall = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Normal, fontSize=13.sp, lineHeight=18.sp, letterSpacing=0.2.sp),
    labelLarge = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Medium, fontSize=13.sp, lineHeight=16.sp, letterSpacing=0.3.sp),
    labelMedium = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Medium, fontSize=11.sp, lineHeight=14.sp, letterSpacing=0.5.sp),
    labelSmall = TextStyle(fontFamily=FontFamily.Default, fontWeight=FontWeight.Medium, fontSize=10.sp, lineHeight=13.sp, letterSpacing=0.6.sp)
)
