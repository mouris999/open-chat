package com.openchat.app.presentation.screens.home
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.core.extensions.formatAsRelativeTime
import com.openchat.app.data.model.Chat
import com.openchat.app.data.model.ChatType
import com.openchat.app.presentation.components.ConversationPulse
import com.openchat.app.presentation.theme.Accent
import com.openchat.app.presentation.theme.DividerInk
import com.openchat.app.presentation.theme.Motion
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(onNavigateToChat:(String)->Unit,onNavigateToContacts:()->Unit,onNavigateToCreateGroup:()->Unit,onNavigateToStories:()->Unit,onNavigateToSettings:()->Unit,onNavigateToProfile:()->Unit,onNavigateToCall:(String,Boolean)->Unit={_,_->},onIncomingCall:((String,String,Boolean)->Unit)?=null,onSignOut:()->Unit,onNavigateToLockPrivate:()->Unit={},viewModel:HomeViewModel=hiltViewModel()){
    val uiState by viewModel.uiState.collectAsState()
    val drawerState=rememberDrawerState(DrawerValue.Closed); val scope=rememberCoroutineScope()
    var selectedTab by remember{mutableStateOf(0)}
    var searchQuery by remember{mutableStateOf("")}
    var showFabMenu by remember{mutableStateOf(false)}
    var showSearch by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){ viewModel.onEvent(HomeEvent.RefreshLockState) }
    val context = LocalContext.current
    // ShowError is handled explicitly: the old `else->{}` silently swallowed every
    // failure the ViewModel reports (failed pin, failed sign-out, load errors).
    LaunchedEffect(Unit){
        viewModel.effect.collect{ e-> when(e){
            is HomeEffect.IncomingCall-> (onIncomingCall?.invoke(e.callerId,e.callId,e.isVideo) ?: onNavigateToCall(e.callerId,e.isVideo))
            is HomeEffect.NavigateToChat-> onNavigateToChat(e.chatId)
            // Only fires after the ViewModel has actually terminated the Firebase
            // session, so signing out from the drawer cannot bounce back to Home.
            is HomeEffect.NavigateToAuth-> onSignOut()
            is HomeEffect.ShowError-> Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
        } }
    }
    ModalNavigationDrawer(drawerState=drawerState, drawerContent={ ModalDrawerSheet(drawerContainerColor=MaterialTheme.colorScheme.surface, content={ DrawerContent(onNavigateToSettings={scope.launch{drawerState.close()}; onNavigateToSettings()}, onNavigateToProfile={scope.launch{drawerState.close()}; onNavigateToProfile()}, onSignOut={
                    scope.launch{drawerState.close()}
                    // Dispatch the event so the ViewModel really terminates the session;
                    // navigating directly left the user signed in and bounced straight
                    // back to Home on the next launch.
                    viewModel.onEvent(HomeEvent.SignOut)
                }) }) }){
        Scaffold(containerColor=MaterialTheme.colorScheme.background, topBar={
            Column{
                TopAppBar(title={
                    Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()){
                        Text("OpenChat", style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold, letterSpacing=(-0.5).sp), modifier=Modifier.weight(1f))
                        // Private-mode chip slides in so the mode change is obvious.
                        AnimatedVisibility(visible=uiState.isPrivateMode, enter=Motion.popIn() + expandHorizontally(tween(Motion.SLOW, easing = Motion.Decelerate)), exit=shrinkHorizontally(tween(Motion.MEDIUM, easing = Motion.Accelerate)) + fadeOut(tween(Motion.FAST))){
                            Surface(shape=RoundedCornerShape(20.dp), color=Accent.copy(alpha=0.18f)){ Row(Modifier.padding(horizontal=8.dp, vertical=4.dp), verticalAlignment=Alignment.CenterVertically){ Icon(Icons.Default.Lock,null, Modifier.size(12.dp), tint=Accent); Spacer(Modifier.width(4.dp)); Text("Private", style=MaterialTheme.typography.labelSmall, color=Accent) } }
                        }
                    }
                }, actions={
                    IconButton(onClick={ showSearch=!showSearch }){ Icon(Icons.Default.Search,null, Modifier.size(20.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick={ if(uiState.isPrivateMode) viewModel.onEvent(HomeEvent.SwitchToStandardMode) else onNavigateToLockPrivate() }){ Icon(if(uiState.isPrivateMode) Icons.Default.Lock else Icons.Default.LockOpen,null, Modifier.size(20.dp), tint=if(uiState.isPrivateMode) Accent else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick={scope.launch{drawerState.open()}}){ Icon(Icons.Default.MoreVert,null, Modifier.size(20.dp)) }
                }, colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
                // Search field expands in place instead of popping, so the top bar
                // never jumps when the user taps the magnifier.
                AnimatedVisibility(
                    visible = showSearch,
                    enter = expandVertically(tween(Motion.SLOW, easing = Motion.Decelerate)) + fadeIn(tween(Motion.MEDIUM)),
                    exit = shrinkVertically(tween(Motion.MEDIUM, easing = Motion.Accelerate)) + fadeOut(tween(Motion.FAST))
                ) {
                    OutlinedTextField(value=searchQuery, onValueChange={searchQuery=it}, placeholder={Text("Search conversations", style=MaterialTheme.typography.bodySmall)}, leadingIcon={Icon(Icons.Default.Search,null, Modifier.size(16.dp))}, trailingIcon={ if(searchQuery.isNotEmpty()) IconButton(onClick={searchQuery=""}){Icon(Icons.Default.Close,null, Modifier.size(16.dp))} else null }, singleLine=true, shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=8.dp), colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=DividerInk, focusedBorderColor=Accent, unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f), focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)))
                }
                HorizontalDivider(color=DividerInk.copy(alpha=0.6f), thickness=0.6.dp)
            }
        }, bottomBar={
            Column{
                HorizontalDivider(color=DividerInk.copy(alpha=0.5f), thickness=0.6.dp)
                NavigationBar(containerColor=MaterialTheme.colorScheme.background, tonalElevation=0.dp){
                        // "Stories" and "Settings" leave the screen immediately on tap, so
                    // they deliberately show no selected indicator - otherwise the pill
                    // would flash and animate for a state the user never occupies.
                    NavigationBarItem(icon={Icon(Icons.Default.Chat,null, Modifier.size(20.dp))}, label={Text("Chat", style=MaterialTheme.typography.labelSmall)}, selected=selectedTab==0, onClick={selectedTab=0}, colors=NavigationBarItemDefaults.colors(indicatorColor=Accent.copy(alpha=0.18f), selectedIconColor=Accent, selectedTextColor=Accent))
                    NavigationBarItem(icon={Icon(Icons.Default.AutoAwesome,null, Modifier.size(20.dp))}, label={Text("Stories", style=MaterialTheme.typography.labelSmall)}, selected=false, onClick=onNavigateToStories, colors=NavigationBarItemDefaults.colors(selectedIconColor=Accent))
                    NavigationBarItem(icon={Icon(Icons.Default.Group,null, Modifier.size(20.dp))}, label={Text("Groups", style=MaterialTheme.typography.labelSmall)}, selected=selectedTab==2, onClick={selectedTab=2}, colors=NavigationBarItemDefaults.colors(indicatorColor=Accent.copy(alpha=0.18f), selectedIconColor=Accent, selectedTextColor=Accent))
                    NavigationBarItem(icon={Icon(Icons.Default.Settings,null, Modifier.size(20.dp))}, label={Text("Settings", style=MaterialTheme.typography.labelSmall)}, selected=false, onClick=onNavigateToSettings)
                }
            }
        }, floatingActionButton={
            if(selectedTab==0 || selectedTab==2){
                Column(horizontalAlignment=Alignment.End){
                    // The FAB menu grows out of the button and each row cascades in,
                    // so the menu reads as attached to the FAB rather than teleporting.
                    AnimatedVisibility(
                        visible = showFabMenu && selectedTab==0,
                        enter = Motion.popIn() + expandVertically(tween(Motion.SLOW, easing = Motion.Decelerate)),
                        exit = Motion.popOut() + shrinkVertically(tween(Motion.MEDIUM, easing = Motion.Accelerate))
                    ) {
                        Surface(shape=RoundedCornerShape(14.dp), color=MaterialTheme.colorScheme.surfaceVariant, shadowElevation=4.dp, modifier=Modifier.padding(bottom=12.dp)){
                            Column(Modifier.padding(8.dp)){
                                FabMenuRow("New chat", Icons.Default.Person, 0){ showFabMenu=false; onNavigateToContacts() }
                                FabMenuRow("New group", Icons.Default.Group, 1){ showFabMenu=false; onNavigateToCreateGroup() }
                                FabMenuRow("New story", Icons.Default.AutoAwesome, 2){ showFabMenu=false; onNavigateToStories() }
                            }
                        }
                    }
                    // Rotating the + glyph 90deg reads as a state change far better
                    // than hard-swapping two different icons instantly.
                    val fabRotation by animateFloatAsState(
                        targetValue = if(showFabMenu && selectedTab==0) 90f else 0f,
                        animationSpec = tween(Motion.MEDIUM, easing = Motion.Emphasized),
                        label = "fabRotation"
                    )
                    val fabScale by animateFloatAsState(
                        targetValue = if(showFabMenu && selectedTab==0) 0.9f else 1f,
                        animationSpec = Motion.bouncySpec(),
                        label = "fabScale"
                    )
                    FloatingActionButton(
                        onClick={ if(selectedTab==2) onNavigateToCreateGroup() else showFabMenu=!showFabMenu },
                        containerColor=Accent,
                        contentColor=MaterialTheme.colorScheme.background,
                        shape=RoundedCornerShape(16.dp),
                        elevation=FloatingActionButtonDefaults.elevation(2.dp),
                        modifier=Modifier.scale(fabScale)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(22.dp).graphicsLayer{ rotationZ = fabRotation })
                    }
                }
            }
        }){ padding ->
            val filtered = if(searchQuery.isBlank()) uiState.sortedChats else uiState.sortedChats.filter{ it.title?.contains(searchQuery,true)==true || it.lastMessage?.content?.contains(searchQuery,true)==true }
            val groupChats = filtered.filter{ it.type==ChatType.GROUP }
            Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)){
                if(selectedTab==0 || selectedTab==2){
                    // Section header crossfades with the tab so the label never
                    // snaps between "Recent" and "Groups".
                    AnimatedContent(
                        targetState = if(selectedTab==0) "Recent" else "Groups",
                        transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "sectionHeader"
                    ) { sectionLabel ->
                        Text(sectionLabel, style=MaterialTheme.typography.labelMedium.copy(fontWeight=FontWeight.Medium), color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=16.dp, vertical=10.dp))
                    }
                }
                // One shared list state drives both tabs so switching them keeps
                // scroll position and lets rows animate instead of recreating.
                val listState = rememberLazyListState()
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = Motion.contentSwap(),
                    modifier = Modifier.fillMaxSize(),
                    label = "homeTabs"
                ) { tab ->
                    val rows = if(tab==2) groupChats else filtered
                    Box(Modifier.fillMaxSize()){
                        LazyColumn(Modifier.fillMaxSize(), state=listState, contentPadding=PaddingValues(bottom=88.dp)){
                            items(rows, key={it.id}){ chat->
                                ConversationRow(
                                    chat = chat,
                                    isPinned = uiState.pinnedChatIds.contains(chat.id),
                                    onClick = { onNavigateToChat(chat.id) },
                                    onLongClick = { viewModel.onEvent(HomeEvent.TogglePinChat(chat.id)) }
                                )
                                HorizontalDivider(color=DividerInk.copy(alpha=0.35f), thickness=0.5.dp, modifier=Modifier.padding(start=72.dp))
                            }
                        }
                        if(rows.isEmpty()){
                            QuietEmpty(
                                isSearch = tab==0 && searchQuery.isNotEmpty(),
                                onAction = if(tab==2) onNavigateToCreateGroup else onNavigateToContacts,
                                isGroup = tab==2
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * One row in the conversation list. Uses an interaction source rather than the
 * default ripple so the row can dim and scale subtly on press, which makes
 * long lists feel considerably more responsive on tap.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun ConversationRow(chat:Chat, isPinned:Boolean, onClick:()->Unit, onLongClick:()->Unit){
    val unread = chat.unreadCount.values.sum()
    val time = chat.lastMessage?.timestamp?.formatAsRelativeTime() ?: ""
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Pin tint fades rather than snapping on/off when a chat is pinned or unpinned.
    val rowColor by animateColorAsState(
        targetValue = if(isPinned) Accent.copy(alpha=0.06f) else MaterialTheme.colorScheme.background,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.Decelerate),
        label = "rowBg"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer { scaleX = if(pressed) 0.985f else 1f; scaleY = if(pressed) 0.985f else 1f }
            .padding(horizontal=16.dp, vertical=13.dp)
            .background(rowColor),
        verticalAlignment=Alignment.CenterVertically
    ){
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment=Alignment.Center){
            Icon(if(chat.type==ChatType.GROUP) Icons.Default.Group else Icons.Default.Person, null, Modifier.size(20.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(chat.title?:"Unknown", style=MaterialTheme.typography.bodyMedium.copy(fontWeight=if(unread>0) FontWeight.SemiBold else FontWeight.Medium), maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.weight(1f))
                // Pin badge scales in so it doesn't shift the title when applied.
                AnimatedVisibility(visible=isPinned, enter=Motion.popIn(), exit=Motion.popOut()){
                    Icon(Icons.Default.PushPin,null, modifier=Modifier.size(13.dp).padding(start=6.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(chat.lastMessage?.content?.take(38)?:"No messages yet", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1, overflow=TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment=Alignment.End){
            if(time.isNotEmpty()) Text(time, style=MaterialTheme.typography.labelSmall, color=if(unread>0) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            AnimatedVisibility(visible=unread>0, enter=Motion.popIn(), exit=Motion.popOut()){
                UnreadBadge(unread)
            }
        }
    }
}

/**
 * Unread count pill. Sizing is driven by the digit count so the row never reflows
 * when a count crosses from 2 to 3 digits, and the pill grows in with a spring so
 * an arriving message is noticed without being loud.
 */
@Composable private fun UnreadBadge(unread:Int){
    val label = if(unread>99) "99+" else unread.toString()
    val widthDp = when {
        label.length >= 3 -> 28.dp
        label.length == 2 -> 24.dp
        else -> 20.dp
    }
    val appear = remember(unread) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(unread){ appear.snapTo(0f); appear.animateTo(1f, Motion.bouncySpec()) }
    Box(
        Modifier.width(widthDp).height(20.dp)
            .graphicsLayer {
                val s = appear.value
                scaleX = s; scaleY = s
                alpha = s.coerceIn(0f,1f)
            }
            .clip(CircleShape).background(Accent),
        contentAlignment=Alignment.Center
    ){
        Text(label, style=MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.Bold), color=MaterialTheme.colorScheme.background, maxLines=1)
    }
}

/** A single row inside the FAB's mini-menu, staggered in by [index]. */
@Composable private fun FabMenuRow(label:String, icon:androidx.compose.ui.graphics.vector.ImageVector, index:Int, onClick:()->Unit){
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(Motion.MEDIUM, delayMillis = index * 45, easing = Motion.Decelerate)) +
            expandVertically(tween(Motion.SLOW, delayMillis = index * 45, easing = Motion.Decelerate))
    ) {
        TextButton(onClick=onClick){
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style=MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable private fun QuietEmpty(isSearch:Boolean, onAction:()->Unit, isGroup:Boolean=false){
    // Whole empty state fades up once on appear rather than snapping in.
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(Motion.SLOW, delayMillis = 120, easing = Motion.Decelerate)) +
            slideInVertically(tween(Motion.SLOW, delayMillis = 120, easing = Motion.Decelerate)) { it / 10 }
    ) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center){
        ConversationPulse(Modifier.padding(bottom=18.dp))
        Text(if(isSearch) "No results" else if(isGroup) "No groups yet" else "No conversations yet", style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text(if(isSearch) "Try a different search" else if(isGroup) "Create a group to gather your people." else "Start with someone you know.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!isSearch){
            Spacer(Modifier.height(20.dp))
            Button(onClick=onAction, colors=ButtonDefaults.buttonColors(containerColor=Accent, contentColor=MaterialTheme.colorScheme.background), shape=RoundedCornerShape(24.dp), contentPadding=PaddingValues(horizontal=22.dp, vertical=10.dp)){ Text(if(isGroup) "Create group" else "Start a chat", style=MaterialTheme.typography.labelMedium) }
        }
    }
    }
}
@Composable private fun DrawerContent(onNavigateToSettings:()->Unit,onNavigateToProfile:()->Unit,onSignOut:()->Unit){
    Column(Modifier.padding(16.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface)){
        Row(Modifier.padding(vertical=14.dp), verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(42.dp).clip(CircleShape).background(Accent), contentAlignment=Alignment.Center){ Text("O", color=MaterialTheme.colorScheme.background, style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold)) }
            Spacer(Modifier.width(12.dp)); Column{ Text("OpenChat", style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.Bold)); Text("Quiet messaging", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalDivider(color=DividerInk.copy(alpha=0.4f), thickness=0.5.dp, modifier=Modifier.padding(vertical=8.dp))
        NavigationDrawerItem(icon={Icon(Icons.Default.Person,null, Modifier.size(18.dp))}, label={Text("Profile", style=MaterialTheme.typography.bodyMedium)}, selected=false, onClick=onNavigateToProfile, colors=NavigationDrawerItemDefaults.colors(unselectedContainerColor=MaterialTheme.colorScheme.surface))
        NavigationDrawerItem(icon={Icon(Icons.Default.Settings,null, Modifier.size(18.dp))}, label={Text("Settings", style=MaterialTheme.typography.bodyMedium)}, selected=false, onClick=onNavigateToSettings)
        Spacer(Modifier.weight(1f)); HorizontalDivider(color=DividerInk.copy(alpha=0.4f), thickness=0.5.dp, modifier=Modifier.padding(vertical=8.dp))
        NavigationDrawerItem(icon={Icon(Icons.Default.ExitToApp,null, Modifier.size(18.dp))}, label={Text("Sign out", style=MaterialTheme.typography.bodyMedium)}, selected=false, onClick=onSignOut)
    }
}

