package ng.kbmovies.customercare.data

import android.content.Context
import android.net.Uri
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ng.kbmovies.customercare.BuildConfig
import java.io.File
import kotlin.coroutines.resume

class CareRepository(private val context:Context) {
    private val prefs=context.getSharedPreferences("kbcc",Context.MODE_PRIVATE)
    private val client=OkHttpClient.Builder().addInterceptor { chain ->
        val token=prefs.getString("token","").orEmpty()
        val request=chain.request().newBuilder().apply { if(token.isNotBlank())header("Authorization","Bearer $token") }.build()
        chain.proceed(request)
    }.addInterceptor(HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BASIC}).build()
    private val api=Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(CareApi::class.java)
    fun signedIn()=!prefs.getString("token","").isNullOrBlank()
    suspend fun login(user:String,pass:String):Agent { val r=api.login(LoginRequest(user,pass));prefs.edit().putString("token",r.token).apply();runCatching{registerDevice()};return r.agent }
    fun logout(){prefs.edit().clear().apply()}
    suspend fun me()=api.me()
    suspend fun setAvailable(value:Boolean)=api.availability(AvailabilityRequest(value))
    suspend fun conversations()=api.conversations()
    suspend fun messages(id:Long,after:Long=0)=api.messages(id,after)
    suspend fun sendText(id:Long,text:String)=api.send(SendRequest(id,"text",text))
    suspend fun sendMedia(id:Long,uri:Uri,kind:String,mime:String):Message {
        val bytes=context.contentResolver.openInputStream(uri)!!.use{it.readBytes()}
        val signed=api.presign(PresignRequest(id,kind,mime,bytes.size.toLong()))
        val put=Request.Builder().url(signed.uploadUrl).put(bytes.toRequestBody(mime.toMediaType())).build()
        client.newCall(put).execute().use{if(!it.isSuccessful)error("R2 upload failed")}
        return api.send(SendRequest(id,kind,mediaUrl=signed.publicUrl,objectKey=signed.objectKey,mimeType=mime))
    }
    suspend fun sendAudio(id:Long,file:File,mime:String="audio/mp4"):Message {
        val bytes=file.readBytes();val signed=api.presign(PresignRequest(id,"audio",mime,bytes.size.toLong()))
        val put=Request.Builder().url(signed.uploadUrl).put(bytes.toRequestBody(mime.toMediaType())).build()
        client.newCall(put).execute().use{if(!it.isSuccessful)error("R2 upload failed")}
        return api.send(SendRequest(id,"audio",mediaUrl=signed.publicUrl,objectKey=signed.objectKey,mimeType=mime))
    }
    suspend fun registerDevice(){
        val token=suspendCancellableCoroutine<String>{c->FirebaseMessaging.getInstance().token.addOnSuccessListener{c.resume(it)}.addOnFailureListener{c.resume("")}}
        if(token.isNotBlank())api.device(DeviceRequest(token))
    }
}
