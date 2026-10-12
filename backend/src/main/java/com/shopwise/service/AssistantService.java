package com.shopwise.service;

import com.shopwise.ai.AssistantPromptFactory;
import com.shopwise.ai.AssistantRuntime;
import com.shopwise.ai.AssistantToolContext;
import com.shopwise.ai.FallbackResponder;
import com.shopwise.ai.IntentCatalog;
import com.shopwise.ai.IntentClassifier;
import com.shopwise.ai.ModerationService;
import com.shopwise.ai.RateLimitService;
import com.shopwise.ai.ShoppingAssistantTools;
import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.AssistantEvent;
import com.shopwise.domain.ChatMessage;
import com.shopwise.domain.Conversation;
import com.shopwise.exception.ApiException;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.AssistantOnboarding;
import com.shopwise.web.dto.CartView;
import com.shopwise.web.dto.ChatBlock;
import com.shopwise.web.dto.ChatRequest;
import com.shopwise.web.dto.ChatResponse;
import com.shopwise.web.dto.ConversationView;
import com.shopwise.web.dto.MessageView;
import com.shopwise.web.dto.QuickReply;
import com.shopwise.web.dto.SupportDtos.EscalationRequest;
import com.shopwise.web.dto.SupportDtos.EscalationView;
import com.shopwise.web.dto.SupportDtos.FeedbackRequest;
import com.shopwise.web.dto.SupportDtos.FeedbackResponse;
import com.shopwise.web.mapper.ChatMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The assistant conversation loop.
 *
 * <p>Order of operations for every turn is deliberate and each step has a fallback:</p>
 * <ol>
 *   <li>resolve or start the conversation,</li>
 *   <li>honour explicit commands and the escalated state,</li>
 *   <li>rate limit,</li>
 *   <li>screen the inbound message,</li>
 *   <li>classify the intent for analytics and for fallback routing,</li>
 *   <li>call the model with tools, or fall back deterministically,</li>
 *   <li>screen the outbound draft,</li>
 *   <li>persist the turn and record analytics.</li>
 * </ol>
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final Map<String, String> TOOL_LABELS = Map.ofEntries(
            Map.entry("search_products", "Searched the catalogue"),
            Map.entry("get_product_details", "Read the spec sheet"),
            Map.entry("compare_products", "Built a comparison"),
            Map.entry("find_best_value", "Ranked options for the budget"),
            Map.entry("list_categories", "Listed categories"),
            Map.entry("get_cart", "Read your cart"),
            Map.entry("add_to_cart", "Added to your cart"),
            Map.entry("update_cart_quantity", "Updated your cart"),
            Map.entry("remove_from_cart", "Updated your cart"),
            Map.entry("get_order_status", "Looked up the order"),
            Map.entry("list_recent_orders", "Read your order history"),
            Map.entry("get_store_policy", "Checked store policy"),
            Map.entry("request_human_handoff", "Raised a support ticket"));

    private final ConversationService conversationService;
    private final CartService cartService;
    private final EscalationService escalationService;
    private final AnalyticsService analyticsService;
    private final ModerationService moderationService;
    private final RateLimitService rateLimitService;
    private final IntentClassifier intentClassifier;
    private final IntentCatalog intentCatalog;
    private final AssistantPromptFactory promptFactory;
    private final AssistantRuntime runtime;
    private final ShoppingAssistantTools tools;
    private final FallbackResponder fallbackResponder;
    private final ChatMapper chatMapper;
    private final ShopwiseProperties properties;

    public AssistantService(ConversationService conversationService,
                            CartService cartService,
                            EscalationService escalationService,
                            AnalyticsService analyticsService,
                            ModerationService moderationService,
                            RateLimitService rateLimitService,
                            IntentClassifier intentClassifier,
                            IntentCatalog intentCatalog,
                            AssistantPromptFactory promptFactory,
                            AssistantRuntime runtime,
                            ShoppingAssistantTools tools,
                            FallbackResponder fallbackResponder,
                            ChatMapper chatMapper,
                            ShopwiseProperties properties) {
        this.conversationService = conversationService;
        this.cartService = cartService;
        this.escalationService = escalationService;
        this.analyticsService = analyticsService;
        this.moderationService = moderationService;
        this.rateLimitService = rateLimitService;
        this.intentClassifier = intentClassifier;
        this.intentCatalog = intentCatalog;
        this.promptFactory = promptFactory;
        this.runtime = runtime;
        this.tools = tools;
        this.fallbackResponder = fallbackResponder;
        this.chatMapper = chatMapper;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Onboarding and history
    // ------------------------------------------------------------------

    public AssistantOnboarding onboarding(ActorContext actor) {
        String unavailable = runtime.unavailableReason();
        return intentCatalog.onboarding(actor.authenticated() ? firstName(actor.displayName()) : "there",
                unavailable != null, unavailable);
    }

    public ConversationView conversation(ActorContext actor, UUID conversationId) {
        return conversationService.view(actor, conversationId);
    }

    public List<ConversationView> conversations(ActorContext actor) {
        return conversationService.recent(actor);
    }

    // ------------------------------------------------------------------
    // The main loop
    // ------------------------------------------------------------------

    /**
     * Deliberately not transactional: the conversation, cart and audit writes each
     * commit on their own so that a failure while recording analytics can never roll
     * back what the shopper just did.
     */
    public ChatResponse chat(ActorContext actor, ChatRequest request) {
        long turnStarted = System.currentTimeMillis();

        Conversation conversation = conversationService.findOrCreate(actor, request.conversationId());
        if (request.conversationId() == null) {
            analyticsService.record(conversation.getId(), actor.userId(),
                    AssistantEvent.Type.CONVERSATION_STARTED, null, null, "started", null, null);
        }

        String rawMessage = request.message().trim();

        // 1. Explicit commands
        if (rawMessage.startsWith("/")) {
            return handleCommand(actor, conversation, rawMessage);
        }

        // 2. Escalated conversations stay with the human until resumed
        if (conversation.getStatus() == Conversation.ConversationStatus.ESCALATED) {
            EscalationView ticket = escalationService.latestForConversation(conversation.getId());
            String text = ticket == null
                    ? "This conversation is with a human agent."
                    : "This conversation is with a human agent under " + ticket.reference() + ". " + ticket.expectedResponse();
            return persistAndRespond(actor, conversation, text, "human_handoff",
                    List.of(ChatBlock.notice("With a human agent", text, "info")),
                    List.of(QuickReply.action("Continue with the assistant", "/resume"),
                            QuickReply.navigate("Back to shopping", "/shop")),
                    null, 0.9, null, 0, turnStarted, false, null, false);
        }

        // 3. Rate limiting
        RateLimitService.Decision conversationLimit = rateLimitService.checkConversation(conversation.getPublicId().toString());
        RateLimitService.Decision identityLimit = actor.authenticated()
                ? rateLimitService.checkUser(actor.userId())
                : rateLimitService.checkAnonymous(actor.guestToken());

        if (!conversationLimit.allowed() || !identityLimit.allowed()) {
            RateLimitService.Decision blocking = !conversationLimit.allowed() ? conversationLimit : identityLimit;
            analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.RATE_LIMITED,
                    null, null, "throttled", (double) blocking.retryAfterSeconds(), null);
            return persistAndRespond(actor, conversation, blocking.reason(), "rate_limited",
                    List.of(ChatBlock.notice("Slow down a moment", blocking.reason(), "warning")),
                    List.of(QuickReply.send("Talk to a human", "I want to talk to a human")),
                    null, 0.0, null, 0, turnStarted, false, null, false);
        }

        // 4. Message length guard
        if (rawMessage.length() > properties.getAssistant().getMaxMessageLength()) {
            String text = "That message is longer than I can process. Could you shorten it to the essentials?";
            return persistAndRespond(actor, conversation, text, "general_help",
                    List.of(ChatBlock.notice("Message too long", text, "warning")), List.of(),
                    null, 0.0, null, 0, turnStarted, false, null, false);
        }

        // 5. Inbound moderation
        ModerationService.Verdict inbound = moderationService.screenInbound(conversation.getId(), rawMessage);
        if (inbound.blocked()) {
            analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.MODERATION_BLOCKED,
                    "moderation", null, inbound.category(), null, Map.of("category", inbound.category()));
            return persistAndRespond(actor, conversation, inbound.userMessage(), "moderation",
                    List.of(ChatBlock.notice("I cannot help with that", inbound.userMessage(), "warning")),
                    List.of(QuickReply.send("What can you help with?", "What can you help with?"),
                            QuickReply.send("Talk to a human", "I want to talk to a human")),
                    null, 1.0, null, 0, turnStarted, false, inbound.flags(), false);
        }

        String safeMessage = inbound.safeText();

        // 6. Intent classification (before persistence, so memory excludes this turn)
        IntentClassifier.Classification classification = intentClassifier.classify(safeMessage);
        List<Message> history = conversationService.memoryFor(conversation);

        conversationService.append(conversation, ChatMessage.MessageRole.USER, safeMessage,
                classification.intent(), classification.confidence(), null, null, null, null,
                inbound.flags().isEmpty() ? null : inbound.flags());

        analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.MESSAGE_SENT,
                classification.intent(), null, "received", (double) safeMessage.length(),
                Map.of("pageContext", String.valueOf(request.pageContext())));
        analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.INTENT_DETECTED,
                classification.intent(), null, "classified", classification.confidence(), null);

        // 7. Turn budget
        if (conversation.getMessageCount() > properties.getAssistant().getMaxTurnsPerConversation() * 2) {
            String text = "We have covered a lot of ground. Shall I hand you to a colleague who can take it from here?";
            return persistAndRespond(actor, conversation, text, classification.intent(),
                    List.of(ChatBlock.notice("Long conversation", text, "info")),
                    List.of(QuickReply.send("Yes, get me a human", "I want to talk to a human"),
                            QuickReply.send("No, keep going", "Keep helping me")),
                    null, 0.5, null, conversationLimit.remaining(), turnStarted, false, null, false);
        }

        CartView cart = cartService.view(actor);

        // 8. Model call, with deterministic fallback on any problem
        List<ChatBlock> blocks = new ArrayList<>();
        List<String> toolLog = new ArrayList<>();
        Map<String, Object> toolContext = AssistantToolContext.create(actor, conversation.getId(),
                conversation.getPublicId(), blocks, toolLog, request.pageContext());

        String unavailable = runtime.unavailableReason();
        AssistantRuntime.LlmResult result;
        String degradedReason = null;

        if (unavailable != null) {
            result = AssistantRuntime.LlmResult.failed(unavailable, runtime.modelName(), 0);
            degradedReason = unavailable;
        } else {
            String systemPrompt = promptFactory.build(actor, cart, request.pageContext(), classification);
            result = runtime.converse(systemPrompt, history, safeMessage, tools, toolContext);

            // Grounding guard: a catalogue question answered from memory rather than from the
            // catalogue is the one failure that can put a wrong price in front of a shopper.
            // One nudge is enough; if the model still will not use a tool we keep its prose and
            // let the deterministic path below decide what to do.
            if (result.ok() && blocks.isEmpty() && needsGrounding(classification.intent())) {
                log.info("Assistant answered a {} turn without a tool call; retrying once with a nudge",
                        classification.intent());
                AssistantRuntime.LlmResult retry = runtime.converse(
                        systemPrompt + "\n\nIMPORTANT: You answered without calling a tool. Call the tool that "
                                + "matches this request now, and answer only from its result.",
                        history, safeMessage, tools, toolContext);
                if (retry.ok()) {
                    result = retry;
                }
            }

            if (!result.ok()) {
                degradedReason = result.failureReason();
            }
        }

        String replyText;
        List<ChatBlock> replyBlocks = new ArrayList<>(blocks);
        List<QuickReply> quickReplies = new ArrayList<>();
        boolean escalated = false;
        boolean degraded = degradedReason != null;

        if (result.ok()) {
            replyText = result.text();
            quickReplies.addAll(collectReplies(replyBlocks));
        } else if (result.empty() && !replyBlocks.isEmpty()) {
            // The tools did real work but the model added no prose. Discarding a correct
            // comparison table because of a missing sentence would be the worst outcome,
            // so the results stay and the prose is written deterministically instead.
            analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.FALLBACK_TRIGGERED,
                    classification.intent(), null, "no_commentary", null, Map.of("reason", "empty_model_answer"));
            replyText = narrate(replyBlocks);
            quickReplies.addAll(collectReplies(replyBlocks));
            degraded = true;
            degradedReason = result.failureReason();
        } else {
            analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.FALLBACK_TRIGGERED,
                    classification.intent(), null, "fallback", null, Map.of("reason", degradedReason));
            analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.ASSISTANT_ERROR,
                    classification.intent(), null, "degraded", null, Map.of("reason", degradedReason));

            FallbackResponder.Answer answer = fallbackResponder.respond(actor, conversation, safeMessage,
                    classification, request.pageContext(), degradedReason);
            replyText = answer.text();
            replyBlocks = new ArrayList<>(answer.blocks());
            quickReplies.addAll(answer.quickReplies());
            escalated = answer.escalated();
            replyBlocks.add(0, ChatBlock.notice("Offline answer",
                    degradedReason + " I answered from the catalogue rules instead.", "warning"));
        }

        // 9. Outbound moderation
        ModerationService.Verdict outbound = moderationService.screenOutbound(conversation.getId(), replyText);
        if (outbound.changed()) {
            replyText = outbound.safeText();
        }

        // 10. Analytics for the turn
        for (String toolName : new LinkedHashSet<>(toolLog)) {
            analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.TOOL_CALLED,
                    classification.intent(), toolName, "ok", null, null);
        }
        analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.MESSAGE_ANSWERED,
                classification.intent(), toolLog.isEmpty() ? null : toolLog.getFirst(), degraded ? "degraded" : "answered",
                (double) (System.currentTimeMillis() - turnStarted), Map.of("degraded", degraded));

        List<MessageView.ToolCallView> toolCallViews = toolLog.stream()
                .distinct()
                .map(name -> new MessageView.ToolCallView(name, TOOL_LABELS.getOrDefault(name, name), "ok", null))
                .toList();

        ChatMessage saved = conversationService.append(conversation, ChatMessage.MessageRole.ASSISTANT, replyText,
                classification.intent(), classification.confidence(),
                result.ok() ? result.model() : "deterministic-fallback",
                (int) (System.currentTimeMillis() - turnStarted),
                replyBlocks, toolCallViews, outbound.flags().isEmpty() ? null : outbound.flags());

        return new ChatResponse(
                conversation.getPublicId(),
                conversation.getTitle(),
                conversation.getStatus().name(),
                chatMapper.toView(saved),
                dedupe(quickReplies),
                classification.intent(),
                classification.confidence(),
                degraded,
                degradedReason,
                result.ok() ? result.model() : "deterministic-fallback",
                System.currentTimeMillis() - turnStarted,
                Math.min(conversationLimit.remaining(), Math.max(identityLimit.remaining(), 0)),
                escalated);
    }

    // ------------------------------------------------------------------
    // Commands, escalation and feedback
    // ------------------------------------------------------------------

    private ChatResponse handleCommand(ActorContext actor, Conversation conversation, String command) {
        long started = System.currentTimeMillis();
        String normalized = command.toLowerCase().trim();

        if (normalized.startsWith("/resume")) {
            conversationService.resume(conversation);
            return persistAndRespond(actor, conversation,
                    "Back with you. The assistant is handling this conversation again.",
                    "general_help", List.of(ChatBlock.notice("Resumed", "The assistant is handling this conversation again.", "info")),
                    List.of(QuickReply.send("Find me a laptop under $1500", "Find me a laptop under $1500")),
                    null, 0.9, null, 0, started, false, null, false);
        }
        if (normalized.startsWith("/end")) {
            conversationService.close(conversation);
            return persistAndRespond(actor, conversation, "Conversation closed. You can start a new one any time.",
                    "general_help", List.of(ChatBlock.notice("Closed", "This conversation is closed.", "info")),
                    List.of(QuickReply.navigate("Back to shopping", "/shop")),
                    null, 0.9, null, 0, started, false, null, false);
        }
        if (normalized.startsWith("/help")) {
            return persistAndRespond(actor, conversation,
                    "Commands: /help for this list, /end to close the conversation, /resume to take it back from a human.",
                    "general_help", List.of(), List.of(), null, 0.9, null, 0, started, false, null, false);
        }
        return persistAndRespond(actor, conversation,
                "I do not know that command. Try /help.", "general_help", List.of(), List.of(),
                null, 0.5, null, 0, started, false, null, false);
    }

    public EscalationView escalate(ActorContext actor, UUID conversationId, EscalationRequest request) {
        Conversation conversation = conversationService.findOrCreate(actor, conversationId);
        EscalationView ticket = escalationService.escalate(actor, conversation, request.reason(), request.note(),
                request.email(), Boolean.TRUE.equals(request.urgent()));

        conversationService.append(conversation, ChatMessage.MessageRole.SYSTEM,
                "Conversation escalated to a human agent as " + ticket.reference() + ".",
                "human_handoff", 1.0, null, null,
                List.of(ChatBlock.of(ChatBlock.TYPE_ESCALATION, "Handed to a human", ticket.reference(),
                        ticket.expectedResponse(), Map.of("ticket", ticket), null)),
                null, null);

        analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.ESCALATION_REQUESTED,
                "human_handoff", "request_human_handoff", ticket.priority(), null,
                Map.of("reference", ticket.reference(), "reason", ticket.reason()));
        return ticket;
    }

    public FeedbackResponse feedback(ActorContext actor, UUID conversationId, FeedbackRequest request) {
        Conversation conversation = conversationService.findOrCreate(actor, conversationId);
        conversationService.assertOwnership(conversation, actor);

        String rating = request.rating().trim().toLowerCase();
        boolean positive = rating.startsWith("up") || rating.startsWith("good") || rating.startsWith("help");
        analyticsService.record(conversation.getId(), actor.userId(),
                AssistantEvent.Type.FEEDBACK_RECEIVED + (positive ? "_UP" : "_DOWN"),
                conversation.getLastIntent(), null, rating, null,
                request.comment() == null ? null : Map.of("comment", request.comment(),
                        "messageId", String.valueOf(request.messageId())));

        return new FeedbackResponse(rating,
                positive ? "Thanks — that helps me do more of it." : "Thanks for the signal. I will pass this to the team.");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private ChatResponse persistAndRespond(ActorContext actor,
                                           Conversation conversation,
                                           String text,
                                           String intent,
                                           List<ChatBlock> blocks,
                                           List<QuickReply> quickReplies,
                                           String model,
                                           double confidence,
                                           String degradedReason,
                                           int remaining,
                                           long started,
                                           boolean escalated,
                                           Map<String, Object> safetyFlags,
                                           boolean degraded) {

        ChatMessage saved = conversationService.append(conversation, ChatMessage.MessageRole.ASSISTANT, text,
                intent, confidence, model, (int) (System.currentTimeMillis() - started),
                blocks, null, safetyFlags);

        analyticsService.record(conversation.getId(), actor.userId(), AssistantEvent.Type.MESSAGE_ANSWERED,
                intent, null, degraded ? "degraded" : "answered",
                (double) (System.currentTimeMillis() - started), null);

        return new ChatResponse(
                conversation.getPublicId(),
                conversation.getTitle(),
                conversation.getStatus().name(),
                chatMapper.toView(saved),
                dedupe(quickReplies),
                intent,
                confidence,
                degraded,
                degradedReason,
                model,
                System.currentTimeMillis() - started,
                Math.max(remaining, 0),
                escalated);
    }

    private List<QuickReply> collectReplies(List<ChatBlock> blocks) {
        List<QuickReply> replies = new ArrayList<>();
        for (ChatBlock block : blocks) {
            if (block.actions() != null) {
                replies.addAll(block.actions());
            }
        }
        return replies;
    }

    /**
     * Writes the sentence the model did not, describing whatever the tools returned.
     * Kept deliberately short: the blocks carry the detail.
     */
    private String narrate(List<ChatBlock> blocks) {
        if (hasBlock(blocks, ChatBlock.TYPE_COMPARISON)) {
            return "Here is the side-by-side. The rows marked \"differs\" are where they diverge — "
                    + "tell me which trade-off matters more and I will pick one.";
        }
        if (hasBlock(blocks, ChatBlock.TYPE_PRODUCT_CARDS)) {
            return "Here is what matched. Ask me to compare any two, or say which one to look at in detail.";
        }
        if (hasBlock(blocks, ChatBlock.TYPE_ORDER_CARD)) {
            return "Here is the order as it stands.";
        }
        if (hasBlock(blocks, ChatBlock.TYPE_CART_SUMMARY)) {
            return "Here is your cart as it stands.";
        }
        return "Here is what I found.";
    }

    /**
     * True for intents whose answers must come from the catalogue rather than from the model's
     * own memory. Used to decide whether a tool-free answer is acceptable.
     */
    private boolean needsGrounding(String intent) {
        return switch (intent == null ? "" : intent) {
            case "product_search", "product_compare", "product_recommend",
                 "product_details", "stock_check", "price_check" -> true;
            default -> false;
        };
    }

    private boolean hasBlock(List<ChatBlock> blocks, String type) {
        return blocks.stream().anyMatch(block -> type.equals(block.type()));
    }

    private List<QuickReply> dedupe(List<QuickReply> replies) {
        Map<String, QuickReply> unique = new LinkedHashMap<>();
        for (QuickReply reply : replies) {
            if (reply == null || reply.label() == null || reply.label().isBlank()) {
                continue;
            }
            unique.putIfAbsent(reply.label(), reply);
        }
        return unique.values().stream().limit(4).toList();
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    public boolean llmAvailable() {
        return runtime.unavailableReason() == null;
    }

    public String llmUnavailableReason() {
        return runtime.unavailableReason();
    }

    public String modelName() {
        return runtime.modelName();
    }

    public void logUnexpected(String context, Exception ex) {
        log.error("Assistant failure while {}", context, ex);
    }

    public ApiException unsupported(String message) {
        return ApiException.badRequest(message);
    }
}
