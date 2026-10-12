package com.shopwise.web.mapper;

import com.shopwise.domain.ChatMessage;
import com.shopwise.domain.Conversation;
import com.shopwise.web.dto.ChatBlock;
import com.shopwise.web.dto.ConversationView;
import com.shopwise.web.dto.MessageView;
import com.shopwise.web.dto.QuickReply;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between the persisted JSON representation of a message and the DTO the
 * client renders.
 *
 * <p>The conversion is written out explicitly rather than delegated to an object
 * mapper: the stored document stays a neutral {@code Map}/JSON structure, the domain
 * layer keeps no dependency on view types, and a change to a view record cannot
 * silently change what is stored.</p>
 */
@Component
public class ChatMapper {

    private static final String KEY_TYPE = "type";
    private static final String KEY_TITLE = "title";
    private static final String KEY_SUBTITLE = "subtitle";
    private static final String KEY_BODY = "body";
    private static final String KEY_DATA = "data";
    private static final String KEY_ACTIONS = "actions";
    private static final String KEY_LABEL = "label";
    private static final String KEY_VALUE = "value";
    private static final String KEY_KIND = "kind";
    private static final String KEY_NAME = "name";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ARGUMENTS = "arguments";

    public List<Map<String, Object>> toStoredBlocks(List<ChatBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> stored = new ArrayList<>(blocks.size());
        for (ChatBlock block : blocks) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(KEY_TYPE, block.type());
            putIfPresent(map, KEY_TITLE, block.title());
            putIfPresent(map, KEY_SUBTITLE, block.subtitle());
            putIfPresent(map, KEY_BODY, block.body());
            if (block.data() != null && !block.data().isEmpty()) {
                map.put(KEY_DATA, block.data());
            }
            if (block.actions() != null && !block.actions().isEmpty()) {
                List<Map<String, Object>> actions = new ArrayList<>(block.actions().size());
                for (QuickReply reply : block.actions()) {
                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put(KEY_LABEL, reply.label());
                    action.put(KEY_VALUE, reply.value());
                    action.put(KEY_KIND, reply.kind());
                    actions.add(action);
                }
                map.put(KEY_ACTIONS, actions);
            }
            stored.add(map);
        }
        return stored;
    }

    public List<Map<String, Object>> toStoredToolCalls(List<MessageView.ToolCallView> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> stored = new ArrayList<>(toolCalls.size());
        for (MessageView.ToolCallView toolCall : toolCalls) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(KEY_NAME, toolCall.name());
            putIfPresent(map, KEY_LABEL, toolCall.label());
            putIfPresent(map, KEY_STATUS, toolCall.status());
            if (toolCall.arguments() != null && !toolCall.arguments().isEmpty()) {
                map.put(KEY_ARGUMENTS, toolCall.arguments());
            }
            stored.add(map);
        }
        return stored;
    }

    public MessageView toView(ChatMessage message) {
        List<ChatBlock> blocks = new ArrayList<>();
        if (message.getBlocks() != null) {
            for (Map<String, Object> raw : message.getBlocks()) {
                blocks.add(blockFrom(raw));
            }
        }
        List<MessageView.ToolCallView> toolCalls = new ArrayList<>();
        if (message.getToolCalls() != null) {
            for (Map<String, Object> raw : message.getToolCalls()) {
                toolCalls.add(toolCallFrom(raw));
            }
        }
        return new MessageView(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getIntent(),
                message.getConfidence(),
                message.getCreatedAt(),
                blocks,
                toolCalls,
                message.getSafetyFlags(),
                message.getModel(),
                message.getLatencyMs());
    }

    public ConversationView toView(Conversation conversation, List<ChatMessage> messages) {
        return new ConversationView(
                conversation.getPublicId(),
                conversation.getTitle(),
                conversation.getStatus().name(),
                conversation.getLastIntent(),
                conversation.getMessageCount(),
                conversation.getCreatedAt(),
                conversation.getLastMessageAt(),
                messages.stream().map(this::toView).toList());
    }

    @SuppressWarnings("unchecked")
    private ChatBlock blockFrom(Map<String, Object> raw) {
        List<QuickReply> actions = new ArrayList<>();
        Object rawActions = raw.get(KEY_ACTIONS);
        if (rawActions instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> action) {
                    actions.add(new QuickReply(
                            stringOrNull(action.get(KEY_LABEL)),
                            stringOrNull(action.get(KEY_VALUE)),
                            stringOrNull(action.get(KEY_KIND))));
                }
            }
        }
        Map<String, Object> data = raw.get(KEY_DATA) instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;

        return new ChatBlock(
                stringOrNull(raw.get(KEY_TYPE)),
                stringOrNull(raw.get(KEY_TITLE)),
                stringOrNull(raw.get(KEY_SUBTITLE)),
                stringOrNull(raw.get(KEY_BODY)),
                data,
                actions.isEmpty() ? null : actions);
    }

    @SuppressWarnings("unchecked")
    private MessageView.ToolCallView toolCallFrom(Map<String, Object> raw) {
        Map<String, Object> arguments = raw.get(KEY_ARGUMENTS) instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;
        return new MessageView.ToolCallView(
                stringOrNull(raw.get(KEY_NAME)),
                stringOrNull(raw.get(KEY_LABEL)),
                stringOrNull(raw.get(KEY_STATUS)),
                arguments);
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}