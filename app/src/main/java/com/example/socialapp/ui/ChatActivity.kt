package com.example.socialapp.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.socialapp.api.ChatMemberInfo
import com.example.socialapp.api.MessageCreate
import com.example.socialapp.api.MessageOut
import com.example.socialapp.api.RetrofitClient
import com.example.socialapp.api.SocialApi
import com.example.socialapp.data.SessionManager
import com.example.socialapp.databinding.ActivityChatBinding
import com.example.socialapp.ws.WebSocketManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatBinding
    private lateinit var session: SessionManager
    private lateinit var api: SocialApi
    private lateinit var ws: WebSocketManager
    private lateinit var adapter: MessageAdapter
    private var chatId: Int = 0
    private var chatType: String = "private"
    private val messagesList = mutableListOf<MessageOut>()
    private val seenRealIds = mutableSetOf<Int>()
    private var myId: Int = 0
    private var replyTo: MessageOut? = null
    private var searchMode = false
    private var isLoadingOlder = false
    private var hasMoreOld = true

    private var membersMap: MutableMap<Int, ChatMemberInfo> = mutableMapOf()

    private var recorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var recordingStart: Long = 0

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSend(it, "image") }
    }
    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSend(it, "file") }
    }
    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Toast.makeText(this, "حالا دکمه را نگه دارید", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "برای ضبط صدا اجازه لازم است", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)

        RetrofitClient.init(this)
        session = SessionManager(this)
        api = RetrofitClient.get()
        myId = session.userId

        chatId = intent.getIntExtra("chat_id", 0)
        chatType = intent.getStringExtra("chat_type") ?: "private"
        b.tvTitle.text = intent.getStringExtra("chat_title") ?: "چت"

        if (chatType == "group" || chatType == "channel") {
            b.btnMembers.visibility = View.VISIBLE
            b.btnMembers.setOnClickListener { openMembersDialog() }
        }

        applyChannelRestrictions()

        adapter = MessageAdapter(myId, {}, { showMessageActions(it) })
        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.recycler.layoutManager = lm
        b.recycler.adapter = adapter

        b.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (!rv.canScrollVertically(-1) && !isLoadingOlder && hasMoreOld &&
                    messagesList.isNotEmpty() && !searchMode) {
                    loadOlderMessages()
                }
            }
        })

        b.btnBack.setOnClickListener { finish() }
        b.btnSend.setOnClickListener {
            val text = b.etMessage.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            sendTextMessage(text)
        }
        b.btnCancelReply.setOnClickListener {
            replyTo = null
            updateReplyBar()
        }
        b.btnSearch.setOnClickListener { toggleSearch() }
        b.btnCloseSearch.setOnClickListener { toggleSearch() }
        b.etSearch.setOnEditorActionListener { _, _, _ ->
            performSearch(b.etSearch.text.toString().trim()); true
        }
        b.btnAttach.setOnClickListener { showAttachMenu() }

        if (b.btnCall != null) {
            b.btnCall.setOnClickListener { startCall() }
        }

        if (b.btnMic != null) {
            b.btnMic.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (ContextCompat.checkSelfPermission(
                                this, Manifest.permission.RECORD_AUDIO
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            startRecording()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        stopAndSendRecording(); true
                    }
                    else -> false
                }
            }
        }

        setupWebSocket()
        loadMessages()
        loadMembers()
    }

    private fun applyChannelRestrictions() {
        if (chatType == "channel") {
            lifecycleScope.launch {
                try {
                    val members = api.chatMembers(chatId)
                    val my = members.firstOrNull { it.id == myId }
                    val canPost = my?.role == "owner" || my?.role == "admin"
                    if (!canPost) {
                        b.inputBar.visibility = View.GONE
                        b.replyBar.visibility = View.GONE
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun loadMembers() {
        lifecycleScope.launch {
            try {
                val members = api.chatMembers(chatId)
                membersMap = members.associateBy { it.id }.toMutableMap()
                val maxLastRead = members
                    .filter { it.id != myId }
                    .mapNotNull { it.last_read_message_id }
                    .maxOrNull() ?: 0
                adapter.setOtherLastRead(maxLastRead)
                updateTitleWithStatus(members)
            } catch (_: Exception) {}
        }
    }

    private fun updateTitleWithStatus(members: List<ChatMemberInfo>) {
        if (chatType != "private") return
        val other = members.firstOrNull { it.id != myId } ?: return
        val title = other.display_name ?: other.phone_number
        val status = if (other.is_online) "آنلاین" else {
            other.last_seen?.let { "آخرین بازدید: ${formatLastSeen(it)}" } ?: "آفلاین"
        }
        b.tvTitle.text = "$title\n$status"
    }

    private fun formatLastSeen(iso: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = sdf.parse(iso.take(19)) ?: return "اخیراً"
            val now = System.currentTimeMillis()
            val diff = now - date.time
            when {
                diff < 60_000 -> "همین حالا"
                diff < 3600_000 -> "${diff / 60_000} دقیقه پیش"
                diff < 86400_000 -> "${diff / 3600_000} ساعت پیش"
                else -> SimpleDateFormat("yyyy/MM/dd", Locale.US).format(date)
            }
        } catch (_: Exception) { "اخیراً" }
    }

    private fun setupWebSocket() {
        ws = WebSocketManager(session.token ?: "") { event, data ->
            runOnUiThread {
                when (event) {
                    "message_deleted" -> {
                        val mid = data.optInt("message_id")
                        if (data.optInt("chat_id") == chatId) {
                            messagesList.removeAll { it.id == mid }
                            seenRealIds.remove(mid)
                            adapter.submit(messagesList)
                        }
                    }
                    "new_message" -> handleNewMessage(data)
                    "call_request" -> handleIncomingCall(data)
                    "read_receipt" -> handleReadReceipt(data)
                    "user_online" -> {
                        val uid = data.optInt("user_id")
                        membersMap[uid]?.let {
                            membersMap[uid] = it.copy(is_online = true)
                            updateTitleWithStatus(membersMap.values.toList())
                        }
                    }
                    "user_offline" -> {
                        val uid = data.optInt("user_id")
                        val ls = data.optString("last_seen").ifEmpty { null }
                        membersMap[uid]?.let {
                            membersMap[uid] = it.copy(is_online = false, last_seen = ls)
                            updateTitleWithStatus(membersMap.values.toList())
                        }
                    }
                }
            }
        }
        ws.connect()
    }

    private fun handleNewMessage(data: JSONObject) {
        if (data.optInt("chat_id") != chatId) return
        val id = data.optInt("id")
        if (id <= 0) return
        if (id in seenRealIds) {
            Log.d("ChatActivity", "Duplicate message id=$id ignored")
            return
        }
        seenRealIds.add(id)

        val replyToId = if (data.has("reply_to_id") && !data.isNull("reply_to_id"))
            data.optInt("reply_to_id") else null
        val msg = MessageOut(
            id = id,
            chat_id = data.optInt("chat_id"),
            sender_id = data.optInt("sender_id"),
            content = if (data.isNull("content")) null else data.optString("content"),
            message_type = data.optString("message_type", "text"),
            file_url = if (data.isNull("file_url")) null else data.optString("file_url"),
            reply_to_id = replyToId,
            is_deleted = false,
            is_edited = false,
            created_at = data.optString("created_at"),
        )
        // حذف optimistic (اگر هنوز هست)
        messagesList.removeAll {
            it.id < 0 && it.sender_id == msg.sender_id && it.content == msg.content
        }
        messagesList.add(msg)
        adapter.submit(messagesList)
        b.recycler.scrollToPosition(messagesList.size - 1)

        if (msg.sender_id != myId) {
            sendReadReceipt(msg.id)
        }
    }

    private fun handleReadReceipt(data: JSONObject) {
        if (data.optInt("chat_id") != chatId) return
        val lastId = data.optInt("last_read_message_id")
        adapter.setOtherLastRead(lastId)
    }

    private fun sendReadReceipt(messageId: Int) {
        ws.send(JSONObject().apply {
            put("event", "read_receipt")
            put("chat_id", chatId)
            put("message_id", messageId)
        }.toString())
        lifecycleScope.launch {
            try { api.markRead(chatId, messageId) } catch (_: Exception) {}
        }
    }

    private fun handleIncomingCall(data: JSONObject) {
        val fromId = data.optInt("from_id")
        val cType = data.optString("call_type", "video")
        val typeFa = if (cType == "video") "تصویری" else "صوتی"
        MaterialAlertDialogBuilder(this)
            .setTitle("📞 تماس $typeFa ورودی")
            .setMessage("از کاربر #$fromId")
            .setPositiveButton("پذیرش") { _, _ ->
                val i = Intent(this, CallActivity::class.java)
                i.putExtra("target_user_id", fromId)
                i.putExtra("is_caller", false)
                i.putExtra("call_type", cType)
                startActivity(i)
            }
            .setNegativeButton("رد") { _, _ ->
                ws.send(JSONObject().apply {
                    put("event", "call_reject")
                    put("target_id", fromId)
                }.toString())
            }
            .setCancelable(false)
            .show()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            try {
                val msgs = api.messages(chatId, 50)
                messagesList.clear()
                seenRealIds.clear()
                msgs.forEach { if (it.id > 0) seenRealIds.add(it.id) }
                messagesList.addAll(msgs)
                adapter.submit(messagesList)
                if (msgs.isNotEmpty()) {
                    b.recycler.scrollToPosition(messagesList.size - 1)
                    val lastOther = msgs.lastOrNull { it.sender_id != myId }
                    if (lastOther != null) sendReadReceipt(lastOther.id)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadOlderMessages() {
        if (messagesList.isEmpty()) return
        isLoadingOlder = true
        val oldestId = messagesList.firstOrNull { it.id > 0 }?.id ?: run {
            isLoadingOlder = false; return
        }
        lifecycleScope.launch {
            try {
                val older = api.messages(chatId, 50, oldestId)
                if (older.isEmpty()) {
                    hasMoreOld = false
                } else {
                    older.forEach { if (it.id > 0) seenRealIds.add(it.id) }
                    messagesList.addAll(0, older)
                    adapter.submit(messagesList)
                    b.recycler.scrollToPosition(older.size - 1)
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "loadOlder error: ${e.message}")
            } finally {
                isLoadingOlder = false
            }
        }
    }

    private fun sendTextMessage(text: String) {
        b.etMessage.setText("")
        val currentReply = replyTo
        replyTo = null
        updateReplyBar()

        val optimistic = MessageOut(
            id = -(System.currentTimeMillis().toInt()),
            chat_id = chatId,
            sender_id = myId,
            content = text,
            message_type = "text",
            file_url = null,
            reply_to_id = currentReply?.id,
            is_deleted = false,
            is_edited = false,
            created_at = "",
        )
        messagesList.add(optimistic)
        adapter.submit(messagesList)
        b.recycler.scrollToPosition(messagesList.size - 1)

        lifecycleScope.launch {
            try {
                val sent = api.sendMessage(MessageCreate(
                    chat_id = chatId, content = text, reply_to_id = currentReply?.id,
                ))
                // ⭐ ثبت ID واقعی
                if (sent.id > 0) seenRealIds.add(sent.id)
                val idx = messagesList.indexOfFirst { it.id == optimistic.id }
                if (idx >= 0) {
                    messagesList[idx] = sent
                    adapter.submit(messagesList)
                } else {
                    if (messagesList.none { it.id == sent.id }) {
                        messagesList.add(sent)
                        adapter.submit(messagesList)
                        b.recycler.scrollToPosition(messagesList.size - 1)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity,
                    "ارسال ناموفق: ${e.message}", Toast.LENGTH_LONG).show()
                messagesList.remove(optimistic)
                adapter.submit(messagesList)
            }
        }
    }

    private fun showMessageActions(m: MessageOut) {
        if (m.is_deleted) return
        val mine = m.sender_id == myId
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("↪ پاسخ")
        actions.add { replyTo = m; updateReplyBar(); b.etMessage.requestFocus() }

        if (!m.content.isNullOrEmpty() && m.message_type == "text") {
            options.add("📋 کپی")
            actions.add {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", m.content))
                Toast.makeText(this, "کپی شد", Toast.LENGTH_SHORT).show()
            }
        }

        if (mine && m.id > 0) {
            if (m.message_type == "text") {
                options.add("✏️ ویرایش")
                actions.add { showEditDialog(m) }
            }
            options.add("🗑 حذف برای همه")
            actions.add { confirmDelete(m) }
        }

        if (m.id > 0) {
            options.add("🙈 حذف فقط برای من")
            actions.add { confirmDeleteForMe(m) }
        }

        MaterialAlertDialogBuilder(this)
            .setItems(options.toTypedArray()) { _, i -> actions[i]() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun updateReplyBar() {
        val r = replyTo
        if (r == null) b.replyBar.visibility = View.GONE
        else {
            b.replyBar.visibility = View.VISIBLE
            b.tvReplyPreview.text = (r.content ?: "[رسانه]").take(80)
        }
    }

    private fun showEditDialog(m: MessageOut) {
        val input = EditText(this).apply {
            setText(m.content); setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("ویرایش پیام").setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        api.editMessage(m.id, t)
                        val idx = messagesList.indexOfFirst { it.id == m.id }
                        if (idx >= 0) {
                            messagesList[idx] = messagesList[idx].copy(content = t, is_edited = true)
                            adapter.submit(messagesList)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmDelete(m: MessageOut) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف پیام").setMessage("این پیام برای همه حذف شود؟")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        api.deleteMessage(m.id, true)
                        val idx = messagesList.indexOfFirst { it.id == m.id }
                        if (idx >= 0) {
                            messagesList[idx] = messagesList[idx].copy(
                                content = null, file_url = null, is_deleted = true,
                            )
                            adapter.submit(messagesList)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    private fun confirmDeleteForMe(m: MessageOut) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف فقط برای من")
            .setMessage("این پیام فقط از دید شما پنهان می‌شود. طرف مقابل همچنان آن را می‌بیند.")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        api.deleteMessage(m.id, forEveryone = false)
                        messagesList.removeAll { it.id == m.id }
                        seenRealIds.remove(m.id)
                        adapter.submit(messagesList)
                        Toast.makeText(this@ChatActivity,
                            "پیام از دید شما حذف شد", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
    private fun toggleSearch() {
        searchMode = !searchMode
        b.searchBar.visibility = if (searchMode) View.VISIBLE else View.GONE
        if (searchMode) b.etSearch.requestFocus()
        else { b.etSearch.setText(""); loadMessages() }
    }

    private fun performSearch(q: String) {
        if (q.length < 2) {
            Toast.makeText(this, "حداقل ۲ حرف", Toast.LENGTH_SHORT).show(); return
        }
        lifecycleScope.launch {
            try {
                val results = api.searchMessages(chatId, q)
                messagesList.clear()
                adapter.submit(messagesList)
                messagesList.addAll(results)
                adapter.submit(messagesList)
                Toast.makeText(this@ChatActivity,
                    "${results.size} نتیجه", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAttachMenu() {
        if (!session.hasPermission("send_file")) {
            Toast.makeText(this, "ارسال فایل توسط مدیر غیرفعال شده", Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("ضمیمه")
            .setItems(arrayOf("📷 تصویر", "📎 فایل")) { _, which ->
                when (which) { 0 -> pickImage.launch("image/*"); 1 -> pickFile.launch("*/*") }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun uploadAndSend(uri: Uri, type: String) {
        val optimisticId = -(System.currentTimeMillis().toInt())
        val optimistic = MessageOut(
            id = optimisticId, chat_id = chatId, sender_id = myId,
            content = "در حال آپلود...", message_type = type,
            file_url = null, reply_to_id = replyTo?.id,
            is_deleted = false, is_edited = false, created_at = "",
        )
        messagesList.add(optimistic)
        adapter.submit(messagesList)
        b.recycler.scrollToPosition(messagesList.size - 1)

        val currentReply = replyTo
        replyTo = null
        updateReplyBar()

        lifecycleScope.launch {
            try {
                val fileName = getFileName(uri) ?: if (type == "image") "image.jpg" else "file.bin"
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val tempFile = File(cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                val part = MultipartBody.Part.createFormData(
                    "file", fileName, tempFile.asRequestBody(mimeType.toMediaTypeOrNull()),
                )
                val up = api.uploadFile(part, chatId)
                val sent = api.sendMessage(MessageCreate(
                    chat_id = chatId, content = up.name, message_type = type,
                    file_url = up.url, reply_to_id = currentReply?.id,
                ))
                if (sent.id > 0) seenRealIds.add(sent.id)
                val idx = messagesList.indexOfFirst { it.id == optimisticId }
                if (idx >= 0) { messagesList[idx] = sent; adapter.submit(messagesList) }
                tempFile.delete()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity,
                    "آپلود ناموفق: ${e.message}", Toast.LENGTH_LONG).show()
                messagesList.removeAll { it.id == optimisticId }
                adapter.submit(messagesList)
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx)
            }
        }
        return name
    }

    private fun startRecording() {
        if (!session.hasPermission("send_voice")) {
            Toast.makeText(this, "ارسال پیام صوتی غیرفعال است", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val f = File(cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            voiceFile = f
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r
            recordingStart = System.currentTimeMillis()
            Toast.makeText(this, "🎤 در حال ضبط...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAndSendRecording() {
        val r = recorder ?: return
        val duration = System.currentTimeMillis() - recordingStart
        try { r.stop(); r.release() } catch (_: Exception) {}
        recorder = null
        val file = voiceFile ?: return
        voiceFile = null
        if (duration < 800) {
            file.delete()
            Toast.makeText(this, "خیلی کوتاه بود", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val part = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody("audio/mp4".toMediaTypeOrNull()),
                )
                val up = api.uploadFile(part, chatId)
                val sent = api.sendMessage(MessageCreate(
                    chat_id = chatId, content = up.name,
                    message_type = "voice", file_url = up.url,
                ))
                if (sent.id > 0) seenRealIds.add(sent.id)
                file.delete()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity,
                    "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startCall() {
        if (chatType != "private") {
            Toast.makeText(this, "تماس فقط در چت خصوصی", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val members = api.chatMembers(chatId)
                val other = members.firstOrNull { it.id != myId } ?: return@launch
                val options = mutableListOf<String>()
                if (session.hasPermission("voice_call")) options.add("📞 صوتی")
                if (session.hasPermission("video_call")) options.add("📹 تصویری")
                if (options.isEmpty()) {
                    Toast.makeText(this@ChatActivity,
                        "تماس توسط مدیر غیرفعال شده", Toast.LENGTH_LONG).show()
                    return@launch
                }
                MaterialAlertDialogBuilder(this@ChatActivity)
                    .setTitle("نوع تماس")
                    .setItems(options.toTypedArray()) { _, which ->
                        val isVideo = options[which].contains("تصویری")
                        val i = Intent(this@ChatActivity, CallActivity::class.java)
                        i.putExtra("target_user_id", other.id)
                        i.putExtra("is_caller", true)
                        i.putExtra("call_type", if (isVideo) "video" else "voice")
                        startActivity(i)
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity,
                    "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openMembersDialog() {
        GroupMembersDialog(
            this, api, chatId, myId, chatType, membersMap,
            onChanged = { loadMembers() },
        ).show()
    }

    override fun onDestroy() {
        adapter.stopPlayback()
        ws.close()
        super.onDestroy()
    }
}