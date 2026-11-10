package com.example.socialapp.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.socialapp.api.ChatCreate
import com.example.socialapp.api.FolderCreate
import com.example.socialapp.api.FolderOut
import com.example.socialapp.api.ProfileUpdate
import com.example.socialapp.api.RetrofitClient
import com.example.socialapp.api.SocialApi
import com.example.socialapp.data.SessionManager
import com.example.socialapp.databinding.ActivityChatListBinding
import com.example.socialapp.databinding.DialogNewGroupBinding
import com.example.socialapp.databinding.DialogNewPrivateBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ChatListActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatListBinding
    private lateinit var session: SessionManager
    private lateinit var api: SocialApi

    private val adapter = ChatListAdapter(
        onClick = { item ->
            val i = Intent(this, ChatActivity::class.java)
            i.putExtra("chat_id", item.chat.id)
            i.putExtra("chat_title", item.displayName)
            i.putExtra("chat_type", item.chat.type)
            startActivity(i)
        },
        onLongClick = { item -> showChatContextMenu(item) },
    )

    private var allItems: List<ChatListItem> = emptyList()
    private var folders: List<FolderOut> = emptyList()
    private var selectedFolderId: Int = -1

    private val avatarPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { uploadAvatar(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(b.root)
        RetrofitClient.init(this)
        session = SessionManager(this)
        api = RetrofitClient.get()

        if (!session.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish(); return
        }

        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.tvUser.text = session.displayName ?: session.phone ?: ""

        b.swipe.setOnRefreshListener { refreshAll() }
        b.fabNewChat.setOnClickListener { showNewChatMenu() }
        b.btnLogout.setOnClickListener { confirmLogout() }
        b.tvUser.setOnClickListener { showEditProfileDialog() }

        loadProfile()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun refreshAll() {
        lifecycleScope.launch {
            try {
                folders = api.listFolders()
                buildFolderChips()
                loadChats()
            } catch (e: Exception) {
                Log.e("ChatList", "refresh error: ${e.message}")
            }
        }
    }

    private fun buildFolderChips() {
        b.folderContainer.removeAllViews()
        b.folderContainer.addView(makeChip("همه", -1))
        for (f in folders) {
            b.folderContainer.addView(makeChip(f.name, f.id))
        }
        b.folderContainer.addView(makeChip("⚙️", -2))
    }

    private fun makeChip(label: String, folderId: Int): TextView {
        val chip = TextView(this).apply {
            text = label
            textSize = 13f
            setPadding(32, 14, 32, 14)
            setTextColor(
                if (folderId == selectedFolderId) 0xFFFFFFFF.toInt()
                else 0xFF1E293B.toInt()
            )
            setBackgroundColor(
                if (folderId == selectedFolderId) 0xFF2563EB.toInt()
                else 0xFFEEF2F7.toInt()
            )
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 16 }
        chip.layoutParams = lp
        chip.setOnClickListener {
            if (folderId == -2) {
                showFolderManagement()
            } else {
                selectedFolderId = folderId
                buildFolderChips()
                applyFolderFilter()
            }
        }
        return chip
    }

    private fun applyFolderFilter() {
        val filtered = if (selectedFolderId == -1) {
            allItems
        } else {
            val folder = folders.firstOrNull { it.id == selectedFolderId }
            val ids = folder?.chat_ids ?: emptyList()
            allItems.filter { it.chat.id in ids }
        }
        adapter.submit(filtered)
        b.emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showFolderManagement() {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("➕ ساخت پوشه جدید")
        actions.add { showCreateFolderDialog() }

        if (selectedFolderId > 0) {
            val current = folders.firstOrNull { it.id == selectedFolderId }
            if (current != null) {
                options.add("✏️ تغییر نام «${current.name}»")
                actions.add { showRenameFolderDialog(current) }
                options.add("🗑 حذف پوشه «${current.name}»")
                actions.add { confirmDeleteFolder(current) }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("مدیریت پوشه‌ها")
            .setItems(options.toTypedArray()) { _, i -> actions[i]() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "نام پوشه (مثلاً: کاری، خانوادگی)"
            setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("پوشه جدید")
            .setView(input)
            .setPositiveButton("ایجاد") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        api.createFolder(FolderCreate(name))
                        Toast.makeText(this@ChatListActivity,
                            "پوشه «$name» ساخته شد", Toast.LENGTH_SHORT).show()
                        refreshAll()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatListActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showRenameFolderDialog(folder: FolderOut) {
        val input = EditText(this).apply {
            setText(folder.name); setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("تغییر نام پوشه")
            .setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        api.renameFolder(folder.id, FolderCreate(name))
                        refreshAll()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatListActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmDeleteFolder(folder: FolderOut) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف پوشه")
            .setMessage("پوشه «${folder.name}» حذف شود؟ (چت‌ها حذف نمی‌شوند)")
            .setPositiveButton("بله") { _, _ ->
                lifecycleScope.launch {
                    try {
                        api.deleteFolder(folder.id)
                        if (selectedFolderId == folder.id) selectedFolderId = -1
                        refreshAll()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatListActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showChatContextMenu(item: ChatListItem) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (folders.isEmpty()) {
            options.add("➕ ساخت پوشه و افزودن")
            actions.add { showCreateFolderWithChat(item) }
        } else {
            if (selectedFolderId > 0) {
                val current = folders.firstOrNull { it.id == selectedFolderId }
                if (current != null && item.chat.id in (current.chat_ids ?: emptyList())) {
                    options.add("➖ حذف از پوشه «${current.name}»")
                    actions.add {
                        lifecycleScope.launch {
                            try {
                                api.removeChatFromFolder(current.id, item.chat.id)
                                refreshAll()
                            } catch (e: Exception) {
                                Toast.makeText(this@ChatListActivity,
                                    "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            options.add("📁 افزودن به پوشه")
            actions.add { showAddToFolderDialog(item) }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(item.displayName)
            .setItems(options.toTypedArray()) { _, i -> actions[i]() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAddToFolderDialog(item: ChatListItem) {
        val names = folders.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("افزودن به پوشه")
            .setItems(names) { _, which ->
                val folder = folders[which]
                lifecycleScope.launch {
                    try {
                        api.addChatToFolder(folder.id, item.chat.id)
                        Toast.makeText(this@ChatListActivity,
                            "به «${folder.name}» افزوده شد", Toast.LENGTH_SHORT).show()
                        refreshAll()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatListActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showCreateFolderWithChat(item: ChatListItem) {
        val input = EditText(this).apply {
            hint = "نام پوشه"; setPadding(40, 30, 40, 30)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("پوشه جدید + افزودن «${item.displayName}»")
            .setView(input)
            .setPositiveButton("ایجاد") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        val folder = api.createFolder(FolderCreate(name))
                        api.addChatToFolder(folder.id, item.chat.id)
                        refreshAll()
                    } catch (e: Exception) {
                        Toast.makeText(this@ChatListActivity,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val me = api.me()
                session.displayName = me.display_name
                session.avatarUrl = me.avatar_url
                session.permissionsJson = org.json.JSONObject(
                    me.permissions ?: emptyMap<String, Boolean>()
                ).toString()
                b.tvUser.text = me.display_name ?: me.phone_number
            } catch (_: Exception) {}
        }
    }

    private fun showEditProfileDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
        }
        val nameInput = EditText(this).apply {
            hint = "نام نمایشی"; setText(session.displayName ?: "")
        }
        val avatarInput = EditText(this).apply {
            hint = "لینک آواتار (یا آپلود کنید)"
            setText(session.avatarUrl ?: "")
        }
        val uploadBtn = android.widget.Button(this).apply { text = "📷 آپلود آواتار از گالری" }
        container.addView(nameInput)
        container.addView(avatarInput)
        container.addView(uploadBtn)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("ویرایش پروفایل")
            .setView(container)
            .setPositiveButton("ذخیره", null)
            .setNegativeButton("انصراف", null)
            .create()
        dialog.show()

        uploadBtn.setOnClickListener {
            avatarPicker.launch("image/*")
        }

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newName = nameInput.text.toString().trim()
            val newAvatar = avatarInput.text.toString().trim()
            lifecycleScope.launch {
                try {
                    val updated = api.updateProfile(
                        ProfileUpdate(
                            display_name = newName.ifEmpty { null },
                            avatar_url = newAvatar.ifEmpty { null },
                        )
                    )
                    session.displayName = updated.display_name
                    session.avatarUrl = updated.avatar_url
                    b.tvUser.text = updated.display_name ?: updated.phone_number
                    Toast.makeText(this@ChatListActivity,
                        "پروفایل به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    refreshAll()
                } catch (e: Exception) {
                    Toast.makeText(this@ChatListActivity,
                        "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun uploadAvatar(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val fileName = "avatar.jpg"
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val tempFile = File(cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                val part = MultipartBody.Part.createFormData(
                    "file", fileName,
                    tempFile.asRequestBody(mimeType.toMediaTypeOrNull()),
                )
                val resp = api.uploadAvatar(part)
                val newUrl = resp["avatar_url"]
                if (newUrl != null) {
                    session.avatarUrl = newUrl
                    Toast.makeText(this@ChatListActivity,
                        "آواتار آپلود شد", Toast.LENGTH_SHORT).show()
                    loadProfile()
                    refreshAll()
                }
                tempFile.delete()
            } catch (e: Exception) {
                Toast.makeText(this@ChatListActivity,
                    "آپلود ناموفق: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ⭐ loadChats با avatarUrl
    private fun loadChats() {
        b.swipe.isRefreshing = true
        lifecycleScope.launch {
            try {
                val chats = api.listChats()
                val items = mutableListOf<ChatListItem>()
                for (c in chats) {
                    var name = c.title ?: "چت"
                    var status = "آفلاین"
                    var isOnline = false
                    var avatarUrl: String? = null

                    if (c.type == "private") {
                        try {
                            val members = api.chatMembers(c.id)
                            val other = members.firstOrNull { it.id != session.userId }
                            if (other != null) {
                                name = other.display_name ?: other.phone_number
                                isOnline = other.is_online
                                status = if (other.is_online) "آنلاین" else "آفلاین"
                                avatarUrl = other.avatar_url
                            }
                        } catch (_: Exception) {}
                    } else {
                        try {
                            val members = api.chatMembers(c.id)
                            status = "${members.size} عضو"
                        } catch (_: Exception) {}
                    }
                    items.add(ChatListItem(c, name, status, isOnline, avatarUrl))
                }
                allItems = items
                applyFolderFilter()
            } catch (e: Exception) {
                Toast.makeText(this@ChatListActivity,
                    "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                b.swipe.isRefreshing = false
            }
        }
    }

    private fun showNewChatMenu() {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("💬 چت خصوصی")
        actions.add { showNewPrivateDialog() }
        if (session.hasPermission("create_group")) {
            options.add("👥 گروه جدید")
            actions.add { showCreateGroupOrChannel("group") }
        }
        if (session.hasPermission("create_channel")) {
            options.add("📢 کانال جدید")
            actions.add { showCreateGroupOrChannel("channel") }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("ایجاد")
            .setItems(options.toTypedArray()) { _, i -> actions[i]() }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showNewPrivateDialog() {
        val db = DialogNewPrivateBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle("چت خصوصی جدید")
            .setView(db.root)
            .setPositiveButton("ایجاد") { _, _ ->
                val phone = db.etPhone.text.toString().trim()
                if (phone.length < 10) {
                    Toast.makeText(this, "شماره نامعتبر", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                createPrivateChat(phone)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun createPrivateChat(phone: String) {
        lifecycleScope.launch {
            try {
                val users = api.searchUsers(phone)
                val target = users.firstOrNull { it.phone_number == phone }
                if (target == null) {
                    Toast.makeText(this@ChatListActivity,
                        "کاربری با این شماره یافت نشد", Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (target.id == session.userId) {
                    Toast.makeText(this@ChatListActivity,
                        "با خودتان نمی‌توانید چت کنید", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val chat = api.createChat(ChatCreate(
                    type = "private", member_ids = listOf(target.id),
                ))
                val name = target.display_name ?: target.phone_number
                loadChats()
                val i = Intent(this@ChatListActivity, ChatActivity::class.java)
                i.putExtra("chat_id", chat.id)
                i.putExtra("chat_title", name)
                i.putExtra("chat_type", "private")
                startActivity(i)
            } catch (e: Exception) {
                Toast.makeText(this@ChatListActivity,
                    "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCreateGroupOrChannel(type: String) {
        val db = DialogNewGroupBinding.inflate(layoutInflater)
        db.etTitle.hint = if (type == "group") "نام گروه" else "نام کانال"
        MaterialAlertDialogBuilder(this)
            .setTitle(if (type == "group") "گروه جدید" else "کانال جدید")
            .setView(db.root)
            .setPositiveButton("ایجاد") { _, _ ->
                val title = db.etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, "نام را وارد کنید", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                createGroupOrChannel(type, title, db.etPhones.text.toString())
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun createGroupOrChannel(type: String, title: String, phonesRaw: String) {
        val phones = phonesRaw.split(",", "\n", " ")
            .map { it.trim() }.filter { it.length >= 10 }.distinct()
        lifecycleScope.launch {
            try {
                val memberIds = mutableListOf<Int>()
                val notFound = mutableListOf<String>()
                for (phone in phones) {
                    try {
                        val users = api.searchUsers(phone)
                        val exact = users.firstOrNull { it.phone_number == phone }
                        if (exact == null) notFound.add(phone)
                        else if (exact.id != session.userId) memberIds.add(exact.id)
                    } catch (_: Exception) { notFound.add(phone) }
                }
                val chat = api.createChat(ChatCreate(
                    type = type, title = title, member_ids = memberIds,
                ))
                val sb = StringBuilder("«$title» ایجاد شد\n${memberIds.size + 1} عضو")
                if (notFound.isNotEmpty()) {
                    sb.append("\n⚠️ یافت نشدند: ${notFound.joinToString(", ")}")
                }
                MaterialAlertDialogBuilder(this@ChatListActivity)
                    .setTitle("نتیجه").setMessage(sb.toString())
                    .setPositiveButton("باشه") { _, _ ->
                        loadChats()
                        val i = Intent(this@ChatListActivity, ChatActivity::class.java)
                        i.putExtra("chat_id", chat.id)
                        i.putExtra("chat_title", title)
                        i.putExtra("chat_type", type)
                        startActivity(i)
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatListActivity,
                    "خطا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("خروج").setMessage("از حساب خود خارج می‌شوید؟")
            .setPositiveButton("بله") { _, _ ->
                session.clear()
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}