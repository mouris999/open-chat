package com.openchat.app.presentation.components
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable fun AppTopBar(title:String, onSearch: (() -> Unit)? = null, onProfile: (() -> Unit)? = null, isPrivate:Boolean=false, onLock:(()->Unit)?=null, onMenu: (() -> Unit)? = null){
    Surface(shadowElevation = 1.dp, tonalElevation = 0.dp){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=10.dp), verticalAlignment = Alignment.CenterVertically){
            Text(title, style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold), modifier=Modifier.weight(1f))
            if(isPrivate) Icon(Icons.Default.Lock, null, tint=MaterialTheme.colorScheme.primary, modifier=Modifier.size(18.dp).padding(end=8.dp))
            if(onSearch!=null) IconButton(onClick=onSearch, modifier=Modifier.size(36.dp)){ Icon(Icons.Default.Search,null, modifier=Modifier.size(20.dp)) }
            if(onLock!=null) IconButton(onClick=onLock, modifier=Modifier.size(36.dp)){ Icon(if(isPrivate) Icons.Default.Lock else Icons.Default.LockOpen, null, modifier=Modifier.size(20.dp), tint=if(isPrivate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            if(onProfile!=null) IconButton(onClick=onProfile, modifier=Modifier.size(36.dp)){ Icon(Icons.Default.AccountCircle,null, modifier=Modifier.size(22.dp)) }
            if(onMenu!=null) IconButton(onClick=onMenu, modifier=Modifier.size(36.dp)){ Icon(Icons.Default.MoreVert,null, modifier=Modifier.size(20.dp)) }
        }
    }
}
@Composable fun SearchBar(query:String, onQuery:(String)->Unit, hint:String="Search"){
    OutlinedTextField(value=query, onValueChange=onQuery, placeholder={Text(hint, style=MaterialTheme.typography.bodyMedium)}, leadingIcon={Icon(Icons.Default.Search,null, modifier=Modifier.size(18.dp))}, trailingIcon={ if(query.isNotEmpty()) IconButton(onClick={onQuery("")}){Icon(Icons.Default.Close,null,Modifier.size(18.dp))}}, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp), colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant, focusedBorderColor=MaterialTheme.colorScheme.primary))
}
@Composable fun Avatar(name:String, size:Int=48, isGroup:Boolean=false){
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment=Alignment.Center){
        Text(name.take(1).uppercase(), style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold), color=MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
@Composable fun SectionHeader(title:String, action:String? = null, onAction:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp), verticalAlignment=Alignment.CenterVertically){
        Text(title, style=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.SemiBold), color=MaterialTheme.colorScheme.primary, modifier=Modifier.weight(1f))
        if(action!=null && onAction!=null) TextButton(onClick=onAction){ Text(action, style=MaterialTheme.typography.labelMedium) }
    }
}
@Composable fun SettingsRow(icon:ImageVector, title:String, subtitle:String? = null, trailing: (@Composable () -> Unit)? = null, onClick:(()->Unit)?=null){
    val mod = if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier
    Row(mod.fillMaxWidth().padding(horizontal=16.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically){
        Surface(shape=RoundedCornerShape(10.dp), color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.size(38.dp)){ Box(contentAlignment=Alignment.Center, modifier=Modifier.fillMaxSize()){ Icon(icon,null, modifier=Modifier.size(18.dp), tint=MaterialTheme.colorScheme.onSecondaryContainer)} }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)){
            Text(title, style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium), maxLines=1, overflow=TextOverflow.Ellipsis)
            if(subtitle!=null) Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1, overflow=TextOverflow.Ellipsis)
        }
        if(trailing!=null) trailing() else if(onClick!=null) Icon(Icons.Default.ChevronRight,null, modifier=Modifier.size(18.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun SettingsSection(title:String, content:@Composable ColumnScope.()->Unit){
    Column{
        Text(title, style=MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.SemiBold), color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=16.dp, vertical=6.dp))
        Surface(shape=RoundedCornerShape(16.dp), tonalElevation=1.dp, modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp)){ Column(content=content) }
        Spacer(Modifier.height(10.dp))
    }
}
@Composable fun EmptyState(icon:ImageVector, title:String, subtitle:String, actionLabel:String? = null, onAction:(()->Unit)?=null){
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment=Alignment.CenterHorizontally){
        Surface(shape=CircleShape, color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.size(72.dp)){ Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Icon(icon,null, modifier=Modifier.size(32.dp), tint=MaterialTheme.colorScheme.onSecondaryContainer)} }
        Spacer(Modifier.height(16.dp))
        Text(title, style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(actionLabel!=null && onAction!=null){ Spacer(Modifier.height(20.dp)); FilledTonalButton(onClick=onAction, shape=RoundedCornerShape(24.dp)){ Text(actionLabel) } }
    }
}
@Composable fun PremiumDivider(){ HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f), thickness=0.8.dp) }
