package com.gemmacode.assistant

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon

data class ChatMessage(val content: String, val isUser: Boolean)

class ChatAdapter(private val markwon: Markwon) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String) {
        if (messages.isNotEmpty()) {
            messages[messages.lastIndex] = messages.last().copy(content = content)
            notifyItemChanged(messages.lastIndex)
        }
    }

    override fun getItemViewType(p: Int) = if (messages[p].isUser) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == 0) R.layout.item_message_user else R.layout.item_message_bot
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        if (msg.isUser) holder.tvMessage.text = msg.content
        else markwon.setMarkdown(holder.tvMessage, msg.content)
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMessage: TextView = view.findViewById(R.id.tvMessage)
    }
}
