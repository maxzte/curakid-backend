package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ChatMessageService(
    private val chatMessageRepository: ChatMessageRepository,
) {
    @CacheEvict(value = ["chatHistory"], key = "#child.id")
    fun save(child: Child, userText: String, aiText: String, id: UUID) {
        val existing = chatMessageRepository.findById(id)
        if (existing.isPresent) return

        chatMessageRepository.save(
            ChatMessage(
                child = child,
                userText = userText,
                aiText = aiText
            ).apply { setId(id) }
        )
    }
}
