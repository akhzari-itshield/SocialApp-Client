package com.example.socialapp.ui

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.socialapp.api.ChatMemberInfo
import com.example.socialapp.api.SocialApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class GroupMembersDialog(
    private val ctx: Context,
    private val api: SocialApi,
    private val chatId: Int,
    private val myId: Int,
    private val chatType: String,
    private var membersMap: Map<Int, ChatMemberInfo>,
    private val onChanged: () -> Unit,
) {
    private lateinit var dialog: Dialog
    private lateinit var container: LinearLayout

    fun show() {
        val scroll = ScrollView(ctx)
        container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
        }
        scroll.addView(container)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            // ⭐ دکمه‌ی افزودن برای گروه AND کانال
            if (chatType == "group" || chatType == "channel") {
                val addBtn = Button(ctx).apply {
                    text = "➕ افزودن عضو"
                    setOnClickListener { showAddDialog() }
                }
                addView(addBtn)
            }
        }

        dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("اعضای چت")
            .setView(root)
            .setNegativeButton("بستن", null)
            .create()
        dialog.show()
        loadMembers()
    }

    private fun loadMembers() {
        container.removeAllViews()
        container.addView(TextView(ctx).apply { text = "در حال بارگذاری..." })
        (ctx as? LifecycleOwner)?.lifecycleScope?.launch {
            try {
                val members = api.chatMembers(chatId)
                container.removeAllViews()
                if (members.isEmpty()) {
                    container.addView(TextView(ctx).apply {
                        text = "هیچ عضوی وجود ندارد"
                        setPadding(20, 20, 20, 20)
                    })
                } else {
                    for (m in members) addMemberRow(m)
                }
            } catch (e: Exception) {
                container.removeAllViews()
                container.addView(TextView(ctx).apply { text = "خطا: ${e.message}" })
            }
        }
    }

    private fun addMemberRow(m: ChatMemberInfo) {
        val roleFa = when (m.role) {
            "owner" -> "👑 مالک"
            "admin" -> "⭐ ادمین"
            else -> "عضو"
        }
        val statusFa = if (m.is_online) "🟢 آنلاین" else "⚫ آفلاین"

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 16, 10, 16)
        }
        row.addView(TextView(ctx).apply {
            text = m.display_name ?: m.phone_number
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(ctx).apply {
            text = "${m.phone_number} — $roleFa — $statusFa"
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        })

        val btns = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6, 0, 0)
        }

        // ⭐ برای گروه AND کانال
        if ((chatType == "group" || chatType == "channel") && m.id != myId) {
            val myRole = membersMap[myId]?.role
            if (myRole == "owner" || myRole == "admin") {
                val removeBtn = Button(ctx).apply {
                    text = "حذف"
                    setOnClickListener {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("حذف عضو")
                            .setMessage("${m.display_name ?: m.phone_number} حذف شود؟")
                            .setPositiveButton("بله") { _, _ ->
                                (ctx as? LifecycleOwner)?.lifecycleScope?.launch {
                                    try {
                                        api.removeMember(chatId, m.id)
                                        onChanged(); loadMembers()
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "خطا: ${e.message}",
                                            Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .setNegativeButton("انصراف", null)
                            .show()
                    }
                }
                btns.addView(removeBtn)
            }
            if (myRole == "owner" && m.role != "owner") {
                val roleBtn = Button(ctx).apply {
                    text = "تغییر نقش"
                    setOnClickListener { showRoleDialog(m) }
                }
                btns.addView(roleBtn)
            }
        }

        if (btns.childCount > 0) row.addView(btns)
        container.addView(row)

        val sep = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        container.addView(sep)
    }

    private fun showRoleDialog(m: ChatMemberInfo) {
        val roles = arrayOf("owner", "admin", "member")
        val labels = arrayOf("👑 مالک", "⭐ ادمین", "عضو")
        MaterialAlertDialogBuilder(ctx)
            .setTitle("نقش جدید برای ${m.display_name ?: m.phone_number}")
            .setItems(labels) { _, which ->
                (ctx as? LifecycleOwner)?.lifecycleScope?.launch {
                    try {
                        api.setMemberRole(chatId, m.id, roles[which])
                        onChanged(); loadMembers()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showAddDialog() {
        val input = EditText(ctx).apply {
            hint = "شماره تلفن (مثال: 09121234567)"
            setPadding(30, 30, 30, 30)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("افزودن عضو").setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.length < 10) {
                    Toast.makeText(ctx, "شماره نامعتبر", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                (ctx as? LifecycleOwner)?.lifecycleScope?.launch {
                    try {
                        // ⭐ جستجوی دقیق
                        val users = api.searchUsers(phone)
                        val exact = users.firstOrNull { it.phone_number == phone }
                        if (exact == null) {
                            Toast.makeText(ctx,
                                "کاربری با شماره $phone یافت نشد",
                                Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (exact.id == myId) {
                            Toast.makeText(ctx, "خودتان عضو هستید", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (membersMap.containsKey(exact.id)) {
                            Toast.makeText(ctx,
                                "این کاربر از قبل عضو است", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        api.addMember(chatId, exact.id)
                        Toast.makeText(ctx,
                            "${exact.display_name ?: exact.phone_number} افزوده شد",
                            Toast.LENGTH_SHORT).show()
                        onChanged(); loadMembers()
                    } catch (e: Exception) {
                        Toast.makeText(ctx,
                            "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}