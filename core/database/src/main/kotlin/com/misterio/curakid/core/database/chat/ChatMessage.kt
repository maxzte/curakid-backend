package com.misterio.curakid.core.database.chat

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.children.Child
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "chat_messages")
@SQLRestriction("deleted_at IS NULL")
class ChatMessage(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Column(name = "user_text", columnDefinition = "TEXT", nullable = false)
    var userText: String = "",

    @Column(name = "ai_text", columnDefinition = "TEXT", nullable = false)
    var aiText: String = ""

) : BaseEntity()
