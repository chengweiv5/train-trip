package cn.traintrip.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.traintrip.app.R
import kotlin.math.cos
import kotlin.math.sin

@Composable internal fun UiIcon(kind:String,modifier:Modifier=Modifier,color:Color=Primary) {
    if(kind=="settings") {
        Icon(painterResource(R.drawable.ic_settings),contentDescription=null,modifier=modifier.size(20.dp),tint=color)
        return
    }
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
        }
    }
}

@Composable internal fun AppTopBar(title:String,onBack:(()->Unit)?=null,action:String?=null,
    onAction:()->Unit={},settings:Boolean=false,enabled:Boolean=true,backTag:String="navigate-back",trailing:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(HeaderTop,PageBackground)))
        .heightIn(min=64.dp).padding(start=10.dp,end=16.dp,top=4.dp,bottom=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(onBack!=null)HeaderIconButton("back","返回",onBack,Modifier.testTag(backTag),enabled)
        Text(title,Modifier.weight(1f).padding(start=if(onBack==null)6.dp else 0.dp).testTag("page-title"),
            color=Ink,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Start)
        when {
            trailing!=null->Box(contentAlignment=Alignment.Center) { trailing() }
            settings->HeaderIconButton("settings","设置",onAction,Modifier.testTag("content-settings"),enabled)
            action!=null->TextButton(onAction,Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(0.dp),enabled=enabled) {
                Surface(shape=RoundedCornerShape(12.dp),color=HeaderControl,border=BorderStroke(1.dp,HeaderBorder)) {
                    Text(action,Modifier.padding(horizontal=10.dp,vertical=8.dp),style=MaterialTheme.typography.bodyMedium,color=if(enabled)Primary else Muted)
                }
            }
        }
    }
}
