package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatMessageService(
    private val chatMessageRepository: ChatMessageRepository,
) {
    @Transactional
    fun save(child: Child, userText: String, aiText: String) {
        chatMessageRepository.save(
            ChatMessage(
                child = child,
                userText = userText,
                aiText = aiText
            )
        )
    }
}
