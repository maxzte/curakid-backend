package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.currentUser
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService
) {

    @PostMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamChat(
        @Valid @RequestBody request: ChatRequest,
        auth: Authentication
    ): SseEmitter {
        return chatService.streamChatEvents(request, auth.currentUser())
    }

    @GetMapping("/history/{childId}")
    fun getChatHistory(
        @PathVariable childId: UUID,
        auth: Authentication
    ): List<ChatMessageResponse> {
        return chatService.getChatHistory(childId, auth.currentUser())
    }
}

