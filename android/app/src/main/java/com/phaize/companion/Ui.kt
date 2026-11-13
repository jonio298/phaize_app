package com.phaize.companion

import android.content.Context
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The colour themes. The accent drives glows, toggles, the nav bar and every highlight. */
enum class Theme(val label:String,val accent:Color,val dim:Color){
 Hologram("Hologram (Default)",Color(0xFF4FD8E8),Color(0xFF1F6F7D)),
 Aurora("Aurora",Color(0xFFA78BFA),Color(0xFF5B49A8)),
 Ember("Ember",Color(0xFFF1A65E),Color(0xFF9A5B1F))
}

/** Live palette: changing the theme recomposes everything that reads it. */
object Pal {
 var accent by mutableStateOf(Theme.Hologram.accent)
 var dim by mutableStateOf(Theme.Hologram.dim)
 val bg=Color(0xFF050B11)
 val bg2=Color(0xFF0A1822)
 val panel=Color(0xFF0B1821)
 val panelHi=Color(0xFF112530)
 val text=Color(0xFFEAF6F8)
 val muted=Color(0xFF8FA9B5)
 val amber=Color(0xFFF1BD72)
 val red=Color(0xFFEA8B8B)
 val green=Color(0xFF82DAAE)
 val border:Color get()=accent.copy(alpha=.30f)
 fun useTheme(t:Theme){accent=t.accent;dim=t.dim}
}

/** Appearance settings, stored on this phone only. */
class UiPrefs(ctx:Context){
 private val p=ctx.getSharedPreferences("ui",Context.MODE_PRIVATE)
 var animations by mutableStateOf(p.getBoolean("animations",true)); private set
 var textScale by mutableStateOf(p.getFloat("textScale",1f).coerceIn(.85f,1.3f)); private set
 var theme by mutableStateOf(Theme.entries.firstOrNull{it.name==p.getString("theme",Theme.Hologram.name)}?:Theme.Hologram); private set
 fun changeAnimations(v:Boolean){animations=v;p.edit().putBoolean("animations",v).apply()}
 fun changeTextScale(v:Float){textScale=v.coerceIn(.85f,1.3f);p.edit().putFloat("textScale",textScale).apply()}
 fun changeTheme(t:Theme){theme=t;Pal.useTheme(t);p.edit().putString("theme",t.name).apply()}
 fun reset(){changeAnimations(true);changeTextScale(1f);changeTheme(Theme.Hologram)}
}

/** False when the owner turned animations off, or the system asks for no motion. */
val LocalAnim=compositionLocalOf{true}

fun <T> motion(on:Boolean,spec:AnimationSpec<T>):AnimationSpec<T> = if(on) spec else snap()

/** Dark navy backdrop with a slow accent glow and the dotted grid used on the desktop. */
@Composable fun Modifier.phaizeBackground():Modifier {
 val on=LocalAnim.current
 val drift=if(on) rememberInfiniteTransition(label="bg").animateFloat(0f,1f,infiniteRepeatable(tween(16000,easing=LinearEasing),RepeatMode.Reverse),label="drift") else null
 return this.drawWithCache{
  val step=26.dp.toPx()
  val points=ArrayList<Offset>()
  var y=step/2
  while(y<size.height){var x=step/2;while(x<size.width){points.add(Offset(x,y));x+=step};y+=step}
  onDrawBehind{
   drawRect(Brush.verticalGradient(listOf(Pal.bg2,Pal.bg)))
   val t=drift?.value?:.5f
   val centre=Offset(size.width*(.15f+.7f*t),size.height*.10f)
   drawCircle(Brush.radialGradient(listOf(Pal.accent.copy(alpha=.12f),Color.Transparent),center=centre,radius=size.width),radius=size.width,center=centre)
   drawPoints(points,PointMode.Points,Pal.accent.copy(alpha=.08f),strokeWidth=2.2.dp.toPx(),cap=StrokeCap.Round)
  }
 }
}

/** Presses shrink slightly, like the desktop buttons. */
@Composable fun Modifier.pressable(onClick:()->Unit):Modifier {
 val source=remember{MutableInteractionSource()}
 val pressed by source.collectIsPressedAsState()
 val on=LocalAnim.current
 val scale by animateFloatAsState(if(pressed&&on)0.97f else 1f,motion(on,spring(stiffness=Spring.StiffnessMedium)),label="press")
 return this.graphicsLayer{scaleX=scale;scaleY=scale}.clickable(interactionSource=source,indication=null,onClick=onClick)
}

/** Fades and rises into place, a little later for each following item. */
@Composable fun Modifier.enter(index:Int):Modifier {
 val on=LocalAnim.current
 var shown by remember{mutableStateOf(!on)}
 LaunchedEffect(Unit){if(on){delay(index.coerceIn(0,8)*45L);shown=true}}
 val a by animateFloatAsState(if(shown)1f else 0f,motion(on,tween(380,easing=FastOutSlowInEasing)),label="enter")
 return this.graphicsLayer{alpha=a;translationY=(1f-a)*24.dp.toPx()}
}

/** The rounded dark-glass panel with a thin accent border used for every card. */
@Composable fun Glass(modifier:Modifier=Modifier,corner:Dp=18.dp,content:@Composable ColumnScope.()->Unit){
 val shape=RoundedCornerShape(corner)
 Column(modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(listOf(Pal.panelHi,Pal.panel))).border(1.dp,Pal.border,shape).padding(14.dp),
  verticalArrangement=Arrangement.spacedBy(7.dp),content=content)
}

@Composable fun Tag(label:String,color:Color=Pal.accent){
 Surface(color=color.copy(alpha=.14f),shape=RoundedCornerShape(10.dp)){
  Text(label,color=color,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
 }
}

@Composable fun Info(text:String,color:Color=Pal.muted){Text(text,color=color,style=MaterialTheme.typography.bodySmall)}

/** A status dot that gently pulses while live. */
@Composable fun LiveDot(live:Boolean){
 val on=LocalAnim.current
 val colour=if(live)Pal.green else Pal.amber
 val pulse=if(on) rememberInfiniteTransition(label="dot").animateFloat(0f,1f,infiniteRepeatable(tween(1500),RepeatMode.Restart),label="pulse") else null
 Canvas(Modifier.size(14.dp)){
  val p=pulse?.value?:0f
  drawCircle(colour.copy(alpha=.35f*(1f-p)),radius=size.minDimension/2*(.45f+.55f*p))
  drawCircle(colour,radius=size.minDimension/5f)
 }
}

// ---- Settings building blocks, after the desktop Settings page: icon, title and subtitle cards; grouped rows with the control on the right.

@Composable fun SectionHeader(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,desc:String?=null){
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(top=6.dp,bottom=2.dp)){
  androidx.compose.material3.Icon(icon,null,tint=Pal.accent,modifier=Modifier.size(22.dp))
  Column{
   Text(title,color=Pal.text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
   if(desc!=null)Info(desc)
  }
 }
}

@Composable fun SettingGroup(content:@Composable ColumnScope.()->Unit){
 val shape=RoundedCornerShape(16.dp)
 Column(Modifier.fillMaxWidth().clip(shape).background(Pal.panel.copy(alpha=.94f)).border(1.dp,Pal.border,shape),content=content)
}

@Composable fun SettingRow(title:String,desc:String,last:Boolean=false,control:@Composable ()->Unit){
 Column{
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text(title,color=Pal.text,fontWeight=FontWeight.SemiBold)
    Info(desc)
   }
   Spacer(Modifier.width(12.dp))
   control()
  }
  if(!last)HorizontalDivider(color=Pal.border.copy(alpha=.5f))
 }
}
