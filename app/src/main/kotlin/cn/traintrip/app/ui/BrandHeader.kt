package cn.traintrip.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import cn.traintrip.app.R
import androidx.compose.ui.unit.sp

@Composable internal fun HomeBrandHeader(onSettings:()->Unit) {
    BrandHeader("train",stringResource(R.string.app_name),false,action={
        HeaderIconButton("settings","设置",onSettings,Modifier.testTag("content-settings"))
    }) {
        Column(Modifier.fillMaxWidth().heightIn(min=68.dp).padding(start=16.dp,end=16.dp,top=4.dp,bottom=14.dp),
            verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("先看有票，再选目的地",Modifier.testTag("home-headline"),
                color=Primary,fontSize=22.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold)
            Text("查直达去程，发现周边好去处",color=Muted,fontSize=13.sp,lineHeight=19.sp)
        }
    }
}

@Composable internal fun WishlistBrandHeader(summary:String,onAdd:()->Unit) {
    BrandHeader("star","想去",true,action={
        TextButton(onAdd,Modifier.heightIn(min=48.dp).testTag("wishlist-add"),contentPadding=PaddingValues(0.dp)) {
            Surface(shape=RoundedCornerShape(12.dp),color=HeaderControl,border=BorderStroke(1.dp,HeaderBorder)) {
                Row(Modifier.heightIn(min=36.dp).padding(horizontal=10.dp,vertical=6.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    UiIcon("plus",Modifier.size(16.dp))
                    Text("添加城市",fontSize=13.sp,lineHeight=19.sp,color=Primary,fontWeight=FontWeight.Medium)
                }
            }
        }
    }) {
        Text(summary,Modifier.fillMaxWidth().heightIn(min=40.dp).padding(start=16.dp,end=16.dp,top=4.dp,bottom=16.dp)
            .testTag("wishlist-summary"),fontSize=13.sp,lineHeight=20.sp,color=Muted)
    }
}

@Composable private fun BrandHeader(icon:String,title:String,largeTitle:Boolean,
    action:@Composable ()->Unit,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(HeaderTop,PageBackground)))) {
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(start=16.dp,end=16.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Surface(Modifier.size(32.dp),shape=RoundedCornerShape(10.dp),color=Primary) {
                Box(contentAlignment=Alignment.Center) { UiIcon(icon,Modifier.size(20.dp),Color.White) }
            }
            Text(title,Modifier.weight(1f).testTag("brand-title"),color=Ink,
                fontSize=if(largeTitle)22.sp else 18.sp,lineHeight=if(largeTitle)30.sp else 26.sp,fontWeight=FontWeight.Bold)
            action()
        }
        content()
    }
}

@Composable internal fun HeaderIconButton(icon:String,label:String,onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true) {
    IconButton(onClick,modifier.size(48.dp).semantics { contentDescription=label },enabled=enabled) {
        Surface(Modifier.size(36.dp),shape=RoundedCornerShape(12.dp),color=HeaderControl,
            border=BorderStroke(1.dp,HeaderBorder)) {
            Box(contentAlignment=Alignment.Center) { UiIcon(icon,Modifier.size(19.dp),if(enabled)Primary else Muted) }
        }
    }
}
