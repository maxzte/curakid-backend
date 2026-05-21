package com.misterio.curakid.core.database.chat

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.children.Child
import jakarta.persistence.*

@Entity
@Table(name = "chat_messages")
class ChatMessage(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Column(name = "user_text", columnDefinition = "TEXT", nullable = false)
    var userText: String = "",

    @Column(name = "ai_text", columnDefinition = "TEXT", nullable = false)
    var aiText: String = ""

) : BaseEntity()
