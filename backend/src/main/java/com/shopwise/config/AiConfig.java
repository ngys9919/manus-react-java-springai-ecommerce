package com.shopwise.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring AI {@link ChatClient} used by the shopping assistant.
 *
 * <p>Model, temperature and token budget come from
 * {@code spring.ai.openai.chat.options.*} so the deployment can be re-pointed at a
 * different OpenAI-compatible endpoint without touching code. The system prompt,
 * conversation memory and tools are supplied per request, because all three depend
 * on who is asking.</p>
 *
 * <p>Retry behaviour is bounded by {@code spring.ai.retry.*}: the assistant must fail
 * fast into its deterministic fallback rather than keep a shopper waiting.</p>
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient shoppingAssistantChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are the Shopwise shopping assistant.")
                .build();
    }
}