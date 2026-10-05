package com.openchat.app.presentation.screens.stories
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StoriesScreen(onNavigateBack:()->Unit,viewModel:StoriesViewModel=hiltViewModel()){
    val uiState by viewModel.uiState.collectAsState(); val ctx=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri-> uri?.let{viewModel.onEvent(StoriesEvent.UploadStory(it))}}
    var showViewer by remember{mutableStateOf(false)}; var idx by remember{mutableIntStateOf(0)}
    LaunchedEffect(Unit){ viewModel.effect.collect{ e-> when(e){ is StoriesEffect.OpenStoryViewer->{idx=e.index; showViewer=true}; StoriesEffect.StoryUploaded->Toast.makeText(ctx,"Story published",Toast.LENGTH_SHORT).show(); is StoriesEffect.ShowError->Toast.makeText(ctx,e.message,Toast.LENGTH_LONG).show(); else->{} } } }
    if(showViewer && uiState.stories.isNotEmpty()) com.openchat.app.presentation.components.StoryViewer(stories=uiState.stories, initialIndex=idx, onDismiss={showViewer=false}, onReact={id,em->viewModel.onEvent(StoriesEvent.ReactToStory(id,em))}, onReply={id,r->viewModel.onEvent(StoriesEvent.ReplyToStory(id,r))}, onViewersClick={id->viewModel.onEvent(StoriesEvent.ViewStoryViewers(id))})
    Scaffold(topBar={ TopAppBar(title={Text("Stories", style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold))}, navigationIcon={IconButton(onClick=onNavigateBack){Icon(Icons.Default.ArrowBack,null)}}) }, floatingActionButton={ FloatingActionButton(onClick={picker.launch("image/*")}, containerColor=MaterialTheme.colorScheme.primary, contentColor=MaterialTheme.colorScheme.onPrimary, shape=RoundedCornerShape(16.dp)){ Icon(Icons.Default.CameraAlt,null)} }){ padding->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surface)){
            LazyRow(contentPadding=PaddingValues(16.dp), horizontalArrangement=Arrangement.spacedBy(14.dp), modifier=Modifier.fillMaxWidth()){
                item{
                    Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.clickable{picker.launch("image/*")}){
                        Box(Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment=Alignment.Center){
                            Box(Modifier.size(62.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment=Alignment.Center){ Icon(Icons.Default.Add,null, Modifier.size(28.dp), tint=MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Spacer(Modifier.height(6.dp)); Text("Your Story", style=MaterialTheme.typography.labelSmall.copy(fontWeight=FontWeight.Medium)); Text("Add", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary)
                    }
                }
                items(uiState.stories){ s->
                    Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.clickable{viewModel.onEvent(StoriesEvent.ViewStory(s))}){
                        Box(Modifier.size(68.dp).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp).clip(CircleShape)){
                            if(s.mediaUrl!=null) AsyncImage(model=s.mediaUrl, contentDescription=null, modifier=Modifier.fillMaxSize().clip(CircleShape), contentScale=ContentScale.Crop)
                            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment=Alignment.Center){ Icon(Icons.Default.CameraAlt,null, Modifier.size(22.dp)) }
                        }
                        Spacer(Modifier.height(6.dp)); Text(s.userName.take(10), style=MaterialTheme.typography.labelSmall, maxLines=1)
                    }
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f))
            Text("Recent updates", style=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.SemiBold), color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(horizontal=16.dp, vertical=12.dp))
            if(uiState.stories.isEmpty() && !uiState.isLoading){
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment=Alignment.Center){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){
                        Surface(shape=CircleShape, color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.size(64.dp)){ Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ Icon(Icons.Default.CameraAlt,null, Modifier.size(28.dp)) } }
                        Spacer(Modifier.height(12.dp)); Text("No stories yet", style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.SemiBold)); Text("Stories disappear after 24 hours", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyVerticalGrid(columns=GridCells.Fixed(2), contentPadding=PaddingValues(16.dp), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxSize()){
                    items(uiState.stories){ story-> PremiumStoryCard(title=story.userName, imageUrl=story.mediaUrl, onClick={viewModel.onEvent(StoriesEvent.ViewStory(story))}) }
                }
            }
        }
        if(uiState.isLoading) Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center){ CircularProgressIndicator() }
    }
}
@Composable fun StoryCard(title:String,imageUrl:String?=null,isMine:Boolean=false,onClick:()->Unit)=PremiumStoryCard(title,imageUrl,onClick)
@Composable private fun PremiumStoryCard(title:String,imageUrl:String?,onClick:()->Unit){
    Card(modifier=Modifier.fillMaxWidth().height(220.dp).clickable(onClick=onClick), shape=RoundedCornerShape(20.dp), elevation=CardDefaults.cardElevation(2.dp)){
        Box(Modifier.fillMaxSize()){
            if(imageUrl!=null) AsyncImage(model=imageUrl, contentDescription=null, modifier=Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment=Alignment.Center){ Icon(Icons.Default.CameraAlt,null, Modifier.size(44.dp), tint=MaterialTheme.colorScheme.onPrimaryContainer) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.65f)), startY=260f)))
            Text(title, color=Color.White, fontWeight=FontWeight.SemiBold, fontSize=13.sp, modifier=Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
    }
}
