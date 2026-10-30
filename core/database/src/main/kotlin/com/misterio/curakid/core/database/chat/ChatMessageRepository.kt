package com.misterio.curakid.core.database.chat

import com.misterio.curakid.core.database.children.Child
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {

    fun findAllByChildOrderByCreatedAtAsc(child: Child): List<ChatMessage>

    fun findAllByChildAndCreatedAtAfterOrderByCreatedAtAsc(child: Child, after: Instant): List<ChatMessage>

    fun findAllByChildOrderByCreatedAtDesc(child: Child, pageable: Pageable): Page<ChatMessage>

}
