package com.phaize.companion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class NavItem(val label:String,val icon:ImageVector,val badge:Int=0)

/**
 * The desktop's side rail turned horizontal: a floating dark-glass bar of icons with a glowing rounded square that slides under the
 * selected one. It sits above the system navigation bar, so nothing is hidden behind it.
 */
@Composable fun GlassNavBar(items:List<NavItem>,selected:Int,onSelect:(Int)->Unit){
 val on=LocalAnim.current
 val barShape=RoundedCornerShape(26.dp)
 val cellShape=RoundedCornerShape(18.dp)
 Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=10.dp)
  .shadow(14.dp,barShape,ambientColor=Pal.accent.copy(alpha=.5f),spotColor=Pal.accent.copy(alpha=.5f))
  .clip(barShape).background(Brush.verticalGradient(listOf(Pal.panelHi,Pal.panel))).border(1.dp,Pal.border,barShape).padding(6.dp)){
  BoxWithConstraints(Modifier.fillMaxWidth()){
   val cell=maxWidth/items.size
   val x by animateDpAsState(cell*selected,motion(on,spring(dampingRatio=.72f,stiffness=Spring.StiffnessMediumLow)),label="indicator")
   Box(Modifier.offset(x=x).width(cell).height(54.dp).padding(horizontal=3.dp)
    .shadow(10.dp,cellShape,ambientColor=Pal.accent,spotColor=Pal.accent)
    .clip(cellShape).background(Brush.verticalGradient(listOf(Pal.accent.copy(alpha=.26f),Pal.accent.copy(alpha=.08f))))
    .border(1.dp,Pal.accent.copy(alpha=.75f),cellShape))
   Row(Modifier.fillMaxWidth()){
    items.forEachIndexed{i,item->
     val chosen=i==selected
     val tint by animateColorAsState(if(chosen)Pal.accent else Pal.muted,motion(on,tween(220)),label="tint")
     val lift by animateFloatAsState(if(chosen)1.14f else 1f,motion(on,spring(dampingRatio=.55f,stiffness=Spring.StiffnessMedium)),label="lift")
     Box(Modifier.weight(1f).height(54.dp)
      .semantics{role=Role.Tab;this.selected=chosen;contentDescription=item.label}
      .clickable(interactionSource=remember(i){MutableInteractionSource()},indication=null){onSelect(i)},
      contentAlignment=Alignment.Center){
      Icon(item.icon,null,tint=tint,modifier=Modifier.size(25.dp).graphicsLayer{scaleX=lift;scaleY=lift})
      if(item.badge>0)Box(Modifier.align(Alignment.TopEnd).padding(top=7.dp,end=10.dp).size(16.dp).clip(CircleShape).background(Pal.red),contentAlignment=Alignment.Center){
       Text(if(item.badge>9)"9+" else item.badge.toString(),color=Color(0xFF2A0D0D),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
      }
     }
    }
   }
  }
 }
}
