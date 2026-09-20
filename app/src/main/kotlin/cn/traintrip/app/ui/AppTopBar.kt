package cn.traintrip.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable internal fun UiIcon(kind:String,modifier:Modifier=Modifier,color:Color=Primary) {
    Canvas(modifier.size(20.dp)) {
        val u=size.width/24f
        val stroke=1.8f*u
        fun line(x:Float,y:Float,xx:Float,yy:Float)=drawLine(color,Offset(x*u,y*u),Offset(xx*u,yy*u),stroke,StrokeCap.Round)
        when(kind) {
            "star", "star-filled" -> {
                val path=androidx.compose.ui.graphics.Path()
                repeat(10) { i -> val a=-Math.PI/2+i*Math.PI/5;val r=if(i%2==0)10f else 4.7f
                    val x=(12+r*cos(a).toFloat())*u;val y=(12+r*sin(a).toFloat())*u
                    if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
                };path.close()
                if(kind=="star-filled")drawPath(path,color) else drawPath(path,color,style=Stroke(stroke))
            }
            "train" -> { drawRoundRect(color,Offset(5*u,2*u),androidx.compose.ui.geometry.Size(14*u,16*u),androidx.compose.ui.geometry.CornerRadius(4*u),style=Stroke(stroke));line(5f,10f,19f,10f);line(8f,18f,5f,22f);line(16f,18f,19f,22f);drawCircle(color,1.2f*u,Offset(8*u,14*u));drawCircle(color,1.2f*u,Offset(16*u,14*u)) }

            "plus"->{line(5f,12f,19f,12f);line(12f,5f,12f,19f)}
            "back"->{line(15f,4f,7f,12f);line(7f,12f,15f,20f)}
            "down"->{line(6f,9f,12f,15f);line(12f,15f,18f,9f)}
            "up"->{line(6f,15f,12f,9f);line(12f,9f,18f,15f)}
            "next"->{line(9f,6f,15f,12f);line(15f,12f,9f,18f)}
            "refresh"->{
                drawArc(color,45f,285f,false,Offset(4*u,4*u),androidx.compose.ui.geometry.Size(16*u,16*u),style=Stroke(stroke,cap=StrokeCap.Round))
                line(20f,3f,20f,9f);line(20f,9f,14f,9f)
            }
            "settings"->{
                drawCircle(color,7*u,Offset(12*u,12*u),style=Stroke(stroke))
                drawCircle(color,2.6f*u,Offset(12*u,12*u),style=Stroke(stroke))
                repeat(8) { i->val a=i*Math.PI/4;line(12+7*cos(a).toFloat(),12+7*sin(a).toFloat(),12+10*cos(a).toFloat(),12+10*sin(a).toFloat()) }
            }
        }
    }
}

@Composable internal fun AppTopBar(title:String,onBack:(()->Unit)?=null,action:String?=null,
    onAction:()->Unit={},settings:Boolean=false,enabled:Boolean=true,backTag:String="navigate-back",trailing:(@Composable ()->Unit)?=null) {
    Surface(color=Color.White) {
        val slotWidth=if(action!=null) (action.length*16*androidx.compose.ui.platform.LocalDensity.current.fontScale+16).dp.coerceAtLeast(64.dp) else 48.dp
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null) Box(Modifier.width(slotWidth)) {
                IconButton(onBack,Modifier.size(48.dp).testTag(backTag).semantics { contentDescription="返回" },enabled=enabled) { UiIcon("back",color=Ink) }
            }
            Text(title,Modifier.weight(1f).padding(start=if(onBack==null) 8.dp else 0.dp),
                fontSize=18.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold,textAlign=if(onBack==null) TextAlign.Start else TextAlign.Center)
            when {
                trailing!=null->Box(Modifier.width(48.dp),contentAlignment=Alignment.Center) { trailing() }
                settings->IconButton(onAction,Modifier.size(48.dp).testTag("content-settings").semantics { contentDescription="设置" }) { UiIcon("settings",color=Muted) }
                action!=null->TextButton(onAction,Modifier.width(slotWidth).heightIn(min=48.dp),contentPadding=PaddingValues(4.dp),enabled=enabled) { Text(action,style=MaterialTheme.typography.bodyMedium) }
                onBack!=null->Spacer(Modifier.width(48.dp))
            }
        }
    }
}
