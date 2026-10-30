package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.writeTx
import java.util.UUID

@Service
class ChatMessageService(
    private val chatMessageRepository: ChatMessageRepository,
    private val transactionManager: PlatformTransactionManager
) {
    @CacheEvict(value = ["chatHistory"], key = "{#child.id, #child.user.id}")
    fun save(
        child: Child,
        userText: String,
        aiText: String,
        id: UUID,
        urgencyLevel: String = "ROUTINE",
        verifiedSources: List<String> = emptyList()
    ) {
        transactionManager.writeTx {
            val existing = chatMessageRepository.findById(id)
            if (existing.isPresent) return@writeTx

            chatMessageRepository.save(
                ChatMessage(
                    child = child,
                    userText = userText,
                    aiText = aiText,
                    urgencyLevel = urgencyLevel,
                    verifiedSources = verifiedSources
                ).apply { setId(id) }
            )
        }
    }
}
