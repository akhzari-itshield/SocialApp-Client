package com.example.socialapp.api

import okhttp3.MultipartBody
import retrofit2.http.*

interface SocialApi {
    @POST("auth/request-otp")
    suspend fun requestOtp(@Body req: PhoneRequest): Map<String, String>

    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body req: OtpVerify): TokenResponse

    @GET("users/me")
    suspend fun me(): UserOut

    @PUT("users/me")
    suspend fun updateProfile(@Body data: ProfileUpdate): UserOut

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Map<String, String>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: Int): UserOut

    @GET("users/search")
    suspend fun searchUsers(@Query("q") q: String): List<UserOut>

    @GET("chats/")
    suspend fun listChats(): List<ChatOut>

    @POST("chats/")
    suspend fun createChat(@Body req: ChatCreate): ChatOut

    @GET("chats/{id}/members")
    suspend fun chatMembers(@Path("id") chatId: Int): List<ChatMemberInfo>

    @POST("chats/{chatId}/members/{uid}")
    suspend fun addMember(
        @Path("chatId") chatId: Int,
        @Path("uid") uid: Int,
    ): Map<String, Boolean>

    @DELETE("chats/{chatId}/members/{uid}")
    suspend fun removeMember(
        @Path("chatId") chatId: Int,
        @Path("uid") uid: Int,
    ): Map<String, Boolean>

    @POST("chats/{chatId}/members/{uid}/role")
    suspend fun setMemberRole(
        @Path("chatId") chatId: Int,
        @Path("uid") uid: Int,
        @Query("role") role: String,
    ): Map<String, Boolean>

    @POST("chats/{chatId}/read")
    suspend fun markRead(
        @Path("chatId") chatId: Int,
        @Query("message_id") messageId: Int,
    ): Map<String, Boolean>

    @GET("messages/{chatId}")
    suspend fun messages(
        @Path("chatId") chatId: Int,
        @Query("limit") limit: Int = 50,
        @Query("before_id") beforeId: Int? = null,
    ): List<MessageOut>

    @POST("messages/")
    suspend fun sendMessage(@Body msg: MessageCreate): MessageOut

    @GET("messages/{chatId}/search")
    suspend fun searchMessages(
        @Path("chatId") chatId: Int,
        @Query("q") q: String,
    ): List<MessageOut>

    @PUT("messages/{id}")
    suspend fun editMessage(
        @Path("id") id: Int,
        @Query("content") content: String,
    ): Map<String, Boolean>

    @DELETE("messages/{id}")
    suspend fun deleteMessage(
        @Path("id") id: Int,
        @Query("for_everyone") forEveryone: Boolean = true,
    ): Map<String, Boolean>

    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Query("chat_id") chatId: Int?,
    ): FileUploadResponse

    // ---- Folders ----
    @GET("folders/")
    suspend fun listFolders(): List<FolderOut>

    @POST("folders/")
    suspend fun createFolder(@Body data: FolderCreate): FolderOut

    @PUT("folders/{id}")
    suspend fun renameFolder(
        @Path("id") id: Int,
        @Body data: FolderCreate,
    ): Map<String, Boolean>

    @DELETE("folders/{id}")
    suspend fun deleteFolder(@Path("id") id: Int): Map<String, Boolean>

    @POST("folders/{fid}/chats/{cid}")
    suspend fun addChatToFolder(
        @Path("fid") fid: Int,
        @Path("cid") cid: Int,
    ): Map<String, Any>

    @DELETE("folders/{fid}/chats/{cid}")
    suspend fun removeChatFromFolder(
        @Path("fid") fid: Int,
        @Path("cid") cid: Int,
    ): Map<String, Any>
}