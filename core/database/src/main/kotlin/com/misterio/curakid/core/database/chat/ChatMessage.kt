package com.misterio.curakid.core.database.chat

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.children.Child
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "chat_messages",
    indexes = [
        Index(name = "idx_chat_messages_child_created", columnList = "child_id, created_at")
    ]
)
@SQLRestriction("deleted_at IS NULL")
class ChatMessage(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Column(name = "user_text", columnDefinition = "TEXT", nullable = false)
    var userText: String = "",

    @Column(name = "ai_text", columnDefinition = "TEXT", nullable = false)
    var aiText: String = "",

    @Column(name = "urgency_level", nullable = false)
    var urgencyLevel: String = "ROUTINE",

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "chat_message_sources", joinColumns = [JoinColumn(name = "message_id")])
    @Column(name = "source_title", columnDefinition = "TEXT")
    @BatchSize(size = 20)
    var verifiedSources: List<String> = emptyList()

) : BaseEntity()
