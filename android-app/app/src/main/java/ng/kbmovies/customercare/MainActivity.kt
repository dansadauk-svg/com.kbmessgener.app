package ng.kbmovies.customercare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ng.kbmovies.customercare.data.*
import java.io.File

private val Green=Color(0xFF075E54);private val Lime=Color(0xFF16A05D);private val ChatBg=Color(0xFFEFEAE2)

data class UiState(val loading:Boolean=false,val agent:Agent?=null,val conversations:List<Conversation> = emptyList(),val selected:Conversation?=null,val messages:List<Message> = emptyList(),val peerReadId:Long=0,val error:String?=null,val transfer:String?=null,val progress:Int=0)

class CareViewModel(private val repo:CareRepository):ViewModel(){
    private val _ui=MutableStateFlow(UiState());val ui=_ui.asStateFlow();private var poll:Job?=null
    fun restore(){if(repo.signedIn())viewModelScope.launch{runCatching{repo.me()}.onSuccess{_ui.value=_ui.value.copy(agent=it);launch{runCatching{repo.registerDevice()}};refresh()}.onFailure{repo.logout()}}}
    fun login(u:String,p:String)=viewModelScope.launch{_ui.value=_ui.value.copy(loading=true,error=null);runCatching{repo.login(u,p)}.onSuccess{_ui.value=_ui.value.copy(loading=false,agent=it);refresh()}.onFailure{_ui.value=_ui.value.copy(loading=false,error=it.message)}}
    fun logout(){poll?.cancel();repo.logout();_ui.value=UiState()}
    fun available(v:Boolean)=viewModelScope.launch{runCatching{repo.setAvailable(v)}.onSuccess{_ui.value=_ui.value.copy(agent=it)}.onFailure{fail(it)}}
    fun refresh()=viewModelScope.launch{runCatching{repo.conversations()}.onSuccess{_ui.value=_ui.value.copy(conversations=it,error=null)}.onFailure{fail(it)}}
    fun open(c:Conversation){_ui.value=_ui.value.copy(selected=c,messages=emptyList(),peerReadId=0,conversations=_ui.value.conversations.map{if(it.id==c.id)it.copy(unread=0)else it});poll?.cancel();poll=viewModelScope.launch{while(isActive){loadMessages();delay(1000)}}}
    fun back(){poll?.cancel();_ui.value=_ui.value.copy(selected=null,messages=emptyList());refresh()}
    private fun merge(incoming:List<Message>){_ui.value=_ui.value.copy(messages=(_ui.value.messages+incoming).associateBy{it.id}.values.sortedBy{it.id})}
    private suspend fun loadMessages(){val c=_ui.value.selected?:return;runCatching{repo.messages(c.id,0)}.onSuccess{r->if(r.messages.isNotEmpty())merge(r.messages);_ui.value=_ui.value.copy(peerReadId=r.peerReadId)}}
    fun send(text:String)=viewModelScope.launch{val id=_ui.value.selected?.id?:return@launch;_ui.value=_ui.value.copy(transfer="Sending message…",error=null);runCatching{repo.sendText(id,text)}.onSuccess{merge(listOf(it));_ui.value=_ui.value.copy(transfer=null)}.onFailure{fail(it)}}
    fun image(uri:Uri,mime:String)=viewModelScope.launch{val id=_ui.value.selected?.id?:return@launch;_ui.value=_ui.value.copy(transfer="Sending picture…",progress=0,error=null);runCatching{repo.sendMedia(id,uri,"image",mime){p->_ui.value=_ui.value.copy(progress=p,transfer="Sending picture… $p%")}}.onSuccess{merge(listOf(it));_ui.value=_ui.value.copy(transfer=null,progress=0)}.onFailure{fail(it)}}
    fun audio(file:File)=viewModelScope.launch{val id=_ui.value.selected?.id?:return@launch;_ui.value=_ui.value.copy(transfer="Sending voice note…",progress=0,error=null);runCatching{repo.sendAudio(id,file){p->_ui.value=_ui.value.copy(progress=p,transfer="Sending voice note… $p%")}}.onSuccess{merge(listOf(it));file.delete();_ui.value=_ui.value.copy(transfer=null,progress=0)}.onFailure{fail(it)}}
    fun clearError(){_ui.value=_ui.value.copy(error=null)}
    fun reportError(message:String){_ui.value=_ui.value.copy(error=message,transfer=null,progress=0)}
    private fun fail(t:Throwable){_ui.value=_ui.value.copy(error=t.message?:"Request failed",transfer=null,progress=0)}
}

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(android.os.Build.VERSION.SDK_INT>=33&&ActivityCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
        val repo=(application as CareApp).repository;val vm=ViewModelProvider(this,object:ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=CareViewModel(repo) as T})[CareViewModel::class.java];vm.restore();setContent{MaterialTheme(colorScheme=lightColorScheme(primary=Green,secondary=Lime)){App(vm)}}}
}

@Composable fun App(vm:CareViewModel){val state by vm.ui.collectAsStateWithLifecycle();Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)){when{state.agent==null->Login(state,vm);state.selected==null->Inbox(state,vm);else->Chat(state,vm)}}}

@Composable fun Avatar(url:String?,name:String,size:Int=52){Box(Modifier.size(size.dp).background(Color(0xFFD7F8E1),CircleShape).border(1.dp,Color.White.copy(.35f),CircleShape),contentAlignment=Alignment.Center){if(!url.isNullOrBlank())AsyncImage(model=url,contentDescription="$name profile picture",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Text(name.trim().take(1).uppercase().ifBlank{"?"},color=Green,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge)}}

@Composable fun Login(s:UiState,vm:CareViewModel){var u by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().background(Color(0xFFF7FAF8)).padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Image(painterResource(R.drawable.kb_movies_logo),"KB Movies logo",Modifier.size(92.dp));Spacer(Modifier.height(18.dp));Text("KB Movies Support",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black,color=Green);Text("Customer Care",color=Color(0xFF66776E));Spacer(Modifier.height(26.dp));OutlinedTextField(u,{u=it},label={Text("Username or email")},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(10.dp));OutlinedTextField(p,{p=it},label={Text("Password")},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth());s.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=8.dp))};Button({vm.login(u,p)},enabled=!s.loading&&u.isNotBlank()&&p.isNotBlank(),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().height(54.dp).padding(top=10.dp)){Text(if(s.loading)"Signing in…" else "Sign in",fontWeight=FontWeight.Bold)}}}

@Composable fun Inbox(s:UiState,vm:CareViewModel){Column(Modifier.fillMaxSize().background(Color.White)){Row(Modifier.fillMaxWidth().height(82.dp).background(Green).padding(horizontal=14.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.kb_movies_logo),"KB Movies",Modifier.size(46.dp));Column(Modifier.weight(1f).padding(start=10.dp)){Text("KB Movies Support",color=Color.White,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium,maxLines=1);Text(s.agent?.name.orEmpty(),color=Color.White.copy(.82f),maxLines=1)};Column(horizontalAlignment=Alignment.End){Text(if(s.agent?.available==true)"● Online" else "○ Offline",color=Color.White,style=MaterialTheme.typography.labelSmall);Switch(checked=s.agent?.available==true,onCheckedChange=vm::available,colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=Lime))}};Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Text("Chats",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f));TextButton(onClick=vm::refresh){Text("Refresh")};TextButton(onClick=vm::logout){Text("Logout")}};s.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=16.dp))};if(s.conversations.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("No customer conversations yet",color=Color.Gray)}else LazyColumn(Modifier.fillMaxSize()){items(s.conversations,key={it.id}){c->Row(Modifier.fillMaxWidth().clickable{vm.open(c)}.padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){Avatar(c.customerAvatar,c.customerName,56);Column(Modifier.weight(1f).padding(start=12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text(c.customerName,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));Text(c.updatedAt.takeLast(8).take(5),color=Color.Gray,style=MaterialTheme.typography.labelSmall)};Text(c.lastMessage?:"New conversation",maxLines=1,color=Color(0xFF66776E),style=MaterialTheme.typography.bodyMedium)};if(c.unread>0)Badge(containerColor=Lime){Text(c.unread.coerceAtMost(99).toString())}};HorizontalDivider(color=Color(0xFFE9EDEA),modifier=Modifier.padding(start=82.dp))}}}}

@Composable fun LegacyChat(s:UiState,vm:CareViewModel){Text("Updating chat…")}

@Composable fun LegacyMessageBubble(m:Message,open:(String)->Unit){Text(m.body.orEmpty())}

@Composable fun Chat(s:UiState,vm:CareViewModel){
    val context=LocalContext.current;var text by remember{mutableStateOf("")};var recorder by remember{mutableStateOf<MediaRecorder?>(null)};var recording by remember{mutableStateOf(false)};var seconds by remember{mutableIntStateOf(0)};var audioFile by remember{mutableStateOf<File?>(null)};var preview by remember{mutableStateOf<String?>(null)}
    val image=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)vm.image(uri,context.contentResolver.getType(uri)?:"image/jpeg")}
    fun beginRecording(){runCatching{val f=File(context.cacheDir,"voice-${System.currentTimeMillis()}.m4a");audioFile=f;recorder=MediaRecorder(context).apply{setAudioSource(MediaRecorder.AudioSource.MIC);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setAudioSamplingRate(44100);setAudioEncodingBitRate(96000);setOutputFile(f.absolutePath);prepare();start()};seconds=0;recording=true}.onFailure{vm.reportError("Microphone could not start: ${it.message?:"check permission"}")}}
    val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)beginRecording()else vm.reportError("Microphone permission is required for voice notes")}
    LaunchedEffect(recording){while(recording){delay(1000);seconds++}}
    DisposableEffect(Unit){onDispose{runCatching{recorder?.stop()};recorder?.release();recorder=null}}
    if(preview!=null)Dialog(onDismissRequest={preview=null}){Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){AsyncImage(preview,null,Modifier.fillMaxSize().clickable{preview=null},contentScale=ContentScale.Fit);TextButton({preview=null},Modifier.align(Alignment.TopEnd).padding(12.dp)){Text("✕",color=Color.White,style=MaterialTheme.typography.headlineMedium)}}}
    Column(Modifier.fillMaxSize().background(ChatBg)){
        Row(Modifier.fillMaxWidth().background(Green).padding(horizontal=8.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=vm::back,contentPadding=PaddingValues(4.dp)){Text("‹",color=Color.White,style=MaterialTheme.typography.headlineMedium)};Avatar(s.selected?.customerAvatar,s.selected?.customerName.orEmpty(),44);Column(Modifier.weight(1f).padding(start=10.dp)){Text(s.selected?.customerName.orEmpty(),color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text("Customer • online",color=Color.White.copy(.78f),style=MaterialTheme.typography.bodySmall)};Image(painterResource(R.drawable.kb_movies_logo),"KB Movies",Modifier.size(38.dp))}
        val list=rememberLazyListState();LaunchedEffect(s.messages.size){if(s.messages.isNotEmpty())list.animateScrollToItem(s.messages.lastIndex)}
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),state=list){items(s.messages,key={it.id}){m->MessageBubble(m){preview=it}}}
        if(recording){Row(Modifier.fillMaxWidth().background(Color(0xFFFFE3E0)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text("●",color=Color.Red,fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Text("Recording ${seconds/60}:${(seconds%60).toString().padStart(2,'0')} — tap stop to send",color=Color(0xFFB3261E),fontWeight=FontWeight.Bold)}}
        s.transfer?.let{Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=12.dp,vertical=7.dp)){Text(it,color=Green,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium);if(s.progress>0)LinearProgressIndicator(progress={s.progress/100f},modifier=Modifier.fillMaxWidth())}}
        s.error?.let{Text(it,color=Color(0xFFB3261E),modifier=Modifier.background(Color(0xFFFFDAD6)).fillMaxWidth().clickable{vm.clearError()}.padding(8.dp))}
        Row(Modifier.fillMaxWidth().background(Color(0xFFF0F2F1)).padding(horizontal=8.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=CircleShape,color=Color.White){TextButton(onClick={image.launch("image/*")},enabled=s.transfer==null,contentPadding=PaddingValues(9.dp)){Text("📎")}};OutlinedTextField(text,{text=it},placeholder={Text("Message")},modifier=Modifier.weight(1f).padding(horizontal=6.dp),shape=RoundedCornerShape(26.dp),maxLines=4,enabled=s.transfer==null,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=Color.Transparent,focusedContainerColor=Color.White,unfocusedContainerColor=Color.White));Surface(shape=CircleShape,color=if(recording)Color(0xFFB3261E)else Lime){TextButton(onClick={if(recording){val ok=runCatching{recorder?.stop()}.isSuccess;recorder?.release();recorder=null;recording=false;if(ok)audioFile?.let(vm::audio)}else mic.launch(Manifest.permission.RECORD_AUDIO)},enabled=s.transfer==null,contentPadding=PaddingValues(9.dp)){Text(if(recording)"■" else "🎙",color=Color.White)}};Spacer(Modifier.width(5.dp));Button(onClick={if(text.isNotBlank()){vm.send(text.trim());text=""}},enabled=s.transfer==null,shape=CircleShape,contentPadding=PaddingValues(11.dp),modifier=Modifier.size(48.dp)){Text("➤")}}
    }
}

@Composable fun MessageBubble(m:Message,openImage:(String)->Unit){
    val mine=m.senderType=="agent";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Column(Modifier.padding(vertical=3.dp).background(if(mine)Color(0xFFD9FDD3)else Color.White,RoundedCornerShape(10.dp)).padding(9.dp).widthIn(max=300.dp)){when(m.messageType){"image"->AsyncImage(m.mediaUrl,null,Modifier.fillMaxWidth().heightIn(max=300.dp).clickable{m.mediaUrl?.let(openImage)},contentScale=ContentScale.Fit);"audio"->m.mediaUrl?.let{InlineAudio(it)}?:Text("Voice note unavailable");else->Text(m.body.orEmpty())};Row(Modifier.align(Alignment.End),verticalAlignment=Alignment.CenterVertically){Text(m.createdAt.takeLast(8).take(5),style=MaterialTheme.typography.labelSmall,color=Color.Gray);if(mine){Spacer(Modifier.width(4.dp));Text(if(m.readAt.isNullOrBlank())"✓" else "✓✓",color=if(m.readAt.isNullOrBlank())Color.Gray else Color(0xFF1687D9),fontWeight=FontWeight.Bold)}}}}}

@Composable fun InlineAudio(url:String){val context=LocalContext.current;val player=remember(url){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(url));prepare()}};DisposableEffect(player){onDispose{player.release()}};AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true;setShowNextButton(false);setShowPreviousButton(false)}},modifier=Modifier.fillMaxWidth().height(62.dp))}
