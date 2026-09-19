package cn.traintrip.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PageBackground=Color(0xFFF5F7FA)
val Ink=Color(0xFF17233D)
val Muted=Color(0xFF596579)
val Subtle=Color(0xFF687589)
val Primary=Color(0xFF1769E8)
val PrimaryTint=Color(0xFFEBF3FF)
val Line=Color(0xFFE6EAF0)
val AvailableGreen=Color(0xFF12845B)
val AvailableTint=Color(0xFFEAF7F0)
val Amber=Color(0xFFA8500C)
val AmberBg=Color(0xFFFFF4E5)

@Composable fun TrainTripTheme(content:@Composable ()->Unit) {
    MaterialTheme(
        colorScheme=lightColorScheme(primary=Primary,onPrimary=Color.White,primaryContainer=PrimaryTint,
            onPrimaryContainer=Primary,secondary=Primary,onSecondary=Color.White,
            background=PageBackground,onBackground=Ink,surface=Color.White,onSurface=Ink,
            surfaceVariant=PageBackground,onSurfaceVariant=Muted,surfaceContainer=PageBackground,
            surfaceContainerHigh=Color.White,surfaceContainerHighest=PageBackground,surfaceContainerLow=Color.White,
            outline=Line,outlineVariant=Line,error=Amber,onError=Color.White,errorContainer=AmberBg,onErrorContainer=Amber),
        shapes=Shapes(small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(12.dp),extraLarge=RoundedCornerShape(16.dp)),
        typography=Typography(
            headlineLarge=TextStyle(fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold),
            headlineMedium=TextStyle(fontSize=22.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
            titleLarge=TextStyle(fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.SemiBold),
            titleMedium=TextStyle(fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
            titleSmall=TextStyle(fontSize=14.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold),
            bodyLarge=TextStyle(fontSize=15.sp,lineHeight=23.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),
            bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
            labelLarge=TextStyle(fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold)),content=content)
}
