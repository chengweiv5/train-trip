package cn.traintrip.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Cream=Color(0xFFF5F3EE)
val Ink=Color(0xFF20392C)
val Muted=Color(0xFF617065)
val Forest=Color(0xFF2D5E3A)
val Sage=Color(0xFFE6EFDF)
val Line=Color(0xFFD8DED3)
val Amber=Color(0xFF87531F)
val AmberBg=Color(0xFFFBF0DE)
@Composable fun TrainTripTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Forest,onPrimary=Color.White,primaryContainer=Sage,onPrimaryContainer=Forest,background=Cream,onBackground=Ink,surface=Color.White,onSurface=Ink,surfaceVariant=Cream,onSurfaceVariant=Muted,surfaceContainer=Cream,surfaceContainerHigh=Cream,surfaceContainerHighest=Cream,surfaceContainerLow=Color.White,outline=Line,error=Amber,errorContainer=AmberBg),
        typography=Typography(headlineLarge=TextStyle(fontSize=28.sp,lineHeight=38.sp,fontWeight=FontWeight.Bold),headlineMedium=TextStyle(fontSize=24.sp,lineHeight=34.sp,fontWeight=FontWeight.SemiBold),titleLarge=TextStyle(fontSize=22.sp,fontWeight=FontWeight.SemiBold),titleMedium=TextStyle(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),labelLarge=TextStyle(fontSize=15.sp,fontWeight=FontWeight.SemiBold)),content=content)
}
