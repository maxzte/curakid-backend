package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.currentUser
import org.springframework.security.core.Authentication
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.publisher.Flux
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

