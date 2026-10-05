package com.openchat.app.presentation.screens.group
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.openchat.app.domain.model.User
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CreateGroupScreen(onNavigateBack:()->Unit,onGroupCreated:(String)->Unit,viewModel:CreateGroupViewModel=hiltViewModel()){
    val uiState by viewModel.uiState.collectAsState()
    var groupName by remember{mutableStateOf("")}
    var groupDesc by remember{mutableStateOf("")}
    var showDialog by remember{mutableStateOf(false)}
    val snack=remember{SnackbarHostState()}
    LaunchedEffect(Unit){ viewModel.effect.collect{ e-> when(e){ is CreateGroupEffect.NavigateToChat-> onGroupCreated(e.chatId); is CreateGroupEffect.ShowError-> snack.showSnackbar(e.message) } } }
    val canCreate = uiState.selectedContacts.isNotEmpty() && groupName.isNotBlank()
    Scaffold(topBar={
        TopAppBar(title={Column{
            Text(if(uiState.selectedContacts.isEmpty()) "New Group" else "${uiState.selectedContacts.size} selected", style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold))
            if(uiState.selectedContacts.isNotEmpty()) Text("${uiState.selectedContacts.size} members", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}, navigationIcon={IconButton(onClick=onNavigateBack){Icon(Icons.Default.ArrowBack,null)}}, actions={ if(uiState.selectedContacts.isNotEmpty()) FilledTonalButton(onClick={showDialog=true}, contentPadding=PaddingValues(horizontal=16.dp, vertical=0.dp), modifier=Modifier.padding(end=8.dp)){ Text("Next")} })
    }, snackbarHost={SnackbarHost(snack)}, floatingActionButton={ if(uiState.selectedContacts.isNotEmpty()) ExtendedFloatingActionButton(onClick={showDialog=true}, icon={Icon(Icons.Default.Check,null, Modifier.size(18.dp))}, text={Text("Create (${uiState.selectedContacts.size})")}, containerColor=MaterialTheme.colorScheme.primary, contentColor=MaterialTheme.colorScheme.onPrimary) }){ padding->
        Column(Modifier.fillMaxSize().padding(padding)){
            Surface(shape=CircleShape, color=MaterialTheme.colorScheme.surfaceVariant, modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=12.dp).size(72.dp).clickable{}){ Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Icon(Icons.Default.Group,null, Modifier.size(32.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)} }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value=uiState.searchQuery, onValueChange={viewModel.onEvent(CreateGroupEvent.SearchQueryChanged(it))}, placeholder={Text("Search contacts", style=MaterialTheme.typography.bodyMedium)}, leadingIcon={Icon(Icons.Default.Search,null, Modifier.size(18.dp))}, trailingIcon={ if(uiState.searchQuery.isNotEmpty()) IconButton(onClick={viewModel.onEvent(CreateGroupEvent.SearchQueryChanged(""))}){Icon(Icons.Default.Close,null, Modifier.size(18.dp))}}, singleLine=true, shape=RoundedCornerShape(14.dp), modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp), colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant.copy(0.6f), focusedBorderColor=MaterialTheme.colorScheme.primary))
            if(uiState.selectedContacts.isNotEmpty()){
                LazyRow(contentPadding=PaddingValues(16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()){
                    items(uiState.selectedContacts){ c-> InputChip(selected=true, onClick={viewModel.onEvent(CreateGroupEvent.ToggleContact(c))}, label={Text(c.displayName.take(10), maxLines=1)}, avatar={ if(c.photoUrl!=null) AsyncImage(model=c.photoUrl, contentDescription=null, modifier=Modifier.size(24.dp).clip(CircleShape)) else Icon(Icons.Default.Person,null, Modifier.size(16.dp)) }, trailingIcon={Icon(Icons.Default.Close,null, Modifier.size(14.dp))}) }
                }
            } else { Spacer(Modifier.height(12.dp)) }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
            Text("${uiState.filteredContacts.size} contacts", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=16.dp, vertical=8.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(bottom=80.dp)){
                items(uiState.filteredContacts){ contact-> SelectableContact(contact, uiState.selectedContacts.contains(contact)){ viewModel.onEvent(CreateGroupEvent.ToggleContact(contact)) } }
            }
        }
    }
    if(showDialog){
        AlertDialog(onDismissRequest={showDialog=false}, title={Text("New Group", style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold))}, text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                Surface(shape=CircleShape, color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.size(56.dp).align(Alignment.CenterHorizontally)){ Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Icon(Icons.Default.Group,null, Modifier.size(28.dp)) } }
                OutlinedTextField(value=groupName, onValueChange={groupName=it}, label={Text("Group name *")}, placeholder={Text("e.g. Design Team")}, singleLine=true, shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=groupDesc, onValueChange={groupDesc=it}, label={Text("Description (optional)")}, placeholder={Text("What is this group about?")}, shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth(), maxLines=2)
                Text("${uiState.selectedContacts.size} members will be added", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton={ Button(onClick={ if(groupName.isNotBlank()){ viewModel.onEvent(CreateGroupEvent.CreateGroup(groupName)); showDialog=false } }, enabled=groupName.isNotBlank(), shape=RoundedCornerShape(24.dp)){ Text("Create")} }, dismissButton={ TextButton(onClick={showDialog=false}){Text("Cancel")} })
    }
}
@Composable private fun SelectableContact(contact:User, isSelected:Boolean, onClick:()->Unit){
    Surface(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick), color=if(isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) else MaterialTheme.colorScheme.surface){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment=Alignment.Center){
                if(contact.photoUrl!=null) AsyncImage(model=contact.photoUrl, contentDescription=null, modifier=Modifier.fillMaxSize().clip(CircleShape)) else Icon(Icons.Default.Person,null, Modifier.size(22.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){ Text(contact.displayName, style=MaterialTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Medium), maxLines=1, overflow=TextOverflow.Ellipsis); contact.username?.let{ Text("@$it", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)} }
            Checkbox(checked=isSelected, onCheckedChange={onClick()})
        }
    }
}
