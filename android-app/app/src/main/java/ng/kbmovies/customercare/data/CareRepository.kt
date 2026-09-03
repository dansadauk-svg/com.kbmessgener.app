package ng.kbmovies.customercare.data

import android.content.Context
import android.net.Uri
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ng.kbmovies.customercare.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class CareRepository(private val context:Context) {
    private val prefs=context.getSharedPreferences("kbcc",Context.MODE_PRIVATE)
    private val apiClient=OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(30,TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token=prefs.getString("token","").orEmpty()
            val request=chain.request().newBuilder().apply { if(token.isNotBlank())header("Authorization","Bearer $token") }.build()
            chain.proceed(request)
        }.addInterceptor(HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BASIC}).build()
    // R2 presigned uploads must not receive the WordPress Bearer header.
    private val uploadClient=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private val api=Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(apiClient).addConverterFactory(GsonConverterFactory.create()).build().create(CareApi::class.java)

    fun signedIn()=!prefs.getString("token","").isNullOrBlank()
    suspend fun login(user:String,pass:String):Agent { val r=api.login(LoginRequest(user,pass));prefs.edit().putString("token",r.token).apply();cacheAgent(r.agent);runCatching{registerDevice()};return r.agent }
    fun logout(){prefs.edit().clear().apply()}
    fun cacheAgent(agent:Agent){prefs.edit().putLong("agent_id",agent.id).putString("agent_name",agent.name).putString("agent_avatar",agent.avatar.orEmpty()).putBoolean("agent_available",agent.available).apply()}
    fun cachedAgent():Agent?{val id=prefs.getLong("agent_id",0);if(id<=0)return null;return Agent(id,prefs.getString("agent_name","Customer Care").orEmpty(),prefs.getString("agent_avatar","").orEmpty().ifBlank{null},prefs.getBoolean("agent_available",false))}
    suspend fun me()=api.me()
    suspend fun setAvailable(value:Boolean)=api.availability(AvailabilityRequest(value)).also(::cacheAgent)
    suspend fun conversations()=api.conversations()
    suspend fun messages(id:Long,after:Long=0)=api.messages(id,after,1)
    suspend fun closeConversation(id:Long)=api.closeConversation(id)
    suspend fun activity(id:Long,state:String)=api.activity(ActivityRequest(id,state))
    suspend fun sendText(id:Long,text:String)=api.send(SendRequest(id,"text",text))

    private fun cleanMime(raw:String,kind:String):String {
        val mime=raw.substringBefore(';').lowercase()
        return when {
            kind=="image" && mime in setOf("image/jpeg","image/png","image/webp") -> mime
            kind=="audio" && mime in setOf("audio/mp4","audio/aac","audio/mpeg","audio/webm","audio/ogg","audio/3gpp","audio/amr") -> mime
            kind=="image" -> "image/jpeg"
            else -> "audio/mp4"
        }
    }

    suspend fun sendMedia(id:Long,uri:Uri,kind:String,mime:String,onProgress:(Int)->Unit={}):Message {
        val resolved=cleanMime(mime,kind)
        val temp=withContext(Dispatchers.IO){File.createTempFile("kbcc-$kind-",".upload",context.cacheDir).also{target->context.contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{output->input.copyTo(output)}}?:throw IOException("The selected file could not be opened")}}
        return try{uploadAndSend(id,temp,kind,resolved,onProgress)}finally{temp.delete()}
    }

    suspend fun sendAudio(id:Long,file:File,onProgress:(Int)->Unit={}):Message {
        if(!file.exists()||file.length()<=0)throw IOException("No voice recording was created")
        return uploadAndSend(id,file,"audio","audio/mp4",onProgress)
    }

    private suspend fun uploadAndSend(id:Long,file:File,kind:String,mime:String,onProgress:(Int)->Unit):Message {
        val length=file.length();if(length<=0)throw IOException("The media file is empty")
        val delivered=api.presign(PresignRequest(id,kind,mime,length))
        val body=object:RequestBody(){
            override fun contentType()=mime.toMediaType()
            override fun contentLength()=length
            override fun writeTo(sink:BufferedSink){file.inputStream().use{input->val buffer=ByteArray(DEFAULT_BUFFER_SIZE);var sent=0L;while(true){val count=input.read(buffer);if(count<0)break;sink.write(buffer,0,count);sent+=count;onProgress(((sent*100)/length).toInt().coerceIn(0,100))}}}
        }
        var failure:Throwable?=null
        for(attempt in 1..3){try{putSigned(delivered.uploadUrl,body);failure=null;break}catch(e:Throwable){failure=e;if(attempt<3){onProgress(0);delay((attempt*600).toLong())}}}
        if(failure!=null)throw IOException("Direct R2 ${if(kind=="audio")"voice-note" else "image"} upload failed after 3 attempts: ${failure.message?:"check the R2 API settings and connection"}",failure)
        onProgress(100)
        return try{api.send(SendRequest(id,kind,mediaUrl=delivered.publicUrl,objectKey=delivered.objectKey,mimeType=mime))}catch(e:Exception){throw IOException("The file reached R2, but WordPress could not save the chat message: ${e.message?:"request failed"}",e)}
    }

    private suspend fun putSigned(url:String,body:RequestBody)=withContext(Dispatchers.IO){val request=Request.Builder().url(url).header("Content-Type",body.contentType().toString()).put(body).build();uploadClient.newCall(request).execute().use{if(!it.isSuccessful){val detail=it.body?.string()?.take(240)?.replace(Regex("\\s+")," ").orEmpty();throw IOException("R2 HTTP ${it.code}${if(detail.isBlank())"" else ": $detail"}")}}}
    suspend fun registerDevice(){val token=suspendCancellableCoroutine<String>{c->FirebaseMessaging.getInstance().token.addOnSuccessListener{c.resume(it)}.addOnFailureListener{c.resume("")}};val resolved=token.ifBlank{prefs.getString("pending_fcm_token","").orEmpty()};if(resolved.isNotBlank())registerDeviceToken(resolved)}
    suspend fun registerDeviceToken(token:String){prefs.edit().putString("pending_fcm_token",token).apply();if(signedIn())api.device(DeviceRequest(token))}
}
