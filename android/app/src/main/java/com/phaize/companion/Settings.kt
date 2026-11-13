package com.phaize.companion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class SettingsSection(val title:String,val subtitle:String,val icon:ImageVector){
 Connection("Connection","Gateway address and token",Icons.Outlined.Link),
 Appearance("Appearance","Theme, motion and text size",Icons.Outlined.Palette),
 Privacy("Privacy","What is and is not stored",Icons.Outlined.Lock)
}

@Composable fun fieldColors()=OutlinedTextFieldDefaults.colors(
 focusedBorderColor=Pal.accent,unfocusedBorderColor=Pal.border,cursorColor=Pal.accent,focusedLabelColor=Pal.accent,unfocusedLabelColor=Pal.muted,
 focusedTextColor=Pal.text,unfocusedTextColor=Pal.text)

/** An icon, title and subtitle card with a chevron, like the categories on the desktop Settings page. */
@Composable private fun CategoryCard(index:Int,section:SettingsSection,onClick:()->Unit){
 val shape=RoundedCornerShape(18.dp)
 Row(Modifier.enter(index).fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(Pal.accent.copy(alpha=.14f),Pal.panel)))
  .border(1.dp,Pal.border,shape).pressable(onClick).padding(end=14.dp),verticalAlignment=Alignment.CenterVertically){
  Box(Modifier.padding(vertical=14.dp).width(3.dp).height(46.dp).clip(RoundedCornerShape(2.dp)).background(Pal.accent))
  Box(Modifier.padding(start=12.dp).size(46.dp).clip(RoundedCornerShape(14.dp)).background(Pal.accent.copy(alpha=.12f)).border(1.dp,Pal.border,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){
   Icon(section.icon,null,tint=Pal.accent)
  }
  Column(Modifier.weight(1f).padding(horizontal=14.dp)){
   Text(section.title,color=Pal.text,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
   Info(section.subtitle)
  }
  Icon(Icons.Outlined.ChevronRight,null,tint=Pal.muted)
 }
}

@Composable private fun SwitchControl(checked:Boolean,onChange:(Boolean)->Unit){
 Switch(checked,onChange,colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=Pal.accent.copy(alpha=.6f),checkedBorderColor=Pal.accent,
  uncheckedThumbColor=Pal.muted,uncheckedTrackColor=Pal.panelHi,uncheckedBorderColor=Pal.border))
}

@Composable private fun ThemeMenu(current:Theme,onPick:(Theme)->Unit){
 var open by remember{mutableStateOf(false)}
 Box{
  OutlinedButton(onClick={open=true},border=androidx.compose.foundation.BorderStroke(1.dp,Pal.border),shape=RoundedCornerShape(12.dp)){
   Text(current.label,color=Pal.text);Icon(Icons.Outlined.ExpandMore,null,tint=Pal.muted)
  }
  DropdownMenu(expanded=open,onDismissRequest={open=false},containerColor=Pal.panelHi){
   Theme.entries.forEach{t->DropdownMenuItem(text={Text(t.label,color=if(t==current)t.accent else Pal.text)},onClick={onPick(t);open=false})}
  }
 }
}

@Composable private fun AppearanceDetail(prefs:UiPrefs){
 var scale by remember(prefs.textScale){mutableStateOf(prefs.textScale)}
 SectionHeader(Icons.Outlined.Palette,"Appearance","How the app looks on this phone")
 SettingGroup{
  SettingRow("Colour theme","Colours the whole app: accents, glows and the navigation bar."){ThemeMenu(prefs.theme){prefs.changeTheme(it)}}
  SettingRow("Animations","Off removes motion and transitions everywhere."){SwitchControl(prefs.animations){prefs.changeAnimations(it)}}
  SettingRow("Text size","Scales all text in the app.",last=true){
   Row(verticalAlignment=Alignment.CenterVertically){
    Slider(scale,{scale=it},onValueChangeFinished={prefs.changeTextScale(scale)},valueRange=.85f..1.3f,modifier=Modifier.width(120.dp),
     colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Pal.accent,inactiveTrackColor=Pal.accent.copy(alpha=.2f)))
    Text("${(scale*100).roundToInt()}%",color=Pal.text,modifier=Modifier.padding(start=8.dp))
   }
  }
 }
 Info("Animations also turn off when Android's own animation setting is off.")
}

@Composable private fun ConnectionDetail(base:String,token:String,live:Boolean,busy:Boolean,setBase:(String)->Unit,setToken:(String)->Unit,connect:()->Unit,disconnect:()->Unit){
 SectionHeader(Icons.Outlined.Link,"Trusted HTTPS desktop gateway","A private tunnel or authenticated proxy only")
 Glass{
  Info("Do not expose the desktop's localhost over public HTTP. The app refuses plain http addresses.")
  OutlinedTextField(base,setBase,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("https://your-private-gateway")},colors=fieldColors())
  OutlinedTextField(token,setToken,modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Gateway token")},visualTransformation=PasswordVisualTransformation(),colors=fieldColors())
  Button(onClick=connect,enabled=!busy&&base.startsWith("https://")&&token.isNotBlank()){Text("Connect securely")}
  OutlinedButton(onClick=disconnect){Text("Disconnect / forget token")}
  Info(if(live)"Live data enabled" else "Disconnected, or a demo gateway",if(live)Pal.green else Pal.muted)
 }
}

@Composable private fun PrivacyRow(title:String,desc:String,tag:String,last:Boolean=false){
 SettingRow(title,desc,last){Tag(tag,Pal.green)}
}

@Composable private fun PrivacyDetail(){
 SectionHeader(Icons.Outlined.Lock,"Privacy","What this app keeps, and what it does not")
 SettingGroup{
  PrivacyRow("Gateway token","Encrypted with the Android Keystore. It is not shown again after you save it.","Encrypted")
  PrivacyRow("Chats, files and approvals","Held in memory while you read them. Nothing from them is saved on this phone.","Not stored")
  PrivacyRow("Android backups","Backups are turned off for this app.","Off")
  PrivacyRow("Remote decisions","A phone decision is only a request. The desktop checks it again before anything happens.","Re-checked",last=true)
 }
}

@Composable private fun DetailHeader(section:SettingsSection,onBack:()->Unit,showReset:Boolean,onReset:()->Unit){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
  IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back to settings",tint=Pal.accent)}
  Column(Modifier.weight(1f)){
   Text(section.title,color=Pal.text,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
   Info(section.subtitle)
  }
  if(showReset){
   OutlinedButton(onClick=onReset,border=androidx.compose.foundation.BorderStroke(1.dp,Pal.border),shape=RoundedCornerShape(12.dp)){
    Icon(Icons.Outlined.RestartAlt,null,tint=Pal.accent,modifier=Modifier.size(18.dp))
    Text("  Reset",color=Pal.text)
   }
  }
 }
}

/** Settings: category cards first, then a detail page that slides in. Connection, appearance and privacy are the real settings of this app. */
@Composable fun SettingsPage(prefs:UiPrefs,base:String,token:String,live:Boolean,busy:Boolean,setBase:(String)->Unit,setToken:(String)->Unit,connect:()->Unit,disconnect:()->Unit){
 var section by remember{mutableStateOf<SettingsSection?>(null)}
 val on=LocalAnim.current
 BackHandler(enabled=section!=null){section=null}
 AnimatedContent(targetState=section,transitionSpec={
  if(!on)EnterTransition.None togetherWith ExitTransition.None
  else if(targetState!=null)(fadeIn(tween(240))+slideInHorizontally(tween(300,easing=FastOutSlowInEasing)){it/6}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(260)){-it/8})
  else (fadeIn(tween(240))+slideInHorizontally(tween(300,easing=FastOutSlowInEasing)){-it/6}) togetherWith (fadeOut(tween(140))+slideOutHorizontally(tween(260)){it/8})
 },label="settings"){s->
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   if(s==null){
    SettingsSection.entries.forEachIndexed{i,sec->item{CategoryCard(i,sec){section=sec}}}
    item{Info("PhAize companion 0.3 • read-only chats • native desktop authorization is never replaced by this app.")}
   }else{
    item{DetailHeader(s,{section=null},s==SettingsSection.Appearance,{prefs.reset()})}
    item{
     Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
      when(s){
       SettingsSection.Appearance->AppearanceDetail(prefs)
       SettingsSection.Connection->ConnectionDetail(base,token,live,busy,setBase,setToken,connect,disconnect)
       SettingsSection.Privacy->PrivacyDetail()
      }
     }
    }
   }
  }
 }
}
