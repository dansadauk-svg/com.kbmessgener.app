package ng.kbmovies.customercare.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

data class LoginRequest(val username:String,val password:String)
data class LoginResponse(val token:String,val agent:Agent)
data class Agent(val id:Long,val name:String,val avatar:String?,val available:Boolean)
data class AvailabilityRequest(val available:Boolean)
data class DeviceRequest(val token:String,val platform:String="android")
data class Conversation(val id:Long,@SerializedName("public_id") val publicId:String,@SerializedName("customer_name") val customerName:String,@SerializedName("customer_avatar") val customerAvatar:String?,@SerializedName("last_message") val lastMessage:String?,@SerializedName("updated_at") val updatedAt:String,val unread:Int)
data class Message(val id:Long,@SerializedName("sender_type") val senderType:String,@SerializedName("message_type") val messageType:String,val body:String?,@SerializedName("media_url") val mediaUrl:String?,@SerializedName("created_at") val createdAt:String,@SerializedName("read_at") val readAt:String?=null,@SerializedName("delivery_status") val deliveryStatus:String="sent")
data class RealtimeEvent(val type:String="",@SerializedName("conversation_id") val conversationId:Long=0,@SerializedName("message_id") val messageId:Long=0,val status:String="",val message:Message?=null)
data class MessagesResponse(val messages:List<Message>,@SerializedName("next_after") val nextAfter:Long,@SerializedName("peer_read_id") val peerReadId:Long=0,@SerializedName("peer_activity") val peerActivity:String="",@SerializedName("history_saved") val historySaved:Boolean=true,@SerializedName("history_total") val historyTotal:Int=0)
data class ActivityRequest(@SerializedName("conversation_id") val conversationId:Long,val state:String)
data class SendRequest(@SerializedName("conversation_id") val conversationId:Long,val type:String,val body:String="",@SerializedName("media_url") val mediaUrl:String="",@SerializedName("object_key") val objectKey:String="",@SerializedName("mime_type") val mimeType:String="")
data class PresignRequest(@SerializedName("conversation_id") val conversationId:Long,val kind:String,@SerializedName("mime_type") val mimeType:String,val size:Long)
data class PresignResponse(@SerializedName("upload_url") val uploadUrl:String,@SerializedName("public_url") val publicUrl:String,@SerializedName("object_key") val objectKey:String)

interface CareApi {
    @POST("login") suspend fun login(@Body body:LoginRequest):LoginResponse
    @GET("me") suspend fun me():Agent
    @POST("availability") suspend fun availability(@Body body:AvailabilityRequest):Agent
    @POST("device") suspend fun device(@Body body:DeviceRequest)
    @GET("conversations") suspend fun conversations():List<Conversation>
    @GET("conversations/{id}/messages") suspend fun messages(@Path("id") id:Long,@Query("after") after:Long=0,@Query("mark_read") markRead:Int=1):MessagesResponse
    @POST("conversations/{id}/close") suspend fun closeConversation(@Path("id") id:Long)
    @POST("activity") suspend fun activity(@Body body:ActivityRequest)
    @POST("messages") suspend fun send(@Body body:SendRequest):Message
    @POST("media/presign") suspend fun presign(@Body body:PresignRequest):PresignResponse
}
