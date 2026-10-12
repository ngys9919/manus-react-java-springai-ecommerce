package com.shopwise.ai;

import com.shopwise.security.ActorResolver.ActorContext;
import com.shopwise.web.dto.ChatBlock;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

/**
 * Bridges per-request state into {@code @Tool} methods.
 *
 * <p>Spring AI passes a {@link ToolContext} map to every tool call, which gives the
 * tools three things they cannot otherwise know: who is asking (for permission
 * gates), which conversation they are in (for auditing), and a mutable sink where a
 * tool can drop a rich UI block for the client to render.</p>
 */
public final class AssistantToolContext {

    public static final String KEY_ACTOR = "actor";
    public static final String KEY_CONVERSATION_ID = "conversationId";
    public static final String KEY_CONVERSATION_PUBLIC_ID = "conversationPublicId";
    public static final String KEY_BLOCKS = "blocks";
    public static final String KEY_PAGE_CONTEXT = "pageContext";
    public static final String KEY_TOOL_LOG = "toolLog";

    private AssistantToolContext() {
    }

    public static Map<String, Object> create(ActorContext actor,
                                             Long conversationId,
                                             java.util.UUID conversationPublicId,
                                             List<ChatBlock> blockSink,
                                             List<String> toolLog,
                                             String pageContext) {
        Map<String, Object> context = new java.util.LinkedHashMap<>();
        // Spring AI rejects a tool context containing null values, so absent state is
        // omitted rather than stored as null.
        putIfNotNull(context, KEY_ACTOR, actor);
        putIfNotNull(context, KEY_CONVERSATION_ID, conversationId);
        putIfNotNull(context, KEY_CONVERSATION_PUBLIC_ID, conversationPublicId);
        putIfNotNull(context, KEY_BLOCKS, blockSink);
        putIfNotNull(context, KEY_TOOL_LOG, toolLog);
        putIfNotNull(context, KEY_PAGE_CONTEXT, pageContext);
        return context;
    }

    private static void putIfNotNull(Map<String, Object> context, String key, Object value) {
        if (value != null) {
            context.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public static ActorContext actor(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(KEY_ACTOR);
        return value instanceof ActorContext actor ? actor : null;
    }

    @SuppressWarnings("unchecked")
    public static List<ChatBlock> blocks(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return List.of();
        }
        Object value = toolContext.getContext().get(KEY_BLOCKS);
        return value instanceof List<?> list ? (List<ChatBlock>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<String> toolLog(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return List.of();
        }
        Object value = toolContext.getContext().get(KEY_TOOL_LOG);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    public static Long conversationId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(KEY_CONVERSATION_ID);
        return value instanceof Long id ? id : null;
    }

    public static java.util.UUID conversationPublicId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(KEY_CONVERSATION_PUBLIC_ID);
        return value instanceof java.util.UUID id ? id : null;
    }

    public static String pageContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(KEY_PAGE_CONTEXT);
        return value instanceof String text ? text : null;
    }
}
