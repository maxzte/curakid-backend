package com.misterio.curakid.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringAiConfig {

    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient {
        return ChatClient.builder(chatModel)
            .defaultSystem(
                "Ти — медичний асистент для батьків. " +
                "Завжди відповідай українською мовою. " +
                "Надавай точні, доказові медичні поради відповідно до стандартів ВООЗ."
            )
            .build()
    }
}
