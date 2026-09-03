package com.chatapp.modern.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chatapp.modern.R
import com.chatapp.modern.databinding.ItemContactBinding
import com.chatapp.modern.model.Contact

/**
 * Recycler adapter for the chat/conversation list. Each row exposes a call button
 * that wires back to the host fragment so the imported Call UI can be launched.
 */
class ContactAdapter(
    private val items: List<Contact>,
    private val onRowClick: (Contact) -> Unit,
    private val onCallClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = items[position]
        val b = holder.binding

        b.contactName.text = contact.displayName
        b.contactLastMessage.text = contact.lastMessage
        b.contactStatus.text = holder.itemView.context.getString(
            if (contact.isOnline) R.string.online else R.string.offline
        )
        b.contactStatus.visibility = if (contact.isOnline) View.VISIBLE else View.GONE

        b.root.setOnClickListener { onRowClick(contact) }
        b.actionCall.setOnClickListener { onCallClick(contact) }
    }

    override fun getItemCount(): Int = items.size

    class ContactViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)
}
