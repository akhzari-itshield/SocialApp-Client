package com.example.socialapp.api

data class PhoneRequest(val phone_number: String)
data class OtpVerify(val phone_number: String, val otp: String)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val user_id: Int,
    val display_name: String?,
)

data class UserOut(
    val id: Int,
    val phone_number: String,
    val display_name: String?,
    val avatar_url: String? = null,
    val is_active: Boolean,
    val permissions: Map<String, Boolean>? = null,
    val last_seen: String? = null,
    val is_online: Boolean = false,
)

data class ProfileUpdate(
    val display_name: String? = null,
    val avatar_url: String? = null,
)

data class ChatOut(
    val id: Int,
    val type: String,
    val title: String?,
    val member_ids: List<Int>,
)

data class ChatMemberInfo(
    val id: Int,
    val phone_number: String,
    val display_name: String?,
    val avatar_url: String? = null,
    val role: String,
    val last_read_message_id: Int? = null,
    val last_seen: String? = null,
    val is_online: Boolean = false,
)

data class MessageOut(
    val id: Int,
    val chat_id: Int,
    val sender_id: Int,
    val content: String?,
    val message_type: String,
    val file_url: String?,
    val reply_to_id: Int?,
    val is_deleted: Boolean,
    val is_edited: Boolean,
    val created_at: String,
)

data class MessageCreate(
    val chat_id: Int,
    val content: String?,
    val message_type: String = "text",
    val file_url: String? = null,
    val reply_to_id: Int? = null,
)

data class ChatCreate(
    val type: String,
    val title: String? = null,
    val member_ids: List<Int> = emptyList(),
)

data class FileUploadResponse(
    val id: Int,
    val url: String,
    val size: Long,
    val name: String,
)

data class FolderOut(
    val id: Int,
    val name: String,
    val chat_ids: List<Int>,
)

data class FolderCreate(val name: String)