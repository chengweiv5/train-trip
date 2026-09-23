package cn.traintrip.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import cn.traintrip.app.ThemeChoice
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PageBackground=Color.White
val ControlBackground=Color.White
val HeaderControl=Color(0xCCFFFFFF)
val AvailableGreen=Color(0xFF0F7952)
val AvailableTint=Color(0xFFEAF7F0)
val Amber=Color(0xFFA8500C)
val AmberBg=Color(0xFFFFF4E5)

@Immutable
data class ThemePalette(
    val primary:Color, val tint:Color, val card:Color, val cardBorder:Color,
    val ink:Color, val muted:Color, val line:Color, val contentReady:Color,
    val actionBorder:Color, val headerBorder:Color,
)

private val palettes = mapOf(
    ThemeChoice.BLUE to ThemePalette(Color(0xFF0073AA),Color(0xFFDDF2FF),Color(0xFFEAF5FC),Color(0xFFD5EAF6),
        Color(0xFF1E3443),Color(0xFF516774),Color(0xFFD5E5EE),Color(0xFF446874),Color(0xFF93CFE9),Color(0xFFBFDFEE)),
    ThemeChoice.GREEN to ThemePalette(Color(0xFF246B50),Color(0xFFDEF2E7),Color(0xFFEDF7F1),Color(0xFFD3E8DC),
        Color(0xFF243C31),Color(0xFF536B5F),Color(0xFFD4E4DB),Color(0xFF496B59),Color(0xFF87B99E),Color(0xFFB8DBCA)),
    ThemeChoice.ORANGE to ThemePalette(Color(0xFFA64B18),Color(0xFFFCE8D8),Color(0xFFFFF4EB),Color(0xFFF0DDCD),
        Color(0xFF433025),Color(0xFF756052),Color(0xFFEBDDCE),Color(0xFF775F49),Color(0xFFD8AA85),Color(0xFFEDCDB2)),
    ThemeChoice.PURPLE to ThemePalette(Color(0xFF7052A3),Color(0xFFEEE5FA),Color(0xFFF5F0FB),Color(0xFFE3D9EF),
        Color(0xFF352C46),Color(0xFF695D78),Color(0xFFDFD5E9),Color(0xFF69597D),Color(0xFFBDA6D9),Color(0xFFD7C5EC)),
)
val ThemeChoice.palette:ThemePalette get()=palettes.getValue(this)
private val LocalThemePalette=staticCompositionLocalOf { ThemeChoice.BLUE.palette }

val CardBackground:Color @Composable get()=LocalThemePalette.current.card
val CardBorder:Color @Composable get()=LocalThemePalette.current.cardBorder
val Ink:Color @Composable get()=LocalThemePalette.current.ink
val Muted:Color @Composable get()=LocalThemePalette.current.muted
val Subtle:Color @Composable get()=Muted
val Primary:Color @Composable get()=LocalThemePalette.current.primary
val PrimaryTint:Color @Composable get()=LocalThemePalette.current.tint
val Line:Color @Composable get()=LocalThemePalette.current.line
val ContentReady:Color @Composable get()=LocalThemePalette.current.contentReady
val ActionBorder:Color @Composable get()=LocalThemePalette.current.actionBorder
val HeaderTop:Color @Composable get()=PrimaryTint
val HeaderBorder:Color @Composable get()=LocalThemePalette.current.headerBorder

@Composable fun TrainTripTheme(choice:ThemeChoice=ThemeChoice.BLUE,content:@Composable ()->Unit) {
  CompositionLocalProvider(LocalThemePalette provides choice.palette) {
    MaterialTheme(
        colorScheme=lightColorScheme(primary=Primary,onPrimary=Color.White,primaryContainer=PrimaryTint,
            onPrimaryContainer=Primary,secondary=Primary,onSecondary=Color.White,
            background=PageBackground,onBackground=Ink,surface=Color.White,onSurface=Ink,
            surfaceVariant=CardBackground,onSurfaceVariant=Muted,surfaceContainer=CardBackground,
            surfaceContainerHigh=Color.White,surfaceContainerHighest=PrimaryTint,surfaceContainerLow=Color.White,
            outline=Line,outlineVariant=Line,error=Amber,onError=Color.White,errorContainer=AmberBg,onErrorContainer=Amber,
            secondaryContainer=PrimaryTint,onSecondaryContainer=Primary,tertiary=Primary,onTertiary=Color.White,
            tertiaryContainer=PrimaryTint,onTertiaryContainer=Primary,inversePrimary=PrimaryTint,
            inverseSurface=Ink,inverseOnSurface=Color.White,surfaceTint=Primary,surfaceDim=CardBackground,
            surfaceBright=Color.White,surfaceContainerLowest=Color.White),
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
}
