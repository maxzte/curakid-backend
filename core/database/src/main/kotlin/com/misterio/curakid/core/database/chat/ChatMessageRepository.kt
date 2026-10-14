package com.misterio.curakid.core.database.chat

import com.misterio.curakid.core.database.children.Child
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {

    @Cacheable(value = ["chatHistory"], key = "#child.id")
    fun findAllByChildOrderByCreatedAtAsc(child: Child): List<ChatMessage>

    fun findAllByChildAndCreatedAtAfterOrderByCreatedAtAsc(child: Child, after: Instant): List<ChatMessage>

}
