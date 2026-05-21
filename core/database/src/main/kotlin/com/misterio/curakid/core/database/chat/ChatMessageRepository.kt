package com.misterio.curakid.core.database.chat

import com.misterio.curakid.core.database.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {
    fun findAllByChildOrderByCreatedAtAsc(child: Child): List<ChatMessage>
}
