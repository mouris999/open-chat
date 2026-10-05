package com.openchat.app.presentation.screens.settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.openchat.app.presentation.theme.DividerInk
import com.openchat.app.presentation.theme.Motion
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(onNavigateBack:()->Unit,onNavigateToProfile:()->Unit,onNavigateToNotifications:()->Unit,onNavigateToPrivacy:()->Unit,onNavigateToAppLock:()->Unit,onNavigateToChatLock:()->Unit,onNavigateToStorage:()->Unit,onNavigateToHelp:()->Unit,onNavigateToAbout:()->Unit,onNavigateToScheduledMessages:()->Unit,onSignOut:()->Unit,viewModel:SettingsViewModel=hiltViewModel()){
    val uiState by viewModel.uiState.collectAsState(); val ctx=LocalContext.current
    val scanLauncher=rememberLauncherForActivityResult(ScanContract()){r-> if(r.contents!=null) viewModel.onEvent(SettingsEvent.LinkWebDevice(r.contents)) }
    // Refresh lock state on entry: ChatLockSetup is pushed on this same back stack,
    // so this ViewModel survives the trip and would otherwise show stale values.
    LaunchedEffect(Unit){ viewModel.onEvent(SettingsEvent.RefreshLockState) }
    LaunchedEffect(Unit){
        viewModel.effect.collect{ e-> when(e){
            is SettingsEffect.DeviceLinked-> Toast.makeText(ctx,"Device linked!",Toast.LENGTH_SHORT).show()
            is SettingsEffect.ShowError-> Toast.makeText(ctx,e.message,Toast.LENGTH_LONG).show()
            // Only navigate once the Firebase session has actually been terminated
            // by the ViewModel - navigating from the onClick callback would leave the
            // user signed in and bounced straight back to Home on next launch.
            is SettingsEffect.NavigateToAuth-> onSignOut()
        } }
    }
    Scaffold(containerColor=MaterialTheme.colorScheme.background, topBar={ TopAppBar(title={Text("Settings", style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold))}, navigationIcon={IconButton(onClick=onNavigateBack){Icon(Icons.Default.ArrowBack,null)}}, colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)) }){ padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())){
            Row(Modifier.fillMaxWidth().clickable(onClick=onNavigateToProfile).padding(horizontal=16.dp, vertical=18.dp), verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment=Alignment.Center){
                    if(uiState.currentUser?.photoUrl!=null) AsyncImage(model=uiState.currentUser?.photoUrl, contentDescription=null, modifier=Modifier.fillMaxSize().clip(CircleShape))
                    else Icon(Icons.Default.AccountCircle,null, Modifier.fillMaxSize(0.85f), tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)){
                    Text(uiState.currentUser?.displayName?:"Your name", style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
                    Text(uiState.currentUser?.phoneNumber?:"Tap to edit profile", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight,null, Modifier.size(18.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color=DividerInk.copy(alpha=0.35f), thickness=0.6.dp)
            Spacer(Modifier.height(18.dp))
            QuietSection("Account", 0){
                QuietRow("Profile","Edit name, photo", onClick=onNavigateToProfile)
                QuietRow("Privacy","Blocks, last seen", onClick=onNavigateToPrivacy)
                QuietRow("Security","App lock, chat lock", onClick=onNavigateToAppLock)
                QuietRow("Chat lock", if(uiState.isChatLockEnabled) "${uiState.lockedChatCount} conversations" else "Off", onClick=onNavigateToChatLock)
            }
            QuietSection("Messaging", 1){
                QuietRow("Notifications","Message and call alerts", onClick=onNavigateToNotifications)
                QuietRow("Scheduled messages","Send later", onClick=onNavigateToScheduledMessages)
            }
            QuietSection("Appearance", 2){
                QuietRow("Theme", when(uiState.themeMode){ com.openchat.app.presentation.theme.ThemeMode.LIGHT->"Light"; com.openchat.app.presentation.theme.ThemeMode.DARK->"Dark"; com.openchat.app.presentation.theme.ThemeMode.SYSTEM->"System" }, onClick={viewModel.onEvent(SettingsEvent.ShowThemeDialog)})
                QuietRow("Font size", when(uiState.fontSize){FontSize.SMALL->"Small"; FontSize.MEDIUM->"Medium"; FontSize.LARGE->"Large"; FontSize.EXTRA_LARGE->"Extra large"}, onClick={viewModel.onEvent(SettingsEvent.ShowFontSizeDialog)})
            }
            QuietSection("Devices", 3){
                QuietRow("Link device","Scan QR to link desktop", onClick={
                    val o=ScanOptions().apply{setDesiredBarcodeFormats(ScanOptions.QR_CODE); setPrompt("Scan QR on computer"); setBeepEnabled(false); setBarcodeImageEnabled(true); setOrientationLocked(false)}
                    scanLauncher.launch(o)
                })
                QuietRow("Storage usage","Media and files", onClick=onNavigateToStorage)
            }
            QuietSection("Support", 4){
                QuietRow("Help","FAQ and contact", onClick=onNavigateToHelp)
                QuietRow("About OpenChat","Version 1.0.0", onClick=onNavigateToAbout)
            }
            HorizontalDivider(color=DividerInk.copy(alpha=0.35f), thickness=0.6.dp, modifier=Modifier.padding(top=8.dp))
            // Disabled while the Firebase sign-out is in flight so a double tap
            // cannot fire two sign-out requests.
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = !uiState.isSigningOut) { viewModel.onEvent(SettingsEvent.SignOut) }
                    .padding(horizontal=16.dp, vertical=16.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                Icon(Icons.Default.Logout,null, Modifier.size(18.dp), tint=MaterialTheme.colorScheme.error); Spacer(Modifier.width(12.dp))
                Text(
                    if(uiState.isSigningOut) "Signing out..." else "Sign out",
                    style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium),
                    color=MaterialTheme.colorScheme.error
                )
            }
            Text("OpenChat  •  quiet messaging", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f), modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=18.dp))
            if(uiState.showFontSizeDialog) FontSizeDialog(currentSize=uiState.fontSize, onSizeSelected={viewModel.onEvent(SettingsEvent.SetFontSize(it))}, onDismiss={viewModel.onEvent(SettingsEvent.DismissFontSizeDialog)})
            if(uiState.showThemeDialog) ThemeDialog(currentMode=uiState.themeMode, onSelect={viewModel.onEvent(SettingsEvent.SetThemeMode(it))}, onDismiss={viewModel.onEvent(SettingsEvent.DismissThemeDialog)})
        }
    }
}
/**
 * Settings section. [index] drives a staggered fade-up so the list cascades in on
 * entry rather than appearing all at once, which reads as much faster on a long
 * settings page.
 */
@Composable private fun QuietSection(title:String, index:Int = 0, content:@Composable ColumnScope.()->Unit){
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(Motion.MEDIUM, delayMillis = index * 45, easing = Motion.Decelerate)) +
                slideInVertically(tween(Motion.SLOW, delayMillis = index * 45, easing = Motion.Decelerate)) { it / 12 }
    ) {
    Text(title, style=MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.Medium, letterSpacing=1.2.sp), color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=16.dp, vertical=8.dp))
    Column(content=content)
    Spacer(Modifier.height(16.dp))
    }
}
/**
 * A tappable settings row. The chevron nudges right on press and the row scales
 * very slightly, which makes the whole page feel responsive without a ripple.
 */
@Composable private fun QuietRow(title:String, subtitle:String?, onClick:()->Unit){
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val chevronOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current){ 3.dp.toPx() }
    val chevronOffset by animateFloatAsState(
        targetValue = if(pressed) chevronOffsetPx else 0f,
        animationSpec = tween(Motion.FAST, easing = Motion.Decelerate),
        label = "chevronNudge"
    )
    Row(
        Modifier.fillMaxWidth()
            .clickable(interactionSource=interaction, indication=null, onClick=onClick)
            .graphicsLayer { scaleX = if(pressed) 0.99f else 1f; scaleY = if(pressed) 0.99f else 1f }
            .padding(horizontal=16.dp, vertical=13.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Column(Modifier.weight(1f)){ Text(title, style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Normal)); if(subtitle!=null) Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(subtitle?.takeIf{it.length<12} ?: "", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Default.ChevronRight,null, modifier=Modifier.size(16.dp).padding(start=8.dp).graphicsLayer{ translationX = chevronOffset }, tint=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
    }
    HorizontalDivider(color=DividerInk.copy(alpha=0.22f), thickness=0.5.dp, modifier=Modifier.padding(start=16.dp))
}
@Composable private fun FontSizeDialog(currentSize:FontSize,onSizeSelected:(FontSize)->Unit,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss, title={Text("Font size")}, text={ Column{ FontSize.entries.forEach{ s-> Row(Modifier.fillMaxWidth().clickable{onSizeSelected(s)}.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically){ RadioButton(selected=s==currentSize, onClick={onSizeSelected(s)}); Text(when(s){FontSize.SMALL->"Small"; FontSize.MEDIUM->"Medium"; FontSize.LARGE->"Large"; FontSize.EXTRA_LARGE->"Extra large"}, Modifier.padding(start=8.dp)) } } } }, confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
@Composable private fun ThemeDialog(currentMode:com.openchat.app.presentation.theme.ThemeMode,onSelect:(com.openchat.app.presentation.theme.ThemeMode)->Unit,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss, title={Text("Theme")}, text={ Column{ com.openchat.app.presentation.theme.ThemeMode.entries.forEach{ m-> Row(Modifier.fillMaxWidth().clickable{onSelect(m)}.padding(vertical=10.dp), verticalAlignment=Alignment.CenterVertically){ RadioButton(selected=m==currentMode, onClick={onSelect(m)}); Text(when(m){com.openchat.app.presentation.theme.ThemeMode.LIGHT->"Light"; com.openchat.app.presentation.theme.ThemeMode.DARK->"Dark"; com.openchat.app.presentation.theme.ThemeMode.SYSTEM->"System"}, Modifier.padding(start=8.dp)) } } } }, confirmButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

