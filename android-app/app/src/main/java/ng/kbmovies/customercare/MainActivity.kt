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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ng.kbmovies.customercare.data.*
import java.io.File

private val Green=Color(0xFF075E54);private val Lime=Color(0xFF16A05D);private val ChatBg=Color(0xFFEFEAE2)

data class UiState(val loading:Boolean=false,val agent:Agent?=null,val conversations:List<Conversation> = emptyList(),val selected:Conversation?=null,val messages:List<Message> = emptyList(),val error:String?=null)

class CareViewModel(private val repo:CareRepository):ViewModel(){
    private val _ui=MutableStateFlow(UiState());val ui=_ui.asStateFlow();private var poll:Job?=null
    fun restore(){if(repo.signedIn())viewModelScope.launch{runCatching{repo.me()}.onSuccess{_ui.value=_ui.value.copy(agent=it);refresh()}.onFailure{repo.logout()}}}
    fun login(u:String,p:String)=viewModelScope.launch{_ui.value=_ui.value.copy(loading=true,error=null);runCatching{repo.login(u,p)}.onSuccess{_ui.value=_ui.value.copy(loading=false,agent=it);refresh()}.onFailure{_ui.value=_ui.value.copy(loading=false,error=it.message)}}
    fun logout(){poll?.cancel();repo.logout();_ui.value=UiState()}
    fun available(v:Boolean)=viewModelScope.launch{runCatching{repo.setAvailable(v)}.onSuccess{_ui.value=_ui.value.copy(agent=it)}.onFailure{fail(it)}}
    fun refresh()=viewModelScope.launch{runCatching{repo.conversations()}.onSuccess{_ui.value=_ui.value.copy(conversations=it,error=null)}.onFailure{fail(it)}}
    fun open(c:Conversation){_ui.value=_ui.value.copy(selected=c,messages=emptyList());poll?.cancel();poll=viewModelScope.launch{while(isActive){loadMessages();delay(2000)}}}
    fun back(){poll?.cancel();_ui.value=_ui.value.copy(selected=null,messages=emptyList());refresh()}
    private suspend fun loadMessages(){val c=_ui.value.selected?:return;val after=_ui.value.messages.maxOfOrNull{it.id}?:0;runCatching{repo.messages(c.id,after)}.onSuccess{if(it.messages.isNotEmpty())_ui.value=_ui.value.copy(messages=_ui.value.messages+it.messages)}}
    fun send(text:String)=viewModelScope.launch{val id=_ui.value.selected?.id?:return@launch;runCatching{repo.sendText(id,text)}.onSuccess{_ui.value=_ui.value.copy(messages=_ui.value.messages+it)}.onFailure{fail(it)}}
    fun image(uri:Uri,mime:String)=viewModelScope.launch{val id=_ui.value.selected?.id?:return@launch;runCatching{repo.sendMedia(id,uri,"image",mime)}.onSuccess{_ui.value=_ui.value.copy(messages=_ui.value.messages+it)}.onFailure{fail(it)}}
    fun audio(file:File)=viewModelScope.launch{val id=_ui.value.selected?.id?:return@launch;runCatching{repo.sendAudio(id,file)}.onSuccess{_ui.value=_ui.value.copy(messages=_ui.value.messages+it)}.onFailure{fail(it)}}
    private fun fail(t:Throwable){_ui.value=_ui.value.copy(error=t.message?:"Request failed")}
}

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(android.os.Build.VERSION.SDK_INT>=33&&ActivityCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
        val repo=(application as CareApp).repository;val vm=ViewModelProvider(this,object:ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T=CareViewModel(repo) as T})[CareViewModel::class.java];vm.restore();setContent{MaterialTheme(colorScheme=lightColorScheme(primary=Green,secondary=Lime)){App(vm)}}}
}

@Composable fun App(vm:CareViewModel){val state by vm.ui.collectAsStateWithLifecycle();Surface(Modifier.fillMaxSize()){when{state.agent==null->Login(state,vm);state.selected==null->Inbox(state,vm);else->Chat(state,vm)}}}

@Composable fun Login(s:UiState,vm:CareViewModel){var u by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(78.dp).background(Green,CircleShape),contentAlignment=Alignment.Center){Text("KB",color=Color.White,fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineMedium)};Spacer(Modifier.height(18.dp));Text("KB Movies Customer Care",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Sign in with your customer-care account",color=Color.Gray);Spacer(Modifier.height(24.dp));OutlinedTextField(u,{u=it},label={Text("Username or email")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(p,{p=it},label={Text("Password")},singleLine=true,modifier=Modifier.fillMaxWidth());s.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button({vm.login(u,p)},enabled=!s.loading&&u.isNotBlank()&&p.isNotBlank(),modifier=Modifier.fillMaxWidth().padding(top=14.dp)){Text(if(s.loading)"Signing in…" else "Sign in")}}}

@Composable fun Inbox(s:UiState,vm:CareViewModel){Column(Modifier.fillMaxSize()){Row(Modifier.fillMaxWidth().background(Green).padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Customer Care",color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text(s.agent?.name.orEmpty(),color=Color.White.copy(.8f))};Column(horizontalAlignment=Alignment.End){Text(if(s.agent?.available==true)"Available" else "Offline",color=Color.White);Switch(checked=s.agent?.available==true,onCheckedChange=vm::available)}};Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text("Chats",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick=vm::refresh){Text("Refresh")};TextButton(onClick=vm::logout){Text("Logout")}};s.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(horizontal=14.dp))};LazyColumn{items(s.conversations,key={it.id}){c->Row(Modifier.fillMaxWidth().clickable{vm.open(c)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(52.dp).background(Color(0xFFD5F8DE),CircleShape),contentAlignment=Alignment.Center){Text(c.customerName.take(1).uppercase(),color=Green,fontWeight=FontWeight.Bold)};Column(Modifier.weight(1f).padding(start=12.dp)){Text(c.customerName,fontWeight=FontWeight.Bold);Text(c.lastMessage?:"New conversation",maxLines=1,color=Color.Gray)};if(c.unread>0)Badge{Text(c.unread.toString())}};HorizontalDivider()}}}}

@Composable fun Chat(s:UiState,vm:CareViewModel){val context=LocalContext.current;var text by remember{mutableStateOf("")};var recorder by remember{mutableStateOf<MediaRecorder?>(null)};var recording by remember{mutableStateOf(false)};var audioFile by remember{mutableStateOf<File?>(null)};val image=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)vm.image(uri,context.contentResolver.getType(uri)?:"image/jpeg")};val mic=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it){val f=File(context.cacheDir,"voice-${System.currentTimeMillis()}.m4a");audioFile=f;recorder=MediaRecorder(context).apply{setAudioSource(MediaRecorder.AudioSource.MIC);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setOutputFile(f.absolutePath);prepare();start()};recording=true}};Column(Modifier.fillMaxSize().background(ChatBg)){Row(Modifier.fillMaxWidth().background(Green).padding(10.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=vm::back){Text("‹",color=Color.White,style=MaterialTheme.typography.headlineMedium)};Column{Text(s.selected?.customerName.orEmpty(),color=Color.White,fontWeight=FontWeight.Bold);Text("Customer",color=Color.White.copy(.75f),style=MaterialTheme.typography.bodySmall)}};val list=rememberLazyListState();LaunchedEffect(s.messages.size){if(s.messages.isNotEmpty())list.animateScrollToItem(s.messages.lastIndex)};LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(10.dp),state=list){items(s.messages,key={it.id}){m->MessageBubble(m){url->context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}};s.error?.let{Text(it,color=Color.Red,modifier=Modifier.padding(8.dp))};Row(Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(8.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={image.launch("image/*")}){Text("📎")};OutlinedTextField(text,{text=it},placeholder={Text("Type a message")},modifier=Modifier.weight(1f),maxLines=4);TextButton(onClick={if(recording){recorder?.stop();recorder?.release();recorder=null;recording=false;audioFile?.let(vm::audio)}else mic.launch(Manifest.permission.RECORD_AUDIO)}){Text(if(recording)"■" else "🎙")};Button(onClick={if(text.isNotBlank()){vm.send(text.trim());text=""}},contentPadding=PaddingValues(11.dp)){Text("➤")}}}}

@Composable fun MessageBubble(m:Message,open:(String)->Unit){val mine=m.senderType=="agent";Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){Column(Modifier.padding(vertical=3.dp).background(if(mine)Color(0xFFD9FDD3)else Color.White,RoundedCornerShape(10.dp)).padding(9.dp).widthIn(max=280.dp)){when(m.messageType){"image"->AsyncImage(m.mediaUrl,null,Modifier.fillMaxWidth().heightIn(max=260.dp).clickable{m.mediaUrl?.let(open)});"audio"->Text("▶ Voice note",color=Green,modifier=Modifier.clickable{m.mediaUrl?.let(open)});else->Text(m.body.orEmpty())};Text(m.createdAt.takeLast(8).take(5),style=MaterialTheme.typography.labelSmall,color=Color.Gray,modifier=Modifier.align(Alignment.End))}}}
