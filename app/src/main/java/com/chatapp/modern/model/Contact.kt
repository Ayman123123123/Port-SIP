package com.chatapp.modern.model

/**
 * Minimal chat/conversation entry shown in the chat list. In a full chat app this
 * would come from a database or a SIP buddy list; here it is a lightweight model
 * so the "call from chat" flow is demonstrable.
 */
data class Contact(
    val id: String,
    val displayName: String,
    val sipAddress: String,
    val lastMessage: String,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)
