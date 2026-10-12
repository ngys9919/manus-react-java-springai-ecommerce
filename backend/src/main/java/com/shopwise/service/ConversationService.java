package com.shopwise.service;

import com.shopwise.config.ShopwiseProperties;
import com.shopwise.domain.ChatMessage;
import com.shopwise.domain.Conversation;
import com.shopwise.exception.ApiException;
import com.shopwise.repository.ChatMessageRepository;
import com.shopwise.repository.ConversationRepository;
import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.ChatBlock;
import com.shopwise.web.dto.ConversationView;
import com.shopwise.web.dto.MessageView;
import com.shopwise.web.mapper.ChatMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conversation persistence.
 *
 * <p>Every mutating method reloads the managed row before changing it and then copies
 * the result back onto the caller's instance. The assistant loop runs outside a single
 * transaction, so this is what keeps a stale in-memory copy from overwriting state a
 * tool or an escalation just changed.</p>
 */
@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMapper chatMapper;
    private final ShopwiseProperties properties;

    public ConversationService(ConversationRepository conversationRepository,
                               ChatMessageRepository chatMessageRepository,
                               ChatMapper chatMapper,
                               ShopwiseProperties properties) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMapper = chatMapper;
        this.properties = properties;
    }

    @Transactional
    public Conversation findOrCreate(ActorContext actor, UUID publicId) {
        if (publicId == null) {
            return conversationRepository.save(Conversation.builder()
                    .userId(actor.userId())
                    .guestToken(actor.guestToken())
                    .title("New conversation")
                    .build());
        }
        Conversation conversation = conversationRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Conversation " + publicId));
        assertOwnership(conversation, actor);
        return conversation;
    }

    /** Ownership check: a conversation is only readable by the identity that started it. */
    public void assertOwnership(Conversation conversation, ActorContext actor) {
        boolean ownedByUser = actor.userId() != null && actor.userId().equals(conversation.getUserId());
        boolean ownedByGuest = actor.userId() == null
                && actor.guestToken() != null
                && actor.guestToken().equals(conversation.getGuestToken());
        if (!ownedByUser && !ownedByGuest && !actor.staff()) {
            throw ApiException.forbidden("This conversation belongs to a different session.");
        }
    }

    @Transactional
    public ChatMessage append(Conversation conversation,
                              ChatMessage.MessageRole role,
                              String content,
                              String intent,
                              Double confidence,
                              String model,
                              Integer latencyMs,
                              List<ChatBlock> blocks,
                              List<MessageView.ToolCallView> toolCalls,
                              Map<String, Object> safetyFlags) {

        Conversation managed = conversationRepository.findById(conversation.getId())
                .orElseThrow(() -> ApiException.notFound("Conversation " + conversation.getId()));

        ChatMessage message = ChatMessage.builder()
                .conversation(managed)
                .role(role)
                .content(content == null ? "" : content)
                .intent(intent)
                .confidence(confidence == null ? null : BigDecimal.valueOf(confidence))
                .model(model)
                .latencyMs(latencyMs)
                .blocks(chatMapper.toStoredBlocks(blocks))
                .toolCalls(chatMapper.toStoredToolCalls(toolCalls))
                .safetyFlags(safetyFlags == null || safetyFlags.isEmpty() ? null : safetyFlags)
                .build();

        ChatMessage saved = chatMessageRepository.saveAndFlush(message);

        managed.setMessageCount(managed.getMessageCount() + 1);
        managed.setLastMessageAt(saved.getCreatedAt());
        if (intent != null) {
            managed.setLastIntent(intent);
        }
        if (role == ChatMessage.MessageRole.USER && managed.getTitle().startsWith("New conversation")) {
            managed.setTitle(titleFrom(content));
        }
        conversationRepository.saveAndFlush(managed);
        copyState(managed, conversation);
        return saved;
    }

    @Transactional(readOnly = true)
    public ConversationView view(ActorContext actor, UUID publicId) {
        Conversation conversation = conversationRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Conversation " + publicId));
        assertOwnership(conversation, actor);
        return chatMapper.toView(conversation,
                chatMessageRepository.findByConversationIdOrderByIdAsc(conversation.getId()));
    }

    @Transactional(readOnly = true)
    public List<ConversationView> recent(ActorContext actor) {
        List<Conversation> conversations = actor.authenticated()
                ? conversationRepository.findTop20ByUserIdOrderByLastMessageAtDesc(actor.userId())
                : actor.guestToken() == null
                        ? List.of()
                        : conversationRepository.findTop20ByGuestTokenOrderByLastMessageAtDesc(actor.guestToken());
        return conversations.stream()
                .map(conversation -> chatMapper.toView(conversation, List.of()))
                .toList();
    }

    /**
     * Rebuilds the model's short-term memory from the database. Only user and final
     * assistant turns are replayed; tool chatter is not, because the model already saw
     * the results and the transcript stays smaller and cheaper.
     */
    @Transactional(readOnly = true)
    public List<Message> memoryFor(Conversation conversation) {
        int window = properties.getAssistant().getMemoryWindow();
        List<ChatMessage> recent = new ArrayList<>(
                chatMessageRepository.findTop20ByConversationIdOrderByIdDesc(conversation.getId()));
        Collections.reverse(recent);

        List<Message> messages = new ArrayList<>();
        for (ChatMessage message : recent) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            if (message.getRole() == ChatMessage.MessageRole.USER) {
                messages.add(new UserMessage(message.getContent()));
            } else if (message.getRole() == ChatMessage.MessageRole.ASSISTANT) {
                messages.add(new AssistantMessage(message.getContent()));
            }
        }
        // Keep the most recent turns, but always start the window on a user turn.
        if (messages.size() > window) {
            messages = new ArrayList<>(messages.subList(messages.size() - window, messages.size()));
            while (!messages.isEmpty() && !(messages.getFirst() instanceof UserMessage)) {
                messages.removeFirst();
            }
        }
        return messages;
    }

    @Transactional
    public void close(Conversation conversation) {
        setStatus(conversation, Conversation.ConversationStatus.CLOSED);
    }

    @Transactional
    public void markEscalated(Conversation conversation) {
        setStatus(conversation, Conversation.ConversationStatus.ESCALATED);
    }

    @Transactional
    public void resume(Conversation conversation) {
        setStatus(conversation, Conversation.ConversationStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public String transcriptExcerpt(Long conversationId, int maxChars) {
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderByIdAsc(conversationId);
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            builder.append(message.getRole() == ChatMessage.MessageRole.USER ? "Shopper: " : "Assistant: ")
                    .append(message.getContent())
                    .append('\n');
        }
        String transcript = builder.toString().trim();
        return transcript.length() <= maxChars ? transcript : transcript.substring(transcript.length() - maxChars);
    }

    private void setStatus(Conversation conversation, Conversation.ConversationStatus status) {
        conversationRepository.findById(conversation.getId()).ifPresent(managed -> {
            managed.setStatus(status);
            conversationRepository.saveAndFlush(managed);
            copyState(managed, conversation);
        });
    }

    /** Mirrors the freshly persisted row back onto the caller's detached instance. */
    private void copyState(Conversation source, Conversation target) {
        target.setStatus(source.getStatus());
        target.setMessageCount(source.getMessageCount());
        target.setTitle(source.getTitle());
        target.setLastIntent(source.getLastIntent());
        target.setLastMessageAt(source.getLastMessageAt());
    }

    private String titleFrom(String content) {
        String cleaned = content.strip().replaceAll("\\s+", " ");
        return cleaned.length() <= 60 ? cleaned : cleaned.substring(0, 57) + "...";
    }
}