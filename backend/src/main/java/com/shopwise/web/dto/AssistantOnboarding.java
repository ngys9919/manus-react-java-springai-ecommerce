package com.shopwise.web.dto;

import java.util.List;

/**
 * Everything the chat panel needs to render a useful empty state: the greeting,
 * the supported intents with sample phrasings and sample answers, and the limits
 * the assistant is honest about.
 */
public record AssistantOnboarding(
        String greeting,
        String subGreeting,
        String availability,
        boolean degraded,
        String degradedReason,
        List<IntentCard> intents,
        List<String> starterPrompts,
        List<String> capabilities,
        List<String> limitations,
        String handoffNote,
        String supportEmail) {

    public record IntentCard(
            String id,
            String label,
            String description,
            String icon,
            List<String> sampleUtterances,
            String exampleResponse,
            boolean requiresSignIn) {
    }
}