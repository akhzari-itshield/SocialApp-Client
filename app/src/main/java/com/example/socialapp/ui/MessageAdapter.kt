package com.example.socialapp.ui

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.socialapp.R
import com.example.socialapp.api.MessageOut
import com.example.socialapp.databinding.ItemMessageBinding
import com.example.socialapp.util.Constants

class MessageAdapter(
    private val myId: Int,
    private val onClick: (MessageOut) -> Unit,
    private val onLongClick: (MessageOut) -> Unit,
) : RecyclerView.Adapter<MessageAdapter.VH>() {
    private var items: List<MessageOut> = emptyList()
    private val messageMap = mutableMapOf<Int, MessageOut>()
    private var currentPlayer: MediaPlayer? = null
    private var currentPlayingId: Int = -1
    private var otherLastReadId: Int = 0

    fun submit(list: List<MessageOut>) {
        items = list.toList()
        messageMap.clear()
        items.forEach { messageMap[it.id] = it }
        notifyDataSetChanged()
    }

    fun setOtherLastRead(messageId: Int) {
        otherLastReadId = messageId
        notifyDataSetChanged()
    }

    fun stopPlayback() {
        try { currentPlayer?.stop(); currentPlayer?.release() } catch (_: Exception) {}
        currentPlayer = null
        currentPlayingId = -1
    }

    private fun fullUrl(rel: String?): String? {
        if (rel.isNullOrEmpty() || rel == "null") return null
        if (rel.startsWith("http")) return rel
        return Constants.BASE_URL.trimEnd('/') + rel
    }

    inner class VH(val b: ItemMessageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val mine = m.sender_id == myId
        val ctx = holder.b.root.context

        holder.b.root.setBackgroundColor(
            ContextCompat.getColor(ctx, if (mine) R.color.bubble_mine else R.color.bubble_other)
        )

        // Reply
        if (m.reply_to_id != null) {
            val orig = messageMap[m.reply_to_id]
            holder.b.replyContainer.visibility = View.VISIBLE
            holder.b.tvReplyContent.text = when {
                orig == null -> "پیام"
                orig.is_deleted -> "پیام حذف شده"
                else -> (orig.content ?: "[رسانه]").take(80)
            }
        } else {
            holder.b.replyContainer.visibility = View.GONE
        }

        holder.b.ivImage.visibility = View.GONE
        holder.b.fileCard.visibility = View.GONE
        holder.b.voiceRow.visibility = View.GONE
        holder.b.tvContent.visibility = View.GONE

        when (m.message_type) {
            "image" -> {
                holder.b.ivImage.visibility = View.VISIBLE
                val url = fullUrl(m.file_url)
                if (url != null) {
                    holder.b.ivImage.load(url) {
                        crossfade(true)
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.stat_notify_error)
                    }
                    holder.b.ivImage.setOnClickListener {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            }
            "file" -> {
                holder.b.fileCard.visibility = View.VISIBLE
                holder.b.tvFileName.text = m.content ?: "فایل"
                holder.b.tvFileSize.text = "برای دانلود کلیک کنید"
                holder.b.fileCard.setOnClickListener {
                    val url = fullUrl(m.file_url) ?: return@setOnClickListener
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "برنامه‌ای پیدا نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "voice" -> {
                holder.b.voiceRow.visibility = View.VISIBLE
                holder.b.btnPlayVoice.setImageResource(
                    if (currentPlayingId == m.id) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                holder.b.btnPlayVoice.setOnClickListener { togglePlayback(ctx, m) }
            }
            else -> {
                holder.b.tvContent.visibility = View.VISIBLE
                holder.b.tvContent.text = m.content ?: ""
            }
        }

        // Meta
        val meta = buildString {
            if (mine) append("من") else append("#${m.sender_id}")
            if (m.is_edited) append(" · ویرایش‌شده")
            if (m.is_deleted) append(" · حذف‌شده")
        }
        holder.b.tvMeta.text = meta

        if (mine && m.id > 0 && !m.is_deleted) {
            holder.b.tvReadTick.visibility = View.VISIBLE
            if (m.id <= otherLastReadId) {
                holder.b.tvReadTick.text = "✓✓"
                holder.b.tvReadTick.setTextColor(
                    ContextCompat.getColor(ctx, R.color.primary)
                )
            } else {
                holder.b.tvReadTick.text = "✓"
                holder.b.tvReadTick.setTextColor(
                    ContextCompat.getColor(ctx, R.color.text_secondary)
                )
            }
        } else {
            holder.b.tvReadTick.visibility = View.GONE
        }

        holder.b.root.setOnClickListener { onClick(m) }
        holder.b.root.setOnLongClickListener {
            onLongClick(m)
            true
        }
    }

    private fun togglePlayback(ctx: android.content.Context, m: MessageOut) {
        if (currentPlayingId == m.id && currentPlayer?.isPlaying == true) {
            currentPlayer?.pause()
            currentPlayingId = -1
            notifyDataSetChanged()
            return
        }
        stopPlayback()
        val url = fullUrl(m.file_url) ?: return
        try {
            val player = MediaPlayer()
            player.setDataSource(url)
            player.setOnPreparedListener {
                it.start()
                currentPlayingId = m.id
                notifyDataSetChanged()
            }
            player.setOnCompletionListener {
                stopPlayback()
                notifyDataSetChanged()
            }
            player.setOnErrorListener { _, _, _ ->
                Toast.makeText(ctx, "خطا در پخش", Toast.LENGTH_SHORT).show()
                stopPlayback()
                notifyDataSetChanged()
                true
            }
            player.prepareAsync()
            currentPlayer = player
        } catch (e: Exception) {
            Toast.makeText(ctx, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = items.size
}