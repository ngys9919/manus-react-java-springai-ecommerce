package com.shopwise.web.controller;

import com.shopwise.security.ActorResolver;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.service.AssistantService;
import com.shopwise.web.dto.AssistantOnboarding;
import com.shopwise.web.dto.ChatRequest;
import com.shopwise.web.dto.ChatResponse;
import com.shopwise.web.dto.ConversationView;
import com.shopwise.web.dto.SupportDtos.EscalationRequest;
import com.shopwise.web.dto.SupportDtos.EscalationView;
import com.shopwise.web.dto.SupportDtos.FeedbackRequest;
import com.shopwise.web.dto.SupportDtos.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shopping assistant. Works for guests and signed-in shoppers alike; send
 * {@code X-Guest-Token} to keep a conversation across reloads without an account.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "Spring AI shopping assistant: chat, onboarding, escalation and feedback")
public class AssistantController {

    private final AssistantService assistantService;
    private final ActorResolver actorResolver;

    public AssistantController(AssistantService assistantService, ActorResolver actorResolver) {
        this.assistantService = assistantService;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/onboarding")
    @Operation(summary = "Welcome text, supported intents with sample answers, and honest limits")
    public AssistantOnboarding onboarding(HttpServletRequest request) {
        return assistantService.onboarding(actorResolver.resolve(request));
    }

    @GetMapping("/status")
    @Operation(summary = "Whether the assistant is running on the model or the offline fallback")
    public Map<String, Object> status() {
        boolean available = assistantService.llmAvailable();
        return Map.of(
                "mode", available ? "MODEL" : "FALLBACK",
                "model", assistantService.modelName(),
                "available", available,
                "reason", available ? "" : String.valueOf(assistantService.llmUnavailableReason()));
    }

    @PostMapping("/chat")
    @Operation(summary = "Send one shopper turn and receive a reply with rich blocks and quick replies")
    public ChatResponse chat(HttpServletRequest request, @Valid @RequestBody ChatRequest body) {
        return assistantService.chat(actorResolver.resolve(request), body);
    }

    @GetMapping("/conversations")
    @Operation(summary = "Recent conversations for the current identity")
    public List<ConversationView> conversations(HttpServletRequest request) {
        return assistantService.conversations(actorResolver.resolve(request));
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Full transcript of one conversation")
    public ConversationView conversation(HttpServletRequest request, @PathVariable UUID conversationId) {
        return assistantService.conversation(actorResolver.resolve(request), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/escalate")
    @Operation(summary = "Hand the conversation to a human agent")
    public EscalationView escalate(HttpServletRequest request,
                                   @PathVariable UUID conversationId,
                                   @Valid @RequestBody EscalationRequest body) {
        return assistantService.escalate(actorResolver.resolve(request), conversationId, body);
    }

    @PostMapping("/conversations/{conversationId}/feedback")
    @Operation(summary = "Rate the last answer; feeds the satisfaction metric")
    public FeedbackResponse feedback(HttpServletRequest request,
                                     @PathVariable UUID conversationId,
                                     @Valid @RequestBody FeedbackRequest body) {
        return assistantService.feedback(actorResolver.resolve(request), conversationId, body);
    }
}