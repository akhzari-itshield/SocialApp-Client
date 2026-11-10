package com.example.socialapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.socialapp.api.ChatOut
import com.example.socialapp.databinding.ItemChatBinding
import com.example.socialapp.util.Constants

data class ChatListItem(
    val chat: ChatOut,
    val displayName: String,
    val status: String,
    val isOnline: Boolean,
    val avatarUrl: String? = null,
)

class ChatListAdapter(
    private val onClick: (ChatListItem) -> Unit,
    private val onLongClick: (ChatListItem) -> Unit,
) : RecyclerView.Adapter<ChatListAdapter.VH>() {

    private var items: List<ChatListItem> = emptyList()

    fun submit(newItems: List<ChatListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemChatBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvName.text = item.displayName
        holder.b.tvType.text = "${item.chat.type.toFa()} · ${item.status}"

        // ⭐ آواتار
        val fullAvatar = fullUrl(item.avatarUrl)
        if (!fullAvatar.isNullOrEmpty()) {
            holder.b.ivAvatar.visibility = View.VISIBLE
            holder.b.tvAvatarFallback.visibility = View.GONE
            holder.b.ivAvatar.load(fullAvatar) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_report_image)
                error(android.R.drawable.ic_menu_report_image)
            }
        } else {
            holder.b.ivAvatar.visibility = View.GONE
            holder.b.tvAvatarFallback.visibility = View.VISIBLE
            holder.b.tvAvatarFallback.text = when (item.chat.type) {
                "private" -> "💬"
                "group" -> "👥"
                "channel" -> "📢"
                else -> "💬"
            }
        }

        holder.b.onlineDot.visibility =
            if (item.chat.type == "private" && item.isOnline) View.VISIBLE
            else View.GONE

        holder.b.root.setOnClickListener { onClick(item) }
        holder.b.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount() = items.size

    private fun String.toFa(): String = when (this) {
        "private" -> "خصوصی"
        "group" -> "گروه"
        "channel" -> "کانال"
        else -> this
    }

    private fun fullUrl(rel: String?): String? {
        if (rel.isNullOrEmpty() || rel == "null") return null
        if (rel.startsWith("http")) return rel
        return Constants.BASE_URL.trimEnd('/') + rel
    }
}